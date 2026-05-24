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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PadelTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun PadelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFFFFA500),
            background = Color.Black,
            surface = Color.Black
        ),
        content = content
    )
}

@Composable
fun WearApp(viewModel: PadelViewModel = viewModel()) {
    val state by viewModel.state

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- TOP: SETS HISTORY ---
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        state.setsHistory.forEach { set ->
                            Text(
                                text = "${set.first}-${set.second}  ",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${state.team1Sets} - ${state.team2Sets}",
                        color = Color.Yellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // --- MIDDLE: SCORE BUTTONS ---
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ScoreButton(
                        score = if (state.isTieBreak) state.team1TieBreakPoints.toString() else viewModel.formatPoints(state.team1Points),
                        games = state.team1Games,
                        color = Color(0xFF2196F3),
                        isServing = state.servingTeam == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.scorePoint(1) }
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    ScoreButton(
                        score = if (state.isTieBreak) state.team2TieBreakPoints.toString() else viewModel.formatPoints(state.team2Points),
                        games = state.team2Games,
                        color = Color(0xFFFFA500),
                        isServing = state.servingTeam == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.scorePoint(2) }
                    )
                }

                // --- BOTTOM: ACTIONS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallIconButton(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        onClick = { viewModel.undo() }
                    )
                    SmallIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.reset() }
                    )
                }
            }

            if (state.matchWinner != 0) {
                WinnerOverlay(if (state.matchWinner == 1) "ÉQUIPE 1" else "ÉQUIPE 2") { viewModel.reset() }
            }
        }
    }
}

@Composable
fun ScoreButton(
    score: String, 
    games: Int,
    color: Color, 
    isServing: Boolean, 
    modifier: Modifier = Modifier, 
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$games",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = score,
                color = color,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (isServing) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Yellow)
                    )
                }
            }
        }
    }
}

@Composable
fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
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
            Text("VICTOIRE !", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(winnerName, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("TAP TO RESET", color = Color.Gray, fontSize = 9.sp)
        }
    }
}
