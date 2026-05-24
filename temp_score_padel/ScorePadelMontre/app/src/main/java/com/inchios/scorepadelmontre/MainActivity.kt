package com.inchios.scorepadelmontre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inchios.scorepadelmontre.ui.theme.ScorePadelMontreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScorePadelMontreTheme {
                PadelScoreApp()
            }
        }
    }
}

@Composable
fun PadelScoreApp(viewModel: PadelViewModel = viewModel()) {
    val state by viewModel.state
    var showEditNames by remember { mutableStateOf(false) }
    var showSetDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with Sets History
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.setsHistory.forEach { set ->
                        Text(
                            text = "${set.first}-${set.second}  ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${state.team1Sets} - ${state.team2Sets}",
                    color = Color.Yellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable { showSetDetails = true }
                )
            }

            // Team 1 Area
            ScoreCard(
                name = state.team1Name,
                score = if (state.isTieBreak) state.team1TieBreakPoints.toString() 
                        else viewModel.formatPoints(state.team1Points),
                games = state.team1Games,
                isServing = state.servingTeam == 1,
                servingSide = if (state.isTieBreak) {
                    if ((state.team1TieBreakPoints + state.team2TieBreakPoints) % 2 == 0) "R" else "L"
                } else {
                    if ((state.team1Points + state.team2Points) % 2 == 0) "R" else "L"
                },
                color = Color(0xFF2196F3),
                onClick = { viewModel.scorePoint(1) },
                onLongClick = { viewModel.toggleServer() }
            )

            // Team 2 Area
            ScoreCard(
                name = state.team2Name,
                score = if (state.isTieBreak) state.team2TieBreakPoints.toString() 
                        else viewModel.formatPoints(state.team2Points),
                games = state.team2Games,
                isServing = state.servingTeam == 2,
                servingSide = if (state.isTieBreak) {
                    if ((state.team1TieBreakPoints + state.team2TieBreakPoints) % 2 == 0) "R" else "L"
                } else {
                    if ((state.team1Points + state.team2Points) % 2 == 0) "R" else "L"
                },
                color = Color(0xFFF44336),
                onClick = { viewModel.scorePoint(2) },
                onLongClick = { viewModel.toggleServer() }
            )

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledIconButton(
                    onClick = { viewModel.undo() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Default.History, 
                        "Undo", 
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                FilledIconButton(
                    onClick = { showEditNames = true },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Default.Edit, 
                        "Names", 
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                FilledIconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh, 
                        "Reset", 
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        if (showEditNames) {
            EditNamesDialog(
                currentT1 = state.team1Name,
                currentT2 = state.team2Name,
                onDismiss = { showEditNames = false },
                onConfirm = { t1, t2 ->
                    viewModel.updateNames(t1, t2)
                    showEditNames = false
                }
            )
        }

        if (showSetDetails) {
            SetDetailsDialog(
                setsHistory = state.setsHistory,
                team1Name = state.team1Name,
                team2Name = state.team2Name,
                onDismiss = { showSetDetails = false }
            )
        }

        if (state.matchWinner != 0) {
            WinnerOverlay(
                winnerName = if (state.matchWinner == 1) state.team1Name else state.team2Name,
                onReset = { viewModel.reset() }
            )
        }
    }
}

@Composable
fun ScoreCard(
    name: String,
    score: String,
    games: Int,
    isServing: Boolean,
    servingSide: String, // "R" for Right, "L" for Left
    color: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side: Name, Serving ball, Games
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isServing && servingSide == "L") {
                        BallIndicator(onLongClick)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    
                    Text(
                        text = name.uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isServing && servingSide == "R") {
                        Spacer(modifier = Modifier.width(6.dp))
                        BallIndicator(onLongClick)
                    }
                }
                Text(
                    text = "$games",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Visual Separation (Vertical line)
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .width(2.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )

            // Right Side: Score
            Box(
                modifier = Modifier
                    .weight(0.8f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = score,
                    color = color,
                    fontSize = 58.sp, // Slightly larger score
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun BallIndicator(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp) // Ball agrandie à 22dp pour une visibilité maximale
            .clip(CircleShape)
            .background(Color.Yellow)
            .clickable { onClick() }
    )
}

@Composable
fun EditNamesDialog(
    currentT1: String,
    currentT2: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var t1 by remember { mutableStateOf(currentT1) }
    var t2 by remember { mutableStateOf(currentT2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Joueurs", fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = t1, 
                    onValueChange = { t1 = it }, 
                    label = { Text("T1") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = t2, 
                    onValueChange = { t2 = it }, 
                    label = { Text("T2") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(t1, t2) }) { Text("OK") }
        }
    )
}

@Composable
fun SetDetailsDialog(
    setsHistory: List<Pair<Int, Int>>,
    team1Name: String,
    team2Name: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                "DÉTAILS DES SETS",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (setsHistory.isEmpty()) {
                    Text("Aucun set terminé", color = Color.Gray, fontSize = 16.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            team1Name.uppercase(),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                        Text("VS", modifier = Modifier.padding(horizontal = 4.dp), color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            team2Name.uppercase(),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.Black,
                            fontSize = 14.sp
                        )
                    }
                    setsHistory.forEachIndexed { index, set ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SET ${index + 1} : ", color = Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${set.first} - ${set.second}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FERMER", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
fun WinnerOverlay(winnerName: String, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable { onReset() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VICTOIRE !",
                color = Color.Yellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = winnerName,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "TAP POUR RESET",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}
