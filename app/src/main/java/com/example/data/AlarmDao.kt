package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id ASC")
    suspend fun getAllAlarmsSnapshot(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE hour = :hour AND minute = :minute ORDER BY id ASC LIMIT 1")
    suspend fun getAlarmByTime(hour: Int, minute: Int): Alarm?

    @Query("DELETE FROM alarms WHERE hour = :hour AND minute = :minute AND id != :keepId")
    suspend fun deleteDuplicatesForTime(hour: Int, minute: Int, keepId: Int)

    @Insert
    suspend fun insert(alarm: Alarm)

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)
}
