package com.inchios.agenda

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class Availability(
    var id: String = "",
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
    var partnerRatings: Map<String, Int> = emptyMap()
) {
    val date: LocalDate get() = try { LocalDate.parse(dateString) } catch(e: Exception) { LocalDate(1970, 1, 1) }

    val startTime: LocalTime get() = try { LocalTime.parse(startTimeString) } catch(e: Exception) { LocalTime(12, 0) }

    val endTime: LocalTime get() = try { LocalTime.parse(endTimeString) } catch(e: Exception) { LocalTime(13, 0) }
}
