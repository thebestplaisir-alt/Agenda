package com.inchios.agenda.wear

data class ScoreState(
    val team1Name: String = "ÉQUIPE 1",
    val team2Name: String = "ÉQUIPE 2",
    val team1Points: Int = 0,
    val team2Points: Int = 0,
    val team1Games: Int = 0,
    val team2Games: Int = 0,
    val team1Sets: Int = 0,
    val team2Sets: Int = 0,
    val setsHistory: List<Pair<Int, Int>> = emptyList(),
    val isTieBreak: Boolean = false,
    val team1TieBreakPoints: Int = 0,
    val team2TieBreakPoints: Int = 0,
    val matchWinner: Int = 0,
    val servingTeam: Int = 1,
    val useGoldenPoint: Boolean = true
)
