package com.inchios.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Composable
fun App(
    isPremium: Boolean = false,
    userStats: UserStats = UserStats(),
    groupRatings: List<MemberRatingSummary> = emptyList(),
    userProfile: UserProfile? = null,
    onBack: () -> Unit = {},
    onPremiumClick: () -> Unit = {}
) {
    AgendaTheme {
        // Here we can implement a simple navigator or just show the StatsScreen for now
        // to demonstrate the sharing.
        StatsScreen(
            isPremium = isPremium,
            stats = userStats,
            groupRatings = groupRatings,
            onBack = onBack,
            onPremiumClick = onPremiumClick
        )
    }
}

@Composable
fun AgendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A1C1E),
            onPrimary = Color.White,
            secondary = Color(0xFF0061A4),
            onSecondary = Color.White,
            surface = Color(0xFFFFFFFF),
            background = Color(0xFFF8F9FA),
            outline = Color(0xFFD1D5DB)
        ),
        content = content
    )
}
