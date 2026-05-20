package com.inchios.agenda

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
