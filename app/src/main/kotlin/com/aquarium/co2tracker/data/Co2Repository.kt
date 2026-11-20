package com.aquarium.co2tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.aquarium.co2tracker.domain.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
    val config = getConfigInternal()
    if (config == null) {
      _projectionFlow.value = null
      return@withContext null
    }
    val events = getEventsInternal(config.id)
    val projection = Co2PhysicsEngine.evaluateLedger(config, events)
    _projectionFlow.value = projection
    projection
  }

  suspend fun getConfig(): CylinderConfig? = withContext(ioDispatcher) {
    getConfigInternal()
  }

  suspend fun getEvents(): List<LedgerEvent> = withContext(ioDispatcher) {
    val cfg = getConfigInternal() ?: return@withContext emptyList()
    getEventsInternal(cfg.id)
  }

  suspend fun saveConfig(config: CylinderConfig): CylinderProjection? = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    val cv = ContentValues().apply {
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
    }
    db.insertWithOnConflict("cylinder_config", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    refreshProjection()
  }

  suspend fun resetCylinder(newConfig: CylinderConfig): CylinderProjection = withContext(ioDispatcher) {
    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      // 1. Save new config
      val cv = ContentValues().apply {
        put("id", newConfig.id)
        put("cylinder_name", newConfig.cylinderName)
        put("tare_weight", newConfig.tareWeightGrams)
        put("full_gross_weight", newConfig.fullGrossWeightGrams)
        put("include_regulator", if (newConfig.includeRegulatorWeight) 1 else 0)
        put("regulator_weight", newConfig.regulatorWeightGrams)
        put("working_pressure_value", newConfig.workingPressureValue)
        put("working_pressure_unit", newConfig.workingPressureUnit.name)
        put("fluid_type", newConfig.fluidType.name)
        put("elevation_meters", newConfig.elevationMeters)
        put("atmospheric_pressure_kpa", newConfig.atmosphericPressureKpa)
        put("solenoid_start_min", newConfig.solenoidStartMinutes)
        put("solenoid_end_min", newConfig.solenoidEndMinutes)
        put("starting_bps", newConfig.startingBps)
        put("danger_threshold_pct", newConfig.dangerThresholdPercent)
        put("notification_lead_time_days", newConfig.notificationLeadTimeDays)
        put("setup_timestamp", newConfig.setupTimestampMillis)
      }
      db.insertWithOnConflict("cylinder_config", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

      // 2. Clear old ledger events for this cylinder
      db.delete("ledger_events", "cylinder_id = ?", arrayOf(newConfig.id.toString()))

      // 3. Add initial setup event
      val setupCv = ContentValues().apply {
        put("cylinder_id", newConfig.id)
        put("timestamp_millis", newConfig.setupTimestampMillis)
        put("event_type", LedgerEventType.SETUP.name)
        put("bps", newConfig.startingBps)
        put("solenoid_start_min", newConfig.solenoidStartMinutes)
        put("solenoid_end_min", newConfig.solenoidEndMinutes)
        put("downtime_hours_delta", 0.0)
        putNull("scale_weight")
        put("note", "Initial cylinder refill (${newConfig.initialNetCo2Grams.toInt()}g net CO2)")
      }
      db.insert("ledger_events", null, setupCv)

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    refreshProjection() ?: throw IllegalStateException("Failed to calculate projection after cylinder setup")
  }

  suspend fun addEvent(
    eventType: LedgerEventType,
    bps: Double,
    solenoidStartMinutes: Int,
    solenoidEndMinutes: Int,
    downtimeHoursDelta: Double = 0.0,
    scaleWeightGrams: Double? = null,
    note: String = "",
    timestampMillis: Long = System.currentTimeMillis()
  ): CylinderProjection? = withContext(ioDispatcher) {
    val config = getConfigInternal() ?: return@withContext null
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

  private fun getConfigInternal(): CylinderConfig? {
    val db = dbHelper.readableDatabase
    val cursor = db.query(
      "cylinder_config",
      null,
      null,
      null,
      null,
      null,
      "id ASC",
      "1"
    )
    cursor.use {
      if (it.moveToFirst()) {
        val workingUnitName = it.getString(it.getColumnIndexOrThrow("working_pressure_unit"))
        val fluidName = it.getString(it.getColumnIndexOrThrow("fluid_type"))
        val config = CylinderConfig(
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
          setupTimestampMillis = it.getLong(it.getColumnIndexOrThrow("setup_timestamp"))
        )
        if (config.isConfigured) return config
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
            note = it.getString(it.getColumnIndexOrThrow("note"))
          )
        )
      }
    }
    return events
  }
}
