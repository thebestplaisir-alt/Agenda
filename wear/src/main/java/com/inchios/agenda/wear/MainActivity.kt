package com.inchios.agenda.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.*
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.compose.layout.ScreenScaffold
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState

// --- MODEL ---
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

class PadelViewModel : ViewModel() {
    var state = mutableStateOf(ScoreState())
    private val history = mutableListOf<ScoreState>()

    fun updateNames(t1: String, t2: String) {
        saveHistory()
        state.value = state.value.copy(
            team1Name = if(t1.isNotBlank()) t1.uppercase() else "ÉQUIPE 1",
            team2Name = if(t2.isNotBlank()) t2.uppercase() else "ÉQUIPE 2"
        )
    }

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
        val current = state.value
        state.value = ScoreState(team1Name = current.team1Name, team2Name = current.team2Name)
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
        val newHistory = s.setsHistory + (s.team1Games to s.team2Games)
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

// --- UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Preview(device = "id:wearos_large_round", showSystemUi = true)
@Composable
fun WearAppPreview() {
    WearApp()
}

@Composable
fun WearApp(viewModel: PadelViewModel = viewModel()) {
    val state by viewModel.state
    var screenState by remember { mutableStateOf("main") } // "main", "edit", "details"

    MaterialTheme {
        AppScaffold {
            when (screenState) {
                "edit" -> EditNamesScreen(
                    t1 = state.team1Name,
                    t2 = state.team2Name,
                    onConfirm = { n1, n2 -> viewModel.updateNames(n1, n2); screenState = "main" },
                    onCancel = { screenState = "main" }
                )
                "details" -> SetDetailsScreen(
                    history = state.setsHistory,
                    t1Name = state.team1Name,
                    t2Name = state.team2Name,
                    onClose = { screenState = "main" }
                )
                else -> {
                    ScreenScaffold(timeText = { TimeText() }) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Score Sets - CLIQUABLE pour les détails
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 10.dp).clickable { screenState = "details" }
                                ) {
                                    Text(
                                        text = "${state.team1Sets} - ${state.team2Sets}",
                                        color = Color.Yellow,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("DÉTAILS", color = Color.Yellow.copy(alpha = 0.5f), fontSize = 8.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(2.dp))

                                ScoreCard(
                                    name = state.team1Name,
                                    score = if (state.isTieBreak) state.team1TieBreakPoints.toString() else viewModel.formatPoints(state.team1Points),
                                    games = state.team1Games,
                                    isServing = state.servingTeam == 1,
                                    color = Color(0xFF2196F3),
                                    onClick = { viewModel.scorePoint(1) }
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                ScoreCard(
                                    name = state.team2Name,
                                    score = if (state.isTieBreak) state.team2TieBreakPoints.toString() else viewModel.formatPoints(state.team2Points),
                                    games = state.team2Games,
                                    isServing = state.servingTeam == 2,
                                    color = Color(0xFFF44336),
                                    onClick = { viewModel.scorePoint(2) }
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LargeActionButton(Icons.Default.History) { viewModel.undo() }
                                    LargeActionButton(Icons.Default.Edit) { screenState = "edit" }
                                    LargeActionButton(Icons.Default.Refresh) { viewModel.reset() }
                                }
                            }

                            if (state.matchWinner != 0) {
                                WinnerOverlay(if (state.matchWinner == 1) state.team1Name else state.team2Name) { viewModel.reset() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreCard(name: String, score: String, games: Int, isServing: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(0.92f).height(50.dp).clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.2f)).clickable { onClick() }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isServing) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Yellow))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text("$games", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text(score, color = color, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun LargeActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.15f))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
    }
}

@Composable
fun SetDetailsScreen(history: List<Pair<Int, Int>>, t1Name: String, t2Name: String, onClose: () -> Unit) {
    val listState = rememberScalingLazyListState()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text("DÉTAILS DES SETS", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (history.isEmpty()) {
                item { Text("AUCUN SET FINI", color = Color.Gray, fontSize = 12.sp) }
            } else {
                items(history.size) { index ->
                    val set = history[index]
                    Column(
                        modifier = Modifier.fillMaxWidth(0.9f).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SET ${index + 1}", fontSize = 10.sp, color = Color.Gray)
                        Text("${set.first} - ${set.second}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("$t1Name VS $t2Name", fontSize = 8.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            item {
                Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp), colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun EditNamesScreen(t1: String, t2: String, onConfirm: (String, String) -> Unit, onCancel: () -> Unit) {
    var name1 by remember { mutableStateOf(t1) }
    var name2 by remember { mutableStateOf(t2) }
    
    // Simule une saisie de texte - Sur Wear OS réel, cliquer ici ouvrirait le clavier
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text("MODIFIER LES NOMS", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Bouton Equipe 1
            Chip(
                label = { Text(name1, fontSize = 12.sp) },
                onClick = { /* Normalement : lancer RemoteInput */ name1 = if(name1=="JOUEUR 1") "MOI" else "JOUEUR 1" },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2196F3).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Bouton Equipe 2
            Chip(
                label = { Text(name2, fontSize = 12.sp) },
                onClick = { name2 = if(name2=="JOUEUR 2") "ADVERSAIRE" else "JOUEUR 2" },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFF44336).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray)) {
                    Text("ANNULER", fontSize = 8.sp)
                }
                Button(onClick = { onConfirm(name1, name2) }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF22C55E))) {
                    Text("OK", fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
fun WinnerOverlay(winnerName: String, onReset: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).clickable { onReset() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VICTOIRE !", color = Color.Yellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(winnerName, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text("TAP POUR RESET", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}
