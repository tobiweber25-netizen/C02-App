package com.aquarium.co2tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aquarium.co2tracker.domain.BottleStatus
import com.aquarium.co2tracker.domain.CylinderConfig

class Co2DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS cylinder_config (
        id INTEGER PRIMARY KEY,
        cylinder_name TEXT NOT NULL,
        tare_weight REAL NOT NULL,
        full_gross_weight REAL NOT NULL,
        include_regulator INTEGER NOT NULL,
        regulator_weight REAL NOT NULL,
        working_pressure_value REAL NOT NULL,
        working_pressure_unit TEXT NOT NULL,
        fluid_type TEXT NOT NULL,
        elevation_meters REAL NOT NULL,
        atmospheric_pressure_kpa REAL NOT NULL,
        solenoid_start_min INTEGER NOT NULL,
        solenoid_end_min INTEGER NOT NULL,
        starting_bps REAL NOT NULL,
        danger_threshold_pct REAL NOT NULL,
        notification_lead_time_days INTEGER NOT NULL,
        setup_timestamp INTEGER NOT NULL,
        bottle_status TEXT NOT NULL DEFAULT '${BottleStatus.ACTIVE.name}',
        is_selected INTEGER NOT NULL DEFAULT 1,
        paused_since_millis INTEGER,
        carried_grams_per_bubble REAL,
        refill_shop_days_mask INTEGER NOT NULL DEFAULT ${CylinderConfig.ALL_DAYS_MASK},
        refill_shop_name TEXT NOT NULL DEFAULT '',
        last_refill_cost REAL
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS ledger_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cylinder_id INTEGER NOT NULL,
        timestamp_millis INTEGER NOT NULL,
        event_type TEXT NOT NULL,
        bps REAL NOT NULL,
        solenoid_start_min INTEGER NOT NULL,
        solenoid_end_min INTEGER NOT NULL,
        downtime_hours_delta REAL NOT NULL,
        scale_weight REAL,
        note TEXT NOT NULL,
        drop_checker_status TEXT
      )
      """.trimIndent()
    )

    createRefillArchiveTable(db)
  }

  private fun createRefillArchiveTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS refill_archive (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        cylinder_id INTEGER NOT NULL,
        cylinder_name TEXT NOT NULL,
        started_at_millis INTEGER NOT NULL,
        ended_at_millis INTEGER NOT NULL,
        initial_net_grams REAL NOT NULL,
        consumed_grams REAL NOT NULL,
        duration_days REAL NOT NULL,
        avg_daily_burn_grams REAL NOT NULL,
        calibrated_grams_per_bubble REAL,
        was_calibrated INTEGER NOT NULL DEFAULT 0,
        refill_cost REAL,
        note TEXT NOT NULL DEFAULT ''
      )
      """.trimIndent()
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("DROP TABLE IF EXISTS ledger_events")
      db.execSQL("DROP TABLE IF EXISTS cylinder_config")
      onCreate(db)
      return
    }

    if (oldVersion < 3) {
      // Non-destructive v2 -> v3 migration preserving all existing cylinder configs & ledger events
      addColumnIfMissing(db, "cylinder_config", "bottle_status", "TEXT NOT NULL DEFAULT '${BottleStatus.ACTIVE.name}'")
      addColumnIfMissing(db, "cylinder_config", "is_selected", "INTEGER NOT NULL DEFAULT 1")
      addColumnIfMissing(db, "cylinder_config", "paused_since_millis", "INTEGER")
      addColumnIfMissing(db, "cylinder_config", "carried_grams_per_bubble", "REAL")
      addColumnIfMissing(db, "cylinder_config", "refill_shop_days_mask", "INTEGER NOT NULL DEFAULT ${CylinderConfig.ALL_DAYS_MASK}")
      addColumnIfMissing(db, "cylinder_config", "refill_shop_name", "TEXT NOT NULL DEFAULT ''")
      addColumnIfMissing(db, "cylinder_config", "last_refill_cost", "REAL")

      addColumnIfMissing(db, "ledger_events", "drop_checker_status", "TEXT")

      createRefillArchiveTable(db)
    }
  }

  private fun addColumnIfMissing(db: SQLiteDatabase, table: String, column: String, definition: String) {
    val existingColumns = mutableSetOf<String>()
    db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        if (nameIndex >= 0) existingColumns.add(cursor.getString(nameIndex))
      }
    }
    if (!existingColumns.contains(column)) {
      db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
  }

  companion object {
    const val DATABASE_NAME = "co2_tracker.db"
    const val DATABASE_VERSION = 3
  }
}
