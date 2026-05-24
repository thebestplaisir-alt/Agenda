package com.inchios.scorepadelmontre

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

data class ScoreState(
    val team1Name: String = "Équipe 1",
    val team2Name: String = "Équipe 2",
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
    val servingTeam: Int = 1, // 1 ou 2
    val useGoldenPoint: Boolean = true
)

class PadelViewModel : ViewModel() {
    private val _state = mutableStateOf(ScoreState())
    val state: State<ScoreState> = _state

    private val history = mutableListOf<ScoreState>()

    fun updateNames(t1: String, t2: String) {
        saveHistory()
        _state.value = _state.value.copy(team1Name = t1, team2Name = t2)
    }

    private fun saveHistory() {
        history.add(_state.value.copy())
        if (history.size > 50) history.removeAt(0)
    }

    fun undo() {
        if (history.isNotEmpty()) {
            _state.value = history.removeAt(history.size - 1)
        }
    }

    fun reset() {
        saveHistory()
        val current = _state.value
        _state.value = ScoreState(
            team1Name = current.team1Name,
            team2Name = current.team2Name,
            useGoldenPoint = current.useGoldenPoint
        )
    }

    fun toggleServer() {
        saveHistory()
        _state.value = _state.value.copy(servingTeam = if (_state.value.servingTeam == 1) 2 else 1)
    }

    fun scorePoint(team: Int) {
        if (_state.value.matchWinner != 0) return
        saveHistory()

        val s = _state.value
        if (s.isTieBreak) {
            handleTieBreakPoint(team)
        } else {
            handleNormalPoint(team)
        }
    }

    private fun handleNormalPoint(team: Int) {
        val s = _state.value
        var p1 = s.team1Points
        var p2 = s.team2Points

        if (team == 1) {
            if (p1 < 3) p1++ 
            else if (p1 == 3) {
                if (p2 < 3 || s.useGoldenPoint) { winGame(1); return }
                else p1 = 4
            } else { winGame(1); return }
        } else {
            if (p2 < 3) p2++
            else if (p2 == 3) {
                if (p1 < 3 || s.useGoldenPoint) { winGame(2); return }
                else p2 = 4
            } else { winGame(2); return }
        }

        if (p1 == 4 && team == 2 && p2 == 3) { p1 = 3; p2 = 3 }
        if (p2 == 4 && team == 1 && p1 == 3) { p2 = 3; p1 = 3 }

        _state.value = _state.value.copy(team1Points = p1, team2Points = p2)
    }

    private fun handleTieBreakPoint(team: Int) {
        val s = _state.value
        var tp1 = s.team1TieBreakPoints
        var tp2 = s.team2TieBreakPoints

        if (team == 1) tp1++ else tp2++

        // Au tie-break, le serveur change tous les 2 points (sauf le 1er)
        if ((tp1 + tp2) % 2 == 1) {
            _state.value = _state.value.copy(servingTeam = if (s.servingTeam == 1) 2 else 1)
        }

        if ((tp1 >= 7 || tp2 >= 7) && kotlin.math.abs(tp1 - tp2) >= 2) {
            winSet(if (tp1 > tp2) 1 else 2)
        } else {
            _state.value = _state.value.copy(team1TieBreakPoints = tp1, team2TieBreakPoints = tp2)
        }
    }

    private fun winGame(team: Int) {
        val s = _state.value
        var g1 = s.team1Games
        var g2 = s.team2Games

        if (team == 1) g1++ else g2++

        // Changement de serveur à chaque jeu
        val nextServer = if (s.servingTeam == 1) 2 else 1

        if ((g1 >= 6 || g2 >= 6) && kotlin.math.abs(g1 - g2) >= 2) {
            winSet(team)
        } else if (g1 == 6 && g2 == 6) {
            _state.value = s.copy(
                team1Games = 6, team2Games = 6,
                team1Points = 0, team2Points = 0,
                isTieBreak = true, team1TieBreakPoints = 0, team2TieBreakPoints = 0,
                servingTeam = nextServer
            )
        } else {
            _state.value = s.copy(
                team1Games = g1, team2Games = g2,
                team1Points = 0, team2Points = 0,
                servingTeam = nextServer
            )
        }
    }

    private fun winSet(team: Int) {
        val s = _state.value
        var s1 = s.team1Sets
        var s2 = s.team2Sets
        val newHistory = s.setsHistory.toMutableList()
        newHistory.add(s.team1Games to s.team2Games)

        if (team == 1) s1++ else s2++

        if (s1 == 2 || s2 == 2) {
            _state.value = s.copy(
                team1Sets = s1, team2Sets = s2,
                setsHistory = newHistory,
                team1Games = 0, team2Games = 0,
                team1Points = 0, team2Points = 0,
                isTieBreak = false,
                matchWinner = if (s1 == 2) 1 else 2
            )
        } else {
            _state.value = s.copy(
                team1Sets = s1, team2Sets = s2,
                setsHistory = newHistory,
                team1Games = 0, team2Games = 0,
                team1Points = 0, team2Points = 0,
                isTieBreak = false,
                servingTeam = if (s.servingTeam == 1) 2 else 1
            )
        }
    }

    fun formatPoints(p: Int): String {
        return when (p) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            3 -> "40"
            4 -> "AD"
            else -> "0"
        }
    }
}
