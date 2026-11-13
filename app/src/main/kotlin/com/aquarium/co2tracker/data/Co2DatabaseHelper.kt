package com.aquarium.co2tracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aquarium.co2tracker.domain.*

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
        setup_timestamp INTEGER NOT NULL
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
        note TEXT NOT NULL
      )
      """.trimIndent()
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS ledger_events")
    db.execSQL("DROP TABLE IF EXISTS cylinder_config")
    onCreate(db)
  }

  companion object {
    const val DATABASE_NAME = "co2_tracker.db"
    const val DATABASE_VERSION = 2
  }
}
