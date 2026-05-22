package com.inchios.agenda

import com.inchios.agenda.android.Availability
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM availabilities ORDER BY dateString ASC, startTimeString ASC")
    fun getAllAvailabilities(): Flow<List<Availability>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvailability(availability: Availability)

    @Delete
    suspend fun deleteAvailability(availability: Availability)
}
