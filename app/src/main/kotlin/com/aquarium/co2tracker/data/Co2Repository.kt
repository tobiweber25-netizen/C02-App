package com.aquarium.co2tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.aquarium.co2tracker.domain.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class Co2Repository(
  context: Context,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val dbHelper = Co2DatabaseHelper(context.applicationContext)

  private val _projectionFlow = MutableStateFlow<CylinderProjection?>(null)
  val projectionFlow: StateFlow<CylinderProjection?> = _projectionFlow.asStateFlow()

  suspend fun initialize() {
    refreshProjection()
  }

  suspend fun refreshProjection(): CylinderProjection? = withContext(ioDispatcher) {
    val config = getSelectedConfigInternal()
    if (config == null) {
      _projectionFlow.value = null
      return@withContext null
    }
    val events = getEventsInternal(config.id)
    val projection = Co2PhysicsEngine.evaluateLedger(config, events)
    _projectionFlow.value = projection
    projection
  }

  suspend fun getAllProjections(nowMillis: Long = System.currentTimeMillis()): List<CylinderProjection> =
    withContext(ioDispatcher) {
      getAllConfigsInternal().map { cfg ->
        val events = getEventsInternal(cfg.id)
        Co2PhysicsEngine.evaluateLedger(cfg, events, nowMillis)
      }
    }

  suspend fun getConfig(): CylinderConfig? = withContext(ioDispatcher) {
    getSelectedConfigInternal()
  }

  suspend fun getAllConfigs(): List<CylinderConfig> = withContext(ioDispatcher) {
    getAllConfigsInternal()
  }

  suspend fun getEvents(): List<LedgerEvent> = withContext(ioDispatcher) {
    val cfg = getSelectedConfigInternal() ?: return@withContext emptyList()
    getEventsInternal(cfg.id)
  }

  suspend fun getRefillArchive(): List<RefillArchiveEntry> = withContext(ioDispatcher) {
    getArchiveInternal()
  }

  suspend fun deleteArchiveEntry(entryId: Long): List<RefillArchiveEntry> = withContext(ioDispatcher) {
    dbHelper.writableDatabase.delete("refill_archive", "id = ?", arrayOf(entryId.toString()))
    getArchiveInternal()
  }

  suspend fun saveConfig(config: CylinderConfig): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    db.insertWithOnConflict("cylinder_config", null, configToContentValues(config), SQLiteDatabase.CONFLICT_REPLACE)
    refreshProjection()
  }

  suspend fun selectCylinder(cylinderId: Long): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      val clearCv = ContentValues().apply { put("is_selected", 0) }
      db.update("cylinder_config", clearCv, null, null)
      val selectCv = ContentValues().apply { put("is_selected", 1) }
      db.update("cylinder_config", selectCv, "id = ?", arrayOf(cylinderId.toString()))
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection()
  }

  suspend fun addNewCylinder(
    config: CylinderConfig,
    selectImmediately: Boolean = true
  ): CylinderProjection = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    val nextId = getNextCylinderIdInternal(db)
    val finalConfig = config.copy(
      id = nextId,
      isSelected = selectImmediately
    )
    db.beginTransaction()
    try {
      if (selectImmediately) {
        val clearCv = ContentValues().apply { put("is_selected", 0) }
        db.update("cylinder_config", clearCv, null, null)
      }
      db.insertWithOnConflict("cylinder_config", null, configToContentValues(finalConfig), SQLiteDatabase.CONFLICT_REPLACE)
      insertSetupEvent(db, finalConfig)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection() ?: Co2PhysicsEngine.evaluateLedger(finalConfig, getEventsInternal(finalConfig.id))
  }

  suspend fun resetCylinder(
    newConfig: CylinderConfig,
    archivePrevious: Boolean = true
  ): CylinderProjection = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    val now = System.currentTimeMillis()
    val existing = getConfigByIdInternal(newConfig.id) ?: getSelectedConfigInternal()
    val existingProjection = existing?.let {
      Co2PhysicsEngine.evaluateLedger(it, getEventsInternal(it.id), now)
    }

    val targetId = existing?.id ?: newConfig.id
    val finalConfig = newConfig.copy(
      id = targetId,
      bottleStatus = BottleStatus.ACTIVE,
      isSelected = true,
      pausedSinceMillis = null
    )

    db.beginTransaction()
    try {
      // Archive previous fill cycle if meaningful usage elapsed
      if (archivePrevious && existing != null && existingProjection != null) {
        val elapsedHours = (now - existing.setupTimestampMillis) / Co2PhysicsEngine.MILLIS_PER_HOUR
        if (elapsedHours >= 1.0 || existingProjection.totalConsumedGrams >= 5.0) {
          insertArchiveEntryInternal(
            db = db,
            projection = existingProjection,
            endedAtMillis = now,
            refillCost = existing.lastRefillCost,
            note = "Refilled cylinder"
          )
        }
      }

      // Ensure selected
      val clearCv = ContentValues().apply { put("is_selected", 0) }
      db.update("cylinder_config", clearCv, null, null)

      db.insertWithOnConflict("cylinder_config", null, configToContentValues(finalConfig), SQLiteDatabase.CONFLICT_REPLACE)

      // Clear old ledger events for this cylinder
      db.delete("ledger_events", "cylinder_id = ?", arrayOf(finalConfig.id.toString()))

      // Add fresh initial setup event
      insertSetupEvent(db, finalConfig)

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection() ?: throw IllegalStateException("Failed to calculate projection after cylinder setup")
  }

  /**
   * One-tap bottle swap: archives the currently active bottle, marks it NEEDS_REFILL,
   * activates the chosen full spare bottle (optionally carrying proven scale calibration),
   * and switches the dashboard to the newly connected bottle.
   */
  suspend fun swapToSpareBottle(
    spareCylinderId: Long,
    carryCalibrationFromCurrent: Boolean = true
  ): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    val now = System.currentTimeMillis()
    val current = getSelectedConfigInternal()
    val spare = getConfigByIdInternal(spareCylinderId) ?: return@withContext null
    if (current != null && current.id == spare.id) return@withContext refreshProjection()

    val currentProj = current?.let {
      Co2PhysicsEngine.evaluateLedger(it, getEventsInternal(it.id), now)
    }
    val carriedCoeff = if (carryCalibrationFromCurrent && currentProj?.isScaleCalibrated == true) {
      currentProj.activeGramsPerBubble
    } else {
      spare.carriedGramsPerBubble
    }

    db.beginTransaction()
    try {
      // 1. Archive & mark outgoing bottle NEEDS_REFILL
      if (current != null && currentProj != null && current.bottleStatus == BottleStatus.ACTIVE) {
        val elapsedHours = (now - current.setupTimestampMillis) / Co2PhysicsEngine.MILLIS_PER_HOUR
        if (elapsedHours >= 1.0 || currentProj.totalConsumedGrams >= 5.0) {
          insertArchiveEntryInternal(
            db = db,
            projection = currentProj,
            endedAtMillis = now,
            refillCost = current.lastRefillCost,
            note = "Swapped out for ${spare.cylinderName}"
          )
        }
        val outgoingUpdated = current.copy(
          bottleStatus = BottleStatus.NEEDS_REFILL,
          isSelected = false,
          pausedSinceMillis = null
        )
        db.insertWithOnConflict("cylinder_config", null, configToContentValues(outgoingUpdated), SQLiteDatabase.CONFLICT_REPLACE)
      } else {
        val clearCv = ContentValues().apply { put("is_selected", 0) }
        db.update("cylinder_config", clearCv, null, null)
      }

      // 2. Activate spare bottle with current tank BPS/schedule & optional carried calibration
      val activatedSpare = spare.copy(
        bottleStatus = BottleStatus.ACTIVE,
        isSelected = true,
        pausedSinceMillis = null,
        setupTimestampMillis = now,
        startingBps = currentProj?.currentBps ?: spare.startingBps,
        solenoidStartMinutes = currentProj?.currentStartMinutes ?: spare.solenoidStartMinutes,
        solenoidEndMinutes = currentProj?.currentEndMinutes ?: spare.solenoidEndMinutes,
        carriedGramsPerBubble = carriedCoeff
      )
      db.insertWithOnConflict("cylinder_config", null, configToContentValues(activatedSpare), SQLiteDatabase.CONFLICT_REPLACE)
      db.delete("ledger_events", "cylinder_id = ?", arrayOf(activatedSpare.id.toString()))
      insertSetupEvent(db, activatedSpare, "Swapped spare bottle onto tank (${activatedSpare.initialNetCo2Grams.roundToInt()}g net CO2)")

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection()
  }

  /**
   * Marks an empty/needs-refill spare bottle as refilled and ready in storage.
   */
  suspend fun markBottleRefilledAsSpare(
    cylinderId: Long,
    newFullGrossGrams: Double? = null,
    refillCost: Double? = null
  ): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    val target = getConfigByIdInternal(cylinderId) ?: return@withContext null
    val now = System.currentTimeMillis()
    val updatedGross = newFullGrossGrams
      ?.takeIf { it.isFinite() && it > target.effectiveTareWeightGrams }
      ?: target.fullGrossWeightGrams

    val updated = target.copy(
      fullGrossWeightGrams = updatedGross,
      bottleStatus = BottleStatus.SPARE_FULL,
      pausedSinceMillis = null,
      setupTimestampMillis = now,
      lastRefillCost = refillCost ?: target.lastRefillCost
    )
    db.beginTransaction()
    try {
      db.insertWithOnConflict("cylinder_config", null, configToContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
      db.delete("ledger_events", "cylinder_id = ?", arrayOf(updated.id.toString()))
      insertSetupEvent(db, updated, "Refilled spare ready (${updated.initialNetCo2Grams.roundToInt()}g net CO2)")
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection()
  }

  suspend fun deleteCylinder(cylinderId: Long): CylinderProjection? = withContext(ioDispatcher) {
    val all = getAllConfigsInternal()
    if (all.size <= 1) return@withContext refreshProjection()

    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      db.delete("ledger_events", "cylinder_id = ?", arrayOf(cylinderId.toString()))
      db.delete("cylinder_config", "id = ?", arrayOf(cylinderId.toString()))
      val remaining = getAllConfigsInternal()
      if (remaining.none { it.isSelected } && remaining.isNotEmpty()) {
        val nextSelected = remaining.firstOrNull { it.bottleStatus == BottleStatus.ACTIVE } ?: remaining.first()
        val cv = ContentValues().apply { put("is_selected", 1) }
        db.update("cylinder_config", cv, "id = ?", arrayOf(nextSelected.id.toString()))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection()
  }

  /**
   * One-tap live Pause / Resume for outages and tank maintenance.
   * On resume, calculates only the active solenoid hours actually missed during the pause
   * and logs a single exact DOWNTIME_OVERRIDE entry automatically.
   */
  suspend fun toggleLivePause(nowMillis: Long = System.currentTimeMillis()): Pair<CylinderProjection?, String> =
    withContext(ioDispatcher) {
      val config = getSelectedConfigInternal() ?: return@withContext (null to "No cylinder configured")
      if (config.bottleStatus != BottleStatus.ACTIVE) {
        return@withContext (refreshProjection() to "Only active tank bottles can be paused")
      }

      val db = dbHelper.writableDatabase
      val events = getEventsInternal(config.id)
      val currentProj = Co2PhysicsEngine.evaluateLedger(config, events, nowMillis)

      if (config.pausedSinceMillis == null) {
        val pausedCfg = config.copy(pausedSinceMillis = nowMillis)
        db.insertWithOnConflict("cylinder_config", null, configToContentValues(pausedCfg), SQLiteDatabase.CONFLICT_REPLACE)
        val updated = refreshProjection()
        return@withContext (updated to "CO2 paused — active downtime is being tracked automatically")
      } else {
        val pauseStart = config.pausedSinceMillis
        val missedHours = currentProj.pausedMissedSolenoidHours
        val resumedCfg = config.copy(pausedSinceMillis = null)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val windowStr = "${timeFmt.format(Date(pauseStart))}–${timeFmt.format(Date(nowMillis))}"

        db.beginTransaction()
        try {
          db.insertWithOnConflict("cylinder_config", null, configToContentValues(resumedCfg), SQLiteDatabase.CONFLICT_REPLACE)
          if (missedHours >= 0.01) {
            val eventTs = max(nowMillis, events.lastOrNull()?.timestampMillis ?: nowMillis)
            val roundedHours = (missedHours * 100.0).roundToInt() / 100.0
            val cv = ContentValues().apply {
              put("cylinder_id", config.id)
              put("timestamp_millis", eventTs)
              put("event_type", LedgerEventType.DOWNTIME_OVERRIDE.name)
              put("bps", currentProj.currentBps)
              put("solenoid_start_min", currentProj.currentStartMinutes)
              put("solenoid_end_min", currentProj.currentEndMinutes)
              put("downtime_hours_delta", -roundedHours)
              putNull("scale_weight")
              put("note", "Live pause ($windowStr)")
              putNull("drop_checker_status")
            }
            db.insert("ledger_events", null, cv)
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }

        val updated = refreshProjection()
        val msg = if (missedHours >= 0.01) {
          String.format(Locale.US, "CO2 resumed — logged %.2f missed active hrs", missedHours)
        } else {
          "CO2 resumed (solenoid was off-cycle during pause)"
        }
        return@withContext (updated to msg)
      }
    }

  suspend fun addEvent(
    eventType: LedgerEventType,
    bps: Double,
    solenoidStartMinutes: Int,
    solenoidEndMinutes: Int,
    downtimeHoursDelta: Double = 0.0,
    scaleWeightGrams: Double? = null,
    note: String = "",
    timestampMillis: Long = System.currentTimeMillis(),
    dropCheckerStatus: DropCheckerStatus? = null
  ): CylinderProjection? = withContext(ioDispatcher) {
    val config = getSelectedConfigInternal() ?: return@withContext null
    val db = dbHelper.writableDatabase

    val cv = ContentValues().apply {
      put("cylinder_id", config.id)
      put("timestamp_millis", timestampMillis)
      put("event_type", eventType.name)
      put("bps", bps)
      put("solenoid_start_min", solenoidStartMinutes)
      put("solenoid_end_min", solenoidEndMinutes)
      put("downtime_hours_delta", downtimeHoursDelta)
      if (scaleWeightGrams != null) {
        put("scale_weight", scaleWeightGrams)
      } else {
        putNull("scale_weight")
      }
      put("note", note)
      if (dropCheckerStatus != null) {
        put("drop_checker_status", dropCheckerStatus.name)
      } else {
        putNull("drop_checker_status")
      }
    }
    db.insert("ledger_events", null, cv)
    refreshProjection()
  }

  suspend fun deleteEvent(eventId: Long): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    db.delete(
      "ledger_events",
      "id = ? AND event_type != ?",
      arrayOf(eventId.toString(), LedgerEventType.SETUP.name)
    )
    refreshProjection()
  }

  private fun insertSetupEvent(
    db: SQLiteDatabase,
    config: CylinderConfig,
    customNote: String? = null
  ) {
    val setupCv = ContentValues().apply {
      put("cylinder_id", config.id)
      put("timestamp_millis", config.setupTimestampMillis)
      put("event_type", LedgerEventType.SETUP.name)
      put("bps", config.startingBps)
      put("solenoid_start_min", config.solenoidStartMinutes)
      put("solenoid_end_min", config.solenoidEndMinutes)
      put("downtime_hours_delta", 0.0)
      putNull("scale_weight")
      put("note", customNote ?: "Initial cylinder refill (${config.initialNetCo2Grams.roundToInt()}g net CO2)")
      putNull("drop_checker_status")
    }
    db.insert("ledger_events", null, setupCv)
  }

  private fun insertArchiveEntryInternal(
    db: SQLiteDatabase,
    projection: CylinderProjection,
    endedAtMillis: Long,
    refillCost: Double?,
    note: String
  ) {
    val durationDays = ((endedAtMillis - projection.config.setupTimestampMillis) / Co2PhysicsEngine.MILLIS_PER_DAY)
      .coerceAtLeast(0.01)
    val consumed = projection.totalConsumedGrams.coerceAtLeast(0.0)
    val avgDaily = consumed / durationDays
    val cv = ContentValues().apply {
      put("cylinder_id", projection.config.id)
      put("cylinder_name", projection.config.cylinderName)
      put("started_at_millis", projection.config.setupTimestampMillis)
      put("ended_at_millis", endedAtMillis)
      put("initial_net_grams", projection.config.initialNetCo2Grams)
      put("consumed_grams", consumed)
      put("duration_days", durationDays)
      put("avg_daily_burn_grams", avgDaily)
      if (projection.isScaleCalibrated) {
        put("calibrated_grams_per_bubble", projection.activeGramsPerBubble)
      } else {
        putNull("calibrated_grams_per_bubble")
      }
      put("was_calibrated", if (projection.isScaleCalibrated) 1 else 0)
      if (refillCost != null && refillCost > 0.0) {
        put("refill_cost", refillCost)
      } else {
        putNull("refill_cost")
      }
      put("note", note)
    }
    db.insert("refill_archive", null, cv)
  }

  private fun getNextCylinderIdInternal(db: SQLiteDatabase): Long {
    db.rawQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM cylinder_config", null).use { cursor ->
      if (cursor.moveToFirst()) return cursor.getLong(0).coerceAtLeast(1L)
    }
    return 1L
  }

  private fun configToContentValues(config: CylinderConfig): ContentValues = ContentValues().apply {
    put("id", config.id)
    put("cylinder_name", config.cylinderName)
    put("tare_weight", config.tareWeightGrams)
    put("full_gross_weight", config.fullGrossWeightGrams)
    put("include_regulator", if (config.includeRegulatorWeight) 1 else 0)
    put("regulator_weight", config.regulatorWeightGrams)
    put("working_pressure_value", config.workingPressureValue)
    put("working_pressure_unit", config.workingPressureUnit.name)
    put("fluid_type", config.fluidType.name)
    put("elevation_meters", config.elevationMeters)
    put("atmospheric_pressure_kpa", config.atmosphericPressureKpa)
    put("solenoid_start_min", config.solenoidStartMinutes)
    put("solenoid_end_min", config.solenoidEndMinutes)
    put("starting_bps", config.startingBps)
    put("danger_threshold_pct", config.dangerThresholdPercent)
    put("notification_lead_time_days", config.notificationLeadTimeDays)
    put("setup_timestamp", config.setupTimestampMillis)
    put("bottle_status", config.bottleStatus.name)
    put("is_selected", if (config.isSelected) 1 else 0)
    if (config.pausedSinceMillis != null) {
      put("paused_since_millis", config.pausedSinceMillis)
    } else {
      putNull("paused_since_millis")
    }
    if (config.carriedGramsPerBubble != null) {
      put("carried_grams_per_bubble", config.carriedGramsPerBubble)
    } else {
      putNull("carried_grams_per_bubble")
    }
    put("refill_shop_days_mask", config.refillShopDaysMask)
    put("refill_shop_name", config.refillShopName)
    if (config.lastRefillCost != null && config.lastRefillCost > 0.0) {
      put("last_refill_cost", config.lastRefillCost)
    } else {
      putNull("last_refill_cost")
    }
  }

  private fun cursorToConfig(it: Cursor): CylinderConfig {
    val workingUnitName = it.getString(it.getColumnIndexOrThrow("working_pressure_unit"))
    val fluidName = it.getString(it.getColumnIndexOrThrow("fluid_type"))
    val statusIdx = it.getColumnIndex("bottle_status")
    val statusStr = if (statusIdx >= 0 && !it.isNull(statusIdx)) it.getString(statusIdx) else BottleStatus.ACTIVE.name
    val selectedIdx = it.getColumnIndex("is_selected")
    val isSelected = if (selectedIdx >= 0 && !it.isNull(selectedIdx)) it.getInt(selectedIdx) == 1 else true
    val pausedIdx = it.getColumnIndex("paused_since_millis")
    val pausedSince = if (pausedIdx >= 0 && !it.isNull(pausedIdx)) it.getLong(pausedIdx) else null
    val carriedIdx = it.getColumnIndex("carried_grams_per_bubble")
    val carriedCoeff = if (carriedIdx >= 0 && !it.isNull(carriedIdx)) it.getDouble(carriedIdx) else null
    val shopDaysIdx = it.getColumnIndex("refill_shop_days_mask")
    val shopDaysMask = if (shopDaysIdx >= 0 && !it.isNull(shopDaysIdx)) it.getInt(shopDaysIdx) else CylinderConfig.ALL_DAYS_MASK
    val shopNameIdx = it.getColumnIndex("refill_shop_name")
    val shopName = if (shopNameIdx >= 0 && !it.isNull(shopNameIdx)) it.getString(shopNameIdx) else ""
    val costIdx = it.getColumnIndex("last_refill_cost")
    val lastCost = if (costIdx >= 0 && !it.isNull(costIdx)) it.getDouble(costIdx) else null

    return CylinderConfig(
      id = it.getLong(it.getColumnIndexOrThrow("id")),
      cylinderName = it.getString(it.getColumnIndexOrThrow("cylinder_name")),
      tareWeightGrams = it.getDouble(it.getColumnIndexOrThrow("tare_weight")),
      fullGrossWeightGrams = it.getDouble(it.getColumnIndexOrThrow("full_gross_weight")),
      includeRegulatorWeight = it.getInt(it.getColumnIndexOrThrow("include_regulator")) == 1,
      regulatorWeightGrams = it.getDouble(it.getColumnIndexOrThrow("regulator_weight")),
      workingPressureValue = it.getDouble(it.getColumnIndexOrThrow("working_pressure_value")),
      workingPressureUnit = runCatching { PressureUnit.valueOf(workingUnitName) }.getOrDefault(PressureUnit.BAR),
      fluidType = runCatching { FluidType.valueOf(fluidName) }.getOrDefault(FluidType.WATER),
      elevationMeters = it.getDouble(it.getColumnIndexOrThrow("elevation_meters")),
      atmosphericPressureKpa = it.getDouble(it.getColumnIndexOrThrow("atmospheric_pressure_kpa")),
      solenoidStartMinutes = it.getInt(it.getColumnIndexOrThrow("solenoid_start_min")),
      solenoidEndMinutes = it.getInt(it.getColumnIndexOrThrow("solenoid_end_min")),
      startingBps = it.getDouble(it.getColumnIndexOrThrow("starting_bps")),
      dangerThresholdPercent = it.getDouble(it.getColumnIndexOrThrow("danger_threshold_pct")),
      notificationLeadTimeDays = it.getInt(it.getColumnIndexOrThrow("notification_lead_time_days")),
      setupTimestampMillis = it.getLong(it.getColumnIndexOrThrow("setup_timestamp")),
      bottleStatus = runCatching { BottleStatus.valueOf(statusStr) }.getOrDefault(BottleStatus.ACTIVE),
      isSelected = isSelected,
      pausedSinceMillis = pausedSince,
      carriedGramsPerBubble = carriedCoeff,
      refillShopDaysMask = shopDaysMask,
      refillShopName = shopName,
      lastRefillCost = lastCost
    )
  }

  private fun getAllConfigsInternal(): List<CylinderConfig> {
    val db = dbHelper.readableDatabase
    val list = mutableListOf<CylinderConfig>()
    db.query("cylinder_config", null, null, null, null, null, "is_selected DESC, id ASC").use { cursor ->
      while (cursor.moveToNext()) {
        val cfg = cursorToConfig(cursor)
        if (cfg.isConfigured) list.add(cfg)
      }
    }
    return list
  }

  private fun getSelectedConfigInternal(): CylinderConfig? {
    val all = getAllConfigsInternal()
    return all.firstOrNull { it.isSelected } ?: all.firstOrNull()
  }

  private fun getConfigByIdInternal(id: Long): CylinderConfig? {
    val db = dbHelper.readableDatabase
    db.query("cylinder_config", null, "id = ?", arrayOf(id.toString()), null, null, null, "1").use { cursor ->
      if (cursor.moveToFirst()) {
        val cfg = cursorToConfig(cursor)
        if (cfg.isConfigured) return cfg
      }
    }
    return null
  }

  private fun getEventsInternal(cylinderId: Long): List<LedgerEvent> {
    val db = dbHelper.readableDatabase
    val events = mutableListOf<LedgerEvent>()
    val cursor = db.query(
      "ledger_events",
      null,
      "cylinder_id = ?",
      arrayOf(cylinderId.toString()),
      null,
      null,
      "timestamp_millis ASC, id ASC"
    )
    cursor.use {
      while (it.moveToNext()) {
        val typeStr = it.getString(it.getColumnIndexOrThrow("event_type"))
        val scaleWeightIndex = it.getColumnIndexOrThrow("scale_weight")
        val scaleWeight = if (it.isNull(scaleWeightIndex)) null else it.getDouble(scaleWeightIndex)
        val dcIdx = it.getColumnIndex("drop_checker_status")
        val dcStatus = if (dcIdx >= 0 && !it.isNull(dcIdx)) {
          runCatching { DropCheckerStatus.valueOf(it.getString(dcIdx)) }.getOrNull()
        } else {
          null
        }

        events.add(
          LedgerEvent(
            id = it.getLong(it.getColumnIndexOrThrow("id")),
            cylinderId = it.getLong(it.getColumnIndexOrThrow("cylinder_id")),
            timestampMillis = it.getLong(it.getColumnIndexOrThrow("timestamp_millis")),
            eventType = runCatching { LedgerEventType.valueOf(typeStr) }.getOrDefault(LedgerEventType.BPS_CHANGE),
            bps = it.getDouble(it.getColumnIndexOrThrow("bps")),
            solenoidStartMinutes = it.getInt(it.getColumnIndexOrThrow("solenoid_start_min")),
            solenoidEndMinutes = it.getInt(it.getColumnIndexOrThrow("solenoid_end_min")),
            downtimeHoursDelta = it.getDouble(it.getColumnIndexOrThrow("downtime_hours_delta")),
            scaleWeightGrams = scaleWeight,
            note = it.getString(it.getColumnIndexOrThrow("note")),
            dropCheckerStatus = dcStatus
          )
        )
      }
    }
    return events
  }

  private fun getArchiveInternal(): List<RefillArchiveEntry> {
    val db = dbHelper.readableDatabase
    val entries = mutableListOf<RefillArchiveEntry>()
    db.query("refill_archive", null, null, null, null, null, "ended_at_millis DESC, id DESC").use { it ->
      while (it.moveToNext()) {
        val coeffIdx = it.getColumnIndexOrThrow("calibrated_grams_per_bubble")
        val costIdx = it.getColumnIndexOrThrow("refill_cost")
        entries.add(
          RefillArchiveEntry(
            id = it.getLong(it.getColumnIndexOrThrow("id")),
            cylinderId = it.getLong(it.getColumnIndexOrThrow("cylinder_id")),
            cylinderName = it.getString(it.getColumnIndexOrThrow("cylinder_name")),
            startedAtMillis = it.getLong(it.getColumnIndexOrThrow("started_at_millis")),
            endedAtMillis = it.getLong(it.getColumnIndexOrThrow("ended_at_millis")),
            initialNetGrams = it.getDouble(it.getColumnIndexOrThrow("initial_net_grams")),
            consumedGrams = it.getDouble(it.getColumnIndexOrThrow("consumed_grams")),
            durationDays = it.getDouble(it.getColumnIndexOrThrow("duration_days")),
            avgDailyBurnGrams = it.getDouble(it.getColumnIndexOrThrow("avg_daily_burn_grams")),
            calibratedGramsPerBubble = if (it.isNull(coeffIdx)) null else it.getDouble(coeffIdx),
            wasCalibrated = it.getInt(it.getColumnIndexOrThrow("was_calibrated")) == 1,
            refillCost = if (it.isNull(costIdx)) null else it.getDouble(costIdx),
            note = it.getString(it.getColumnIndexOrThrow("note"))
          )
        )
      }
    }
    return entries
  }
}
