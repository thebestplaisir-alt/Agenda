package com.inchios.agenda.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.lifecycle.viewmodel.compose.viewModel

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

    MaterialTheme {
        AppScaffold {
            ScreenScaffold(timeText = { TimeText() }) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // --- TOP: BIG SCORE BUTTONS ---
                        Row(
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScoreButton(
                                score = if (state.isTieBreak) state.team1TieBreakPoints.toString() else viewModel.formatPoints(state.team1Points),
                                color = Color(0xFF2196F3), // Blue
                                isServing = state.servingTeam == 1,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scorePoint(1) }
                            )
                            ScoreButton(
                                score = if (state.isTieBreak) state.team2TieBreakPoints.toString() else viewModel.formatPoints(state.team2Points),
                                color = Color(0xFFFFA500), // Orange
                                isServing = state.servingTeam == 2,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.scorePoint(2) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // --- BOTTOM: HISTORY AND ACTIONS ---
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // History Table
                            HistoryTable(
                                setsHistory = state.setsHistory,
                                currentGames = state.team1Games to state.team2Games,
                                modifier = Modifier.weight(1f)
                            )

                            // Actions Column
                            Column(
                                modifier = Modifier.width(60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SmallIconButton(
                                    icon = Icons.AutoMirrored.Filled.Undo,
                                    onClick = { viewModel.undo() }
                                )
                                // Long press to reset
                                SmallIconButton(
                                    icon = Icons.Default.Refresh,
                                    onClick = { /* Could show a toast: "Long press to reset" */ },
                                    onLongClick = { viewModel.reset() }
                                )
                            }
                        }
                    }

                    if (state.matchWinner != 0) {
                        WinnerOverlay(if (state.matchWinner == 1) "ÉQUIPE 1" else "ÉQUIPE 2") { viewModel.reset() }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreButton(score: String, color: Color, isServing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score,
            color = color,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold
        )
        if (isServing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun HistoryTable(setsHistory: List<Pair<Int, Int>>, currentGames: Pair<Int, Int>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 8.dp)) {
        // Headers (1 2 3)
        Row {
            repeat(3) { i ->
                Text(
                    text = "${i + 1}",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp).width(72.dp).background(Color.Gray.copy(alpha = 0.5f)))

        // Team 1 Games
        Row {
            repeat(3) { i ->
                val games = if (i < setsHistory.size) setsHistory[i].first.toString() 
                            else if (i == setsHistory.size) currentGames.first.toString() 
                            else ""
                Text(
                    text = games,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Team 2 Games
        Row {
            repeat(3) { i ->
                val games = if (i < setsHistory.size) setsHistory[i].second.toString() 
                            else if (i == setsHistory.size) currentGames.second.toString() 
                            else ""
                Text(
                    text = games,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun WinnerOverlay(winnerName: String, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onReset() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VICTOIRE !", color = Color.Yellow, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(winnerName, color = Color.White, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("TAP TO RESET", color = Color.Gray, fontSize = 10.sp)
        }
    }
}
