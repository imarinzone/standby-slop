package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Preserves all user-saved alarms when updating in-place from schema v1 to v2.
         * Inspects existing columns via PRAGMA table_info and rebuilds into the exact v2 table definition.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingCols = mutableSetOf<String>()
                db.query("PRAGMA table_info(`alarms`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.let { existingCols.add(it) }
                        }
                    }
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarms_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`toneUri` TEXT NOT NULL, " +
                        "`snoozeMinutes` INTEGER NOT NULL, " +
                        "`musicPackageName` TEXT NOT NULL, " +
                        "`musicAppName` TEXT NOT NULL, " +
                        "`gradualVolume` INTEGER NOT NULL, " +
                        "`gradualVolumeSeconds` INTEGER NOT NULL)"
                )

                if (existingCols.contains("id") && existingCols.contains("hour") && existingCols.contains("minute")) {
                    val enabledExpr = if ("isEnabled" in existingCols) "COALESCE(`isEnabled`, 1)" else "1"
                    val labelExpr = if ("label" in existingCols) "COALESCE(`label`, 'Alarm')" else "'Alarm'"
                    val toneExpr = if ("toneUri" in existingCols) "COALESCE(`toneUri`, 'default_alarm')" else "'default_alarm'"
                    val snoozeExpr = if ("snoozeMinutes" in existingCols) "COALESCE(`snoozeMinutes`, 5)" else "5"
                    val pkgExpr = if ("musicPackageName" in existingCols) "COALESCE(`musicPackageName`, '')" else "''"
                    val appExpr = if ("musicAppName" in existingCols) "COALESCE(`musicAppName`, 'Default Alarm Tone')" else "'Default Alarm Tone'"
                    val gradVolExpr = if ("gradualVolume" in existingCols) "COALESCE(`gradualVolume`, 1)" else "1"
                    val gradSecExpr = if ("gradualVolumeSeconds" in existingCols) "COALESCE(`gradualVolumeSeconds`, 30)" else "30"

                    db.execSQL(
                        "INSERT INTO `alarms_new` (`id`, `hour`, `minute`, `isEnabled`, `label`, `toneUri`, " +
                            "`snoozeMinutes`, `musicPackageName`, `musicAppName`, `gradualVolume`, `gradualVolumeSeconds`) " +
                            "SELECT `id`, `hour`, `minute`, $enabledExpr, $labelExpr, $toneExpr, " +
                            "$snoozeExpr, $pkgExpr, $appExpr, $gradVolExpr, $gradSecExpr FROM `alarms`"
                    )
                }

                db.execSQL("DROP TABLE IF EXISTS `alarms`")
                db.execSQL("ALTER TABLE `alarms_new` RENAME TO `alarms`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "standby_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
