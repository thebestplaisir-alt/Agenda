package com.inchios.agenda.wear

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class PadelViewModel : ViewModel() {
    var state = mutableStateOf(ScoreState())
    private val history = mutableListOf<ScoreState>()

    private fun saveHistory() {
        history.add(state.value.copy())
        if (history.size > 50) history.removeAt(0)
    }

    fun undo() {
        if (history.isNotEmpty()) {
            state.value = history.removeAt(history.size - 1)
        }
    }

    fun reset() {
        saveHistory()
        state.value = ScoreState()
    }

    fun scorePoint(team: Int) {
        if (state.value.matchWinner != 0) return
        saveHistory()
        if (state.value.isTieBreak) handleTieBreakPoint(team) else handleNormalPoint(team)
    }

    private fun handleNormalPoint(team: Int) {
        val s = state.value
        var p1 = s.team1Points; var p2 = s.team2Points
        if (team == 1) {
            if (p1 < 3) p1++ else if (p1 == 3) { if (p2 < 3 || s.useGoldenPoint) { winGame(1); return } else p1 = 4 }
            else { winGame(1); return }
        } else {
            if (p2 < 3) p2++ else if (p2 == 3) { if (p1 < 3 || s.useGoldenPoint) { winGame(2); return } else p2 = 4 }
            else { winGame(2); return }
        }
        if (p1 == 4 && team == 2 && p2 == 3) { p1 = 3; p2 = 3 }
        if (p2 == 4 && team == 1 && p1 == 3) { p2 = 3; p1 = 3 }
        state.value = state.value.copy(team1Points = p1, team2Points = p2)
    }

    private fun handleTieBreakPoint(team: Int) {
        val s = state.value
        var tp1 = s.team1TieBreakPoints; var tp2 = s.team2TieBreakPoints
        if (team == 1) tp1++ else tp2++
        if ((tp1 + tp2) % 2 == 1) {
            state.value = state.value.copy(servingTeam = if (s.servingTeam == 1) 2 else 1, team1TieBreakPoints = tp1, team2TieBreakPoints = tp2)
        } else {
            state.value = state.value.copy(team1TieBreakPoints = tp1, team2TieBreakPoints = tp2)
        }
        if ((tp1 >= 7 || tp2 >= 7) && kotlin.math.abs(tp1 - tp2) >= 2) winSet(if (tp1 > tp2) 1 else 2)
    }

    private fun winGame(team: Int) {
        val s = state.value
        var g1 = s.team1Games; var g2 = s.team2Games
        if (team == 1) g1++ else g2++
        val nextServer = if (s.servingTeam == 1) 2 else 1
        if ((g1 >= 6 || g2 >= 6) && kotlin.math.abs(g1 - g2) >= 2) winSet(team)
        else if (g1 == 6 && g2 == 6) state.value = s.copy(team1Games = 6, team2Games = 6, team1Points = 0, team2Points = 0, isTieBreak = true, servingTeam = nextServer)
        else state.value = s.copy(team1Games = g1, team2Games = g2, team1Points = 0, team2Points = 0, servingTeam = nextServer)
    }

    private fun winSet(team: Int) {
        val s = state.value
        var s1 = s.team1Sets; var s2 = s.team2Sets
        val newHistory = s.setsHistory + (s.team1Games + (if(team==1) 1 else 0) to s.team2Games + (if(team==2) 1 else 0))
        if (team == 1) s1++ else s2++
        state.value = s.copy(
            team1Sets = s1, team2Sets = s2, setsHistory = newHistory,
            team1Games = 0, team2Games = 0, team1Points = 0, team2Points = 0, isTieBreak = false,
            matchWinner = if (s1 == 2 || s2 == 2) (if (s1 == 2) 1 else 2) else 0,
            servingTeam = if (s1 < 2 && s2 < 2) (if (s.servingTeam == 1) 2 else 1) else s.servingTeam
        )
    }

    fun formatPoints(p: Int): String = when (p) { 0 -> "0"; 1 -> "15"; 2 -> "30"; 3 -> "40"; 4 -> "AD"; else -> "0" }
}
