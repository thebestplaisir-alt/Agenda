package com.inchios.agenda.android

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Keep
@IgnoreExtraProperties
@Entity(tableName = "availabilities")
data class Availability(
    @PrimaryKey var id: String = "",
    var personName: String = "",
    var dateString: String = "",
    var startTimeString: String = "",
    var endTimeString: String = "",
    var userId: String = "",
    var userPhoto: String? = null,
    var location: String = "",
    var comment: String = "",
    var groupId: String = "",
    var isDuo: Boolean = false,
    var isUserPremium: Boolean = false,
    var rating: Int = 0,
    var mood: String = "",
    var reviewNotes: String = "",
    var durationMinutes: Int = 60,
    var isCompleted: Boolean = false,
    var participantIds: List<String> = emptyList(),
    var completedBy: List<String> = emptyList(),
    var partnerRatings: Map<String, Int> = emptyMap() // Map<partnerUserId, rating> pour noter ses partenaires
) {
    @get:Exclude
    val date: LocalDate get() = try { LocalDate.parse(dateString) } catch(e: Exception) { LocalDate(1970, 1, 1) }

    @get:Exclude
    val startTime: LocalTime get() = try { LocalTime.parse(startTimeString) } catch(e: Exception) { LocalTime(12, 0) }

    @get:Exclude
    val endTime: LocalTime get() = try { LocalTime.parse(endTimeString) } catch(e: Exception) { LocalTime(13, 0) }
}
