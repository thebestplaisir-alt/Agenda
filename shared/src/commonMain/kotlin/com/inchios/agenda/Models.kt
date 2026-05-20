package com.inchios.agenda

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val level: String = "Intermédiaire",
    val age: String = "",
    val gender: String = "Non spécifié",
    val phone: String = "",
    val groupIds: List<String> = emptyList(),
    val currentGroupId: String = "",
    val inviteCode: String = "",
    val premium: Boolean = false,
    val photoUrl: String? = null
)

data class UserStats(
    val totalMatches: Int = 0,
    val duoMatches: Int = 0,
    val quatuorMatches: Int = 0,
    val estimatedHours: Int = 0,
    val matchesThisMonth: Int = 0,
    val growthPercentage: Int = 0,
    val peakDay: String = "-",
    val favoriteSlot: String = "-",
    val favoriteLocation: String = "-",
    val caloriesBurned: Int = 0,
    val morningMatches: Int = 0,
    val afternoonMatches: Int = 0,
    val eveningMatches: Int = 0,
    val averageRating: Float = 0f,
    val averageReceivedRating: Float = 0f,
    val bestMood: String = "-",
    val activityData: Map<String, Int> = emptyMap(), // On utilise String pour la date pour simplifier le partage
    val partnersGiven: List<PartnerRating> = emptyList(),
    val partnersReceived: List<PartnerRating> = emptyList(),
)

data class MemberRatingSummary(
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val averageRating: Float = 0f,
    val ratingCount: Int = 0
)

data class PartnerRating(
    val partnerId: String = "",
    val partnerName: String = "",
    val rating: Int = 0,
    val mood: String = "",
    val date: String = "",
    val groupId: String = ""
)

data class DuoInvitation(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val toName: String = "",
    val availabilityId: String = "",
    val status: String = "PENDING",
    val date: String = "",
    val time: String = "",
    val proposedStartTime: String = "",
    val proposedEndTime: String = "",
    val lastProposerId: String = ""
)

data class Group(
    val id: String = "",
    val name: String = "",
    val createdBy: String = ""
)
