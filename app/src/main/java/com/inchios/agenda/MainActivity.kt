package com.inchios.agenda

import com.inchios.agenda.android.Availability
import android.app.Activity
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.inchios.agendapadel.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Helper functions for type conversion between java.time and kotlinx.datetime
fun java.time.LocalDate.toKotlinLocalDate() = kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)
fun kotlinx.datetime.LocalDate.toJavaLocalDate() = java.time.LocalDate.of(year, monthNumber, dayOfMonth)
fun java.time.LocalTime.toKotlinLocalTime() = kotlinx.datetime.LocalTime(hour, minute)
fun kotlinx.datetime.LocalTime.toJavaLocalTime() = java.time.LocalTime.of(hour, minute)

// Helper functions for overlap logic
fun getMaxConcurrentPlayers(availabilities: List<Availability>): Int {
    if (availabilities.isEmpty()) return 0
    val events = mutableListOf<Pair<kotlinx.datetime.LocalTime, Int>>()
    
    // Fallback pour les anciennes versions : on s'assure que chaque session compte au moins son créateur
    val processedAvails = availabilities.map { 
        if (it.participantIds.isEmpty()) it.copy(participantIds = listOf(it.userId)) else it
    }
    
    // On ne compte que les personnes qui sont "disponibles" pour former un match à 4.
    val lookingForQuatuor = processedAvails.filter { !it.isDuo || it.participantIds.size < 2 }
    
    // On groupe par UID pour être sûr de ne pas compter deux fois la même personne
    val uniqueParticipants = lookingForQuatuor.flatMap { it.participantIds }.distinct()
    
    // Pour chaque personne cherchant un match, on regarde ses créneaux
    processedAvails.filter { avail -> 
        avail.participantIds.any { id -> uniqueParticipants.contains(id) } && (!avail.isDuo || avail.participantIds.size < 2)
    }.forEach {
        events.add(it.startTime to 1)
        events.add(it.endTime to -1)
    }
    
    events.sortWith(compareBy({ it.first }, { it.second }))
    var max = 0
    var current = 0
    for (event in events) {
        current += event.second
        if (current > max) max = current
    }
    return max
}

fun getMatchSlots(availabilities: List<Availability>): List<Pair<kotlinx.datetime.LocalTime, kotlinx.datetime.LocalTime>> {
    val events = mutableListOf<Pair<kotlinx.datetime.LocalTime, Int>>()
    
    // Même logique : on ne cherche des créneaux de 4 que pour ceux qui ne sont pas en Duo confirmé
    val lookingForQuatuor = availabilities.filter { !it.isDuo || it.participantIds.size < 2 }
    val uniqueParticipants = lookingForQuatuor.flatMap { it.participantIds }.distinct()
    
    availabilities.filter { avail -> 
        avail.participantIds.any { id -> uniqueParticipants.contains(id) } && (!avail.isDuo || avail.participantIds.size < 2)
    }.forEach {
        events.add(it.startTime to 1)
        events.add(it.endTime to -1)
    }

    events.sortWith(compareBy({ it.first }, { it.second }))
    val slots = mutableListOf<Pair<kotlinx.datetime.LocalTime, kotlinx.datetime.LocalTime>>()
    var current = 0
    var start: kotlinx.datetime.LocalTime? = null
    for (event in events) {
        val prev = current
        current += event.second
        if (prev < 4 && current >= 4) {
            start = event.first
        } else if (prev >= 4 && current < 4) {
            if (start != null && event.first > start) {
                slots.add(start to event.first)
            }
            start = null
        }
    }
    return slots
}

enum class AppScreen {
    CALENDAR,
    STATS
}

class MainActivity : ComponentActivity() {
    private val viewModel: AvailabilityViewModel by viewModels()
    private val channelId = "agenda_matches"

    private var pendingNotificationType by mutableStateOf<String?>(null)
    private var pendingAvailabilityId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        createNotificationChannel()
        requestNotificationPermission()
        
        enableEdgeToEdge()
        setContent {
            AgendaTheme {
                var showSplash by remember { mutableStateOf(true) }
                var currentScreen by remember { mutableStateOf(AppScreen.CALENDAR) }
                var showPremiumDialog by remember { mutableStateOf(false) }

                if (showSplash) {
                    AnimatedSplashScreen(onFinished = { showSplash = false })
                } else {
                    val duoActions by viewModel.pendingDuoActions.collectAsStateWithLifecycle()
                    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
                    var showNotificationsList by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
                            when (screen) {
                                AppScreen.CALENDAR -> MainCalendarScreen(
                                    viewModel = viewModel,
                                    onNavigateToStats = { currentScreen = AppScreen.STATS },
                                    onRequestPremium = { showPremiumDialog = true },
                                    onShowNotifications = { showNotificationsList = true }
                                )
                                AppScreen.STATS -> {
                                    val userStats by viewModel.userStats.collectAsStateWithLifecycle()
                                    val groupRatings by viewModel.groupRatings.collectAsStateWithLifecycle()
                                    AndroidStatsScreen(
                                        isPremium = userProfile?.premium == true,
                                        stats = userStats,
                                        groupRatings = groupRatings,
                                        onBack = { currentScreen = AppScreen.CALENDAR },
                                        onPremiumClick = { showPremiumDialog = true }
                                    )
                                }
                            }
                        }

                        // Gestion de la notification "Alors, ce match ?"
                        if (pendingNotificationType == "CHECK_MATCH_PLAYED" && pendingAvailabilityId != null) {
                            val availabilities by viewModel.allAvailabilities.collectAsStateWithLifecycle()
                            val targetAvail = availabilities.find { it.id == pendingAvailabilityId }
                            
                            if (targetAvail != null) {
                                val isPremium = userProfile?.premium ?: false
                                val currentUid = viewModel.currentUser.collectAsStateWithLifecycle().value?.uid ?: ""
                                val isAlreadyConfirmed = targetAvail.completedBy.contains(currentUid)
                                val isMatchStarted = targetAvail.completedBy.isNotEmpty()
                                val notificationPartners = if (targetAvail.isDuo && isMatchStarted) {
                                    targetAvail.participantIds.filter { it != currentUid }.map { pid -> pid to "Partenaire" }
                                } else emptyList()
                                ReviewMatchDialog(
                                    isPremium = isPremium,
                                    isAlreadyConfirmed = isAlreadyConfirmed,
                                    isMe = targetAvail.userId == currentUid,
                                    partners = notificationPartners,
                                    onDismiss = { 
                                        pendingNotificationType = null
                                        pendingAvailabilityId = null
                                    },
                                    onConfirm = { ratings, mood, notes, duration ->
                                        viewModel.completeMatch(targetAvail, ratings, mood, notes, duration)
                                        pendingNotificationType = null
                                        pendingAvailabilityId = null
                                    },
                                    onNotPlayed = { 
                                        pendingNotificationType = null
                                        pendingAvailabilityId = null
                                    }
                                )
                            }
                        }

                        // Pop-up automatique pour valider un match récent
                        val matchToValidate by viewModel.pendingMatchToValidate.collectAsStateWithLifecycle()
                        if (matchToValidate != null) {
                            val currentUid = viewModel.currentUser.collectAsStateWithLifecycle().value?.uid ?: ""
                            val isMe = matchToValidate!!.userId == currentUid
                            val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()
                            val allAvailabilities by viewModel.allAvailabilities.collectAsStateWithLifecycle()

                            // Détection des partenaires par chevauchement (Quatuor) ou par participantIds (Duo)
                            val popupPartners = allAvailabilities.filter { other ->
                                other.dateString == matchToValidate!!.dateString &&
                                (other.groupId == matchToValidate!!.groupId || other.groupId.isBlank()) &&
                                (matchToValidate!!.startTime < other.endTime && matchToValidate!!.endTime > other.startTime)
                            }.flatMap { 
                                if (it.participantIds.isEmpty()) listOf(it.userId) else it.participantIds 
                            }.filter { it != currentUid }
                             .distinct()
                             .map { pid ->
                                 val name = groupMembers.find { it.userId == pid }?.displayName ?: "Partenaire"
                                 pid to name
                             }

                            ReviewMatchDialog(
                                isPremium = userProfile?.premium ?: false,
                                isAlreadyConfirmed = false,
                                isMe = isMe,
                                partners = popupPartners,
                                currentPartnerRatings = matchToValidate!!.partnerRatings,
                                onDismiss = { viewModel.dismissMatchToValidate() },
                                onConfirm = { ratings, mood, notes, duration ->
                                    viewModel.completeMatch(matchToValidate!!, ratings, mood, notes, duration)
                                    viewModel.dismissMatchToValidate()
                                },
                                onNotPlayed = { viewModel.dismissMatchToValidate() }
                            )
                        }

                        if (showPremiumDialog) {
                            val context = LocalContext.current
                            PremiumDialog(
                                onDismiss = { showPremiumDialog = false },
                                onConfirm = { 
                                    viewModel.becomePremium(context as Activity)
                                    showPremiumDialog = false 
                                }
                            )
                        }

                        if (showNotificationsList) {
                            NotificationsListOverlay(
                                invitations = duoActions,
                                onDismiss = { showNotificationsList = false },
                                onAction = { invitation ->
                                    // Fermer la liste et l'overlay de décision s'occupera du reste
                                    showNotificationsList = false
                                    // On force la date du calendrier sur celle de l'invitation
                                    viewModel.updateSelectedDate(kotlinx.datetime.LocalDate.parse(invitation.date))
                                }
                            )
                        }

                        if (duoActions.isNotEmpty()) {
                            val action = duoActions.first()
                            if (action.status == "PENDING") {
                                InvitationOverlay(
                                    invitation = action,
                                    onConfirm = { viewModel.confirmDuo(it) },
                                    onCounterPropose = { inv, start, end -> viewModel.updateDuoProposal(inv.id, start.toKotlinLocalTime(), end.toKotlinLocalTime()) },
                                    onDecline = { viewModel.declineDuo(it.id) }
                                )
                            } else if (action.status == "PROPOSED") {
                                NegotiationOverlay(
                                    invitation = action,
                                    onConfirm = { viewModel.confirmDuo(it) },
                                    onCounterPropose = { inv, start, end -> viewModel.updateDuoProposal(inv.id, start.toKotlinLocalTime(), end.toKotlinLocalTime()) },
                                    onDecline = { viewModel.declineDuo(it.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val type = intent?.getStringExtra("type")
        if (type != null) {
            pendingNotificationType = type
            pendingAvailabilityId = intent.getStringExtra("availabilityId")
        }

        // Sélection directe de la date depuis une notification
        val dateStr = intent?.getStringExtra("date")
        if (!dateStr.isNullOrBlank()) {
            try {
                viewModel.updateSelectedDate(kotlinx.datetime.LocalDate.parse(dateStr))
            } catch (e: Exception) {
                android.util.Log.e("NOTIFICATION", "Erreur parsing date notification: $dateStr")
            }
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.app_name)
        val descriptionText = getString(R.string.notifications_desc)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
            enableVibration(true)
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

@Composable
fun NotificationsListOverlay(
    invitations: List<DuoInvitation>,
    onDismiss: () -> Unit,
    onAction: (DuoInvitation) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() },
        contentAlignment = Alignment.TopEnd
    ) {
        Card(
            modifier = Modifier.padding(top = 60.dp, end = 16.dp).width(280.dp).clickable(enabled = false) { },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.notifications), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (invitations.isEmpty()) {
                    Text(stringResource(R.string.no_notifications), color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                        items(invitations) { inv ->
                            Card(
                                onClick = { onAction(inv) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (inv.status == "PROPOSED") Icons.Default.History else Icons.Default.People,
                                        contentDescription = null,
                                        tint = if (inv.status == "PROPOSED") Color(0xFFF57C00) else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            if (inv.status == "PROPOSED") stringResource(R.string.counter_proposal) else stringResource(R.string.duo_invitation),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            stringResource(R.string.invitation_from_on, if(inv.status == "PROPOSED") inv.toName else inv.fromName, inv.date),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationOverlay(
    invitation: DuoInvitation,
    onConfirm: (DuoInvitation) -> Unit,
    onCounterPropose: (DuoInvitation, LocalTime, LocalTime) -> Unit,
    onDecline: (DuoInvitation) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Valeurs par défaut basées sur la proposition initiale de l'envoyeur
    val initialStart = try { LocalTime.parse(invitation.proposedStartTime) } catch(_: Exception) { LocalTime.of(18, 0) }
    val initialEnd = try { LocalTime.parse(invitation.proposedEndTime) } catch(_: Exception) { LocalTime.of(19, 30) }

    if (showTimePicker) {
        TimeSelectionDialog(
            initialStart = initialStart,
            initialEnd = initialEnd,
            onDismiss = { 
                showTimePicker = false 
            },
            onConfirm = { s, e ->
                onCounterPropose(invitation, s, e)
                showTimePicker = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.duo_request), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.duo_request_desc, invitation.fromName, invitation.date), textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.proposed_time), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(stringResource(R.string.to_time, invitation.proposedStartTime, invitation.proposedEndTime), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onConfirm(invitation) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.validate_time))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.propose_other_time))
                }
                TextButton(onClick = { onDecline(invitation) }) {
                    Text(stringResource(R.string.refuse_invitation), color = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun NegotiationOverlay(
    invitation: DuoInvitation, 
    onConfirm: (DuoInvitation) -> Unit, 
    onCounterPropose: (DuoInvitation, LocalTime, LocalTime) -> Unit,
    onDecline: (DuoInvitation) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    if (showTimePicker) {
        TimeSelectionDialog(
            initialStart = LocalTime.parse(invitation.proposedStartTime),
            initialEnd = LocalTime.parse(invitation.proposedEndTime),
            onDismiss = { 
                showTimePicker = false 
            },
            onConfirm = { s, e ->
                onCounterPropose(invitation, s, e)
                showTimePicker = false
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.negotiation), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.proposes, invitation.toName), textAlign = TextAlign.Center)
                Text(stringResource(R.string.to_time, invitation.proposedStartTime, invitation.proposedEndTime), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onConfirm(invitation) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.validate)) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.counter_propose)) }
                TextButton(onClick = { onDecline(invitation) }) { Text(stringResource(R.string.cancel_duo), color = Color.Red) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectionDialog(initialStart: LocalTime, initialEnd: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime, LocalTime) -> Unit) {
    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val startState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = startTime.minute, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = endTime.hour, initialMinute = endTime.minute, is24Hour = true)

    if (showStartPicker) {
        AlertDialog(onDismissRequest = { showStartPicker = false }, confirmButton = { TextButton(onClick = { startTime = LocalTime.of(startState.hour, startState.minute); showStartPicker = false }) { Text(stringResource(R.string.ok)) } }, text = { TimePicker(state = startState) })
    }
    if (showEndPicker) {
        AlertDialog(onDismissRequest = { showEndPicker = false }, confirmButton = { TextButton(onClick = { endTime = LocalTime.of(endState.hour, endState.minute); showEndPicker = false }) { Text(stringResource(R.string.ok)) } }, text = { TimePicker(state = endState) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedCard(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.start))
                        Text(startTime.toString(), fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedCard(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.end))
                        Text(endTime.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(startTime, endTime) }) { Text(stringResource(R.string.propose)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AnimatedSplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(durationMillis = 1000), label = "splash_alpha")
    val scaleAnim = animateFloatAsState(targetValue = if (startAnimation) 1.2f else 0.5f, animationSpec = tween(durationMillis = 1000), label = "splash_scale")
    LaunchedEffect(key1 = true) { startAnimation = true; delay(2000); onFinished() }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1C1E)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(180.dp).graphicsLayer(alpha = alphaAnim.value, scaleX = scaleAnim.value, scaleY = scaleAnim.value))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(id = R.string.app_name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.graphicsLayer(alpha = alphaAnim.value))
        }
    }
}

@Composable
fun AgendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1A1C1E), onPrimary = Color.White, secondary = Color(0xFF0061A4), onSecondary = Color.White, surface = Color(0xFFFFFFFF), background = Color(0xFFF8F9FA), outline = Color(0xFFD1D5DB)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCalendarScreen(
    viewModel: AvailabilityViewModel, 
    onNavigateToStats: () -> Unit, 
    onRequestPremium: () -> Unit,
    onShowNotifications: () -> Unit
) {
    val availabilities by viewModel.filteredAvailabilities.collectAsStateWithLifecycle()
    val allAvailabilities by viewModel.allAvailabilities.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val showOnlyMySessions by viewModel.showOnlyMySessions.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val otherUserProfile by viewModel.otherUserProfile.collectAsStateWithLifecycle()
    val groupMembers by viewModel.groupMembers.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val duoActions by viewModel.pendingDuoActions.collectAsStateWithLifecycle()
    
    var showDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showOtherProfileDialog by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val context = LocalContext.current
    val matchSlots = remember(availabilities) { getMatchSlots(availabilities) }
    val googleSignInLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener { if (it.isSuccessful) viewModel.updateCurrentUser(FirebaseAuth.getInstance().currentUser) }
            }
        } catch (_: Exception) { Toast.makeText(context, "Erreur de connexion", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.agenda_padel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (currentUser != null) {
                            Text(stringResource(R.string.matches_session, allAvailabilities.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onShowNotifications) {
                        BadgedBox(
                            badge = {
                                if (duoActions.isNotEmpty()) {
                                    Badge { Text(duoActions.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White)
                        }
                    }
                    IconButton(onClick = onNavigateToStats) { Icon(Icons.Default.BarChart, contentDescription = null, tint = if (userProfile?.premium == true) Color(0xFFFFD700) else Color.White) }
                    IconButton(onClick = { viewModel.toggleMySessions() }) { Icon(if (showOnlyMySessions) Icons.Default.Person else Icons.Default.PeopleOutline, contentDescription = null, tint = if (showOnlyMySessions) Color(0xFF22C55E) else Color.White) }
                    IconButton(onClick = { showHelpDialog = true }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = Color.White) }
                    if (currentUser == null) {
                        IconButton(onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(context.getString(R.string.default_web_client_id)).requestEmail().build()
                            googleSignInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                        }) { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                    } else {
                        IconButton(onClick = { if (userProfile != null) showProfileDialog = true }) {
                            Box {
                                AsyncImage(
                                    model = userProfile?.photoUrl ?: currentUser?.photoUrl, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(28.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape), 
                                    contentScale = ContentScale.Crop
                                )
                                if (userProfile?.premium == true) {
                                    Icon(
                                        Icons.Default.Star, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(12.dp).align(Alignment.BottomEnd).background(Color.Black, CircleShape).padding(1.dp), 
                                        tint = Color(0xFFFFD700)
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // SÉLECTEUR DE GROUPE (MENU DÉROULANT)
            if (currentUser != null && userProfile != null) {
                var expanded by remember { mutableStateOf(false) }
                val currentGroup = groups[userProfile!!.currentGroupId]
                val currentGroupDisplay = if (currentGroup != null && currentGroup.name != userProfile!!.currentGroupId) {
                    "${currentGroup.name} (${userProfile!!.currentGroupId})"
                } else {
                    userProfile!!.currentGroupId
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = currentGroupDisplay,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.active_group)) },
                            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            userProfile!!.groupIds.distinct().forEach { groupId ->
                                val group = groups[groupId]
                                val groupName = group?.name ?: groupId
                                val isSelected = userProfile!!.currentGroupId == groupId

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = groupName,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Unspecified
                                                )
                                                if (group != null && groupName != groupId) {
                                                    Text(
                                                        text = groupId,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.switchGroup(groupId)
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            MonthCalendarView(selectedDate = selectedDate.toJavaLocalDate(), currentMonth = currentMonth, allAvailabilities = allAvailabilities, onDateSelected = { if (selectedDate.toJavaLocalDate() == it) showDialog = true else viewModel.updateSelectedDate(it.toKotlinLocalDate()) }, onMonthChange = { currentMonth = it })

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) }, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.search_players), fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) }, shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.secondary))
                if (currentUser != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(onClick = { showDialog = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.size(56.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, contentDescription = null) }
                }
            }
            if (matchSlots.isNotEmpty()) MatchBadge(matchSlots)
            if (availabilities.isEmpty()) Box(modifier = Modifier.weight(1f)) { EmptyState() }
            else {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // On s'assure de ne montrer qu'une seule fois un joueur pour le même créneau horaire précis
                    val distinctAvailabilities = availabilities.distinctBy { "${it.userId}_${it.startTimeString}_${it.endTimeString}" }
                    
                    items(distinctAvailabilities, key = { it.id }) { item ->
                        val group = groups[item.groupId]
                        val groupLabel = if (item.groupId != userProfile?.currentGroupId || showOnlyMySessions) {
                            if (group != null && group.name != item.groupId) "${group.name} (${item.groupId})"
                            else item.groupId
                        } else null

                        // LOGIQUE DE DÉTECTION DES PARTENAIRES (Même session + Chevauchements)
                        val allDetectedPartners = allAvailabilities.filter { other ->
                            other.dateString == item.dateString && 
                            (other.groupId == item.groupId || other.groupId.isBlank()) &&
                            (item.startTime < other.endTime && item.endTime > other.startTime)
                        }.flatMap { 
                            if (it.participantIds.isEmpty()) listOf(it.userId) else it.participantIds 
                        }.filter { it != currentUser?.uid } // On s'enlève soi-même
                         .distinct()
                         .map { pid ->
                            val member = groupMembers.find { it.userId == pid }
                            val name = member?.displayName ?: "Joueur"
                            pid to name
                         }

                        val currentParticipants = if (item.participantIds.isEmpty()) listOf(item.userId) else item.participantIds
                        val isMatchFull = if (item.isDuo) currentParticipants.size >= 2 else currentParticipants.size >= 4
                        val canCompleteSlot = currentParticipants.contains(currentUser?.uid) && (allDetectedPartners.isNotEmpty() || isMatchFull)

                        AvailabilityItem(
                            item = item, 
                            groupName = groupLabel,
                            canDelete = currentUser?.uid == item.userId, 
                            canProposeDuo = currentUser?.uid != item.userId && !item.isDuo, 
                            canComplete = canCompleteSlot,
                            isPremium = userProfile?.premium == true,
                            currentUserId = currentUser?.uid ?: "",
                            partners = allDetectedPartners,
                            allMembers = groupMembers,
                            onDelete = { viewModel.deleteAvailability(item) }, 
                            onProposeDuo = { viewModel.proposeDuo(item) }, 
                            onCompleteMatch = { ratings, mood, notes, duration -> 
                            viewModel.completeMatch(item, ratings, mood, notes, duration) 
                        },
                            onProfileClick = { viewModel.fetchOtherUserProfile(item.userId); showOtherProfileDialog = true }
                        )
                    }
                }
            }
        }
        if (showDialog) AddAvailabilityDialog(initialDate = selectedDate.toJavaLocalDate(), onDismiss = { showDialog = false }, onConfirm = { n, d, s, e, l, c, id, am -> 
            if (am) {
                // Cas "Moi + Ami" : on crée une seule session Duo avec les deux noms
                viewModel.addAvailability("", d.toKotlinLocalDate(), s.toKotlinLocalTime(), e.toKotlinLocalTime(), l, c, false, n)
            } else {
                viewModel.addAvailability(n, d.toKotlinLocalDate(), s.toKotlinLocalTime(), e.toKotlinLocalTime(), l, c, id)
            }
            showDialog = false 
        })
        if (showHelpDialog) HelpDialog(onDismiss = { showHelpDialog = false })
        if (showProfileDialog && userProfile != null) {
            val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
            ProfileDialog(profile = userProfile!!, groupMembers = groupMembers, groups = groups, isUploading = isUploading, onDismiss = { showProfileDialog = false }, onSave = { viewModel.saveUserProfile(it) }, onLogout = { showProfileDialog = false; showLogoutDialog = true }, onJoinGroup = { viewModel.joinGroup(it) }, onLeaveGroup = { viewModel.leaveGroup(it) }, onSwitchGroup = { viewModel.switchGroup(it) }, onUpdateGroupName = { id, name -> viewModel.updateGroupName(id, name) }, onPremiumClick = { showProfileDialog = false; onRequestPremium() }, onPhotoSelected = { viewModel.uploadProfilePicture(it) }, onDeleteAccount = { viewModel.deleteAccount { showProfileDialog = false } })
        }
        if (showOtherProfileDialog) {
            OtherProfileDialog(
                profile = otherUserProfile,
                isCurrentUserPremium = userProfile?.premium == true,
                onWhatsAppClick = { phone ->
                    if (userProfile?.premium == true) {
                        viewModel.contactViaWhatsApp(phone)
                    } else {
                        onRequestPremium()
                    }
                },
                onDismiss = { showOtherProfileDialog = false; viewModel.clearOtherUserProfile() }
            )
        }
        if (showLogoutDialog) AlertDialog(onDismissRequest = { showLogoutDialog = false }, title = { Text(stringResource(R.string.logout)) }, text = { Text(stringResource(R.string.logout_confirm)) }, confirmButton = { Button(onClick = { FirebaseAuth.getInstance().signOut(); viewModel.updateCurrentUser(null); showLogoutDialog = false }) { Text(stringResource(R.string.logout)) } }, dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.premium), fontWeight = FontWeight.Bold) 
            } 
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.premium_desc), style = MaterialTheme.typography.bodyMedium)
                
                PremiumFeatureItem(Icons.Default.Groups, stringResource(R.string.unlimited_groups), stringResource(R.string.unlimited_groups_desc))
                PremiumFeatureItem(Icons.Default.BarChart, stringResource(R.string.advanced_stats), stringResource(R.string.advanced_stats_desc))
                PremiumFeatureItem(Icons.Default.NotificationsActive, stringResource(R.string.priority_alerts), stringResource(R.string.priority_alerts_desc))
                PremiumFeatureItem(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.whatsapp_direct), stringResource(R.string.whatsapp_direct_desc))
                PremiumFeatureItem(Icons.Default.Verified, stringResource(R.string.badge_prestige), stringResource(R.string.badge_prestige_desc))
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFD700).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp), 
                    contentAlignment = Alignment.Center
                ) { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.one_time_payment), fontWeight = FontWeight.Bold, color = Color(0xFFB8860B), fontSize = 12.sp)
                        Text("9,99 €", fontWeight = FontWeight.Black, color = Color(0xFFB8860B), fontSize = 24.sp)
                        Text(stringResource(R.string.lifetime_access), style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8860B))
                    }
                }

                // Mot du développeur
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.developer_note),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        stringResource(R.string.developer_message),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = onConfirm, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) { 
                Text(stringResource(R.string.unlock_premium), fontWeight = FontWeight.Bold) 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { 
                Text(stringResource(R.string.maybe_later), color = Color.Gray) 
            } 
        }
    )
}

@Composable
fun PremiumFeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column { 
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray) 
        }
    }
}

@Composable
fun OtherProfileDialog(profile: UserProfile?, isCurrentUserPremium: Boolean, onWhatsAppClick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profile?.displayName ?: stringResource(R.string.other_profile), fontWeight = FontWeight.Bold) },
        text = {
            if (profile == null) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AsyncImage(model = profile.photoUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
                    Spacer(modifier = Modifier.height(16.dp))
                    ProfileInfoItem(Icons.Default.Star, stringResource(R.string.level), profile.level)
                    ProfileInfoItem(Icons.Default.Cake, stringResource(R.string.age), if(profile.age.isNotBlank()) "${profile.age} ${stringResource(R.string.years)}" else stringResource(R.string.not_specified))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProfileInfoItem(Icons.Default.Phone, stringResource(R.string.phone), profile.phone.ifBlank { stringResource(R.string.not_specified) })
                        }
                        if (profile.phone.isNotBlank()) {
                            IconButton(
                                onClick = { onWhatsAppClick(profile.phone) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isCurrentUserPremium) Color(0xFF25D366) else Color.Gray.copy(alpha = 0.2f),
                                    contentColor = if (isCurrentUserPremium) Color.White else Color.Gray
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp")
                            }
                        }
                    }
                    
                    ProfileInfoItem(Icons.Default.Person, stringResource(R.string.gender), profile.gender)
                    
                    if (!isCurrentUserPremium && profile.phone.isNotBlank()) {
                        Text(
                            stringResource(R.string.premium_whatsapp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun ProfileInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(modifier = Modifier.width(12.dp))
        Column { Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileDialog(profile: UserProfile, groupMembers: List<UserProfile>, groups: Map<String, Group>, isUploading: Boolean, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit, onLogout: () -> Unit, onJoinGroup: (String) -> Unit, onLeaveGroup: (String) -> Unit, onSwitchGroup: (String) -> Unit, onUpdateGroupName: (String, String) -> Unit, onPremiumClick: () -> Unit, onPhotoSelected: (Uri) -> Unit, onDeleteAccount: () -> Unit) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var age by remember { mutableStateOf(profile.age) }
    var phone by remember { mutableStateOf(profile.phone) }
    var selectedLevel by remember { mutableStateOf(profile.level) }
    var selectedGender by remember { mutableStateOf(profile.gender) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var expandedLevel by remember { mutableStateOf(false) }
    var customPLevel by remember { mutableStateOf("") }
    val levelsNoP = listOf("Loisir", "Amateur", "Intermédiaire", "Compétitif")
    val genders = listOf(stringResource(R.string.male), stringResource(R.string.female), stringResource(R.string.other))
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { it?.let { onPhotoSelected(it) } }

    if (showJoinDialog) JoinGroupDialog(onDismiss = { showJoinDialog = false }, onConfirm = { onJoinGroup(it); showJoinDialog = false })
    if (editingGroupId != null) AlertDialog(onDismissRequest = { editingGroupId = null }, title = { Text(stringResource(R.string.name_group)) }, text = { OutlinedTextField(value = newGroupName, onValueChange = { newGroupName = it }, label = { Text(stringResource(R.string.new_name)) }, singleLine = true) }, confirmButton = { Button(onClick = { onUpdateGroupName(editingGroupId!!, newGroupName); editingGroupId = null; newGroupName = "" }) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { editingGroupId = null }) { Text(stringResource(R.string.cancel)) } })
    if (showDeleteConfirm) AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text(stringResource(R.string.delete_account_confirm)) }, text = { Text(stringResource(R.string.delete_account_desc)) }, confirmButton = { Button(onClick = { showDeleteConfirm = false; onDeleteAccount() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(stringResource(R.string.confirm_deletion)) } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.my_profile), fontWeight = FontWeight.Bold); if (profile.premium) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp)) } } },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box { AsyncImage(model = profile.photoUrl ?: FirebaseAuth.getInstance().currentUser?.photoUrl, contentDescription = null, modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape), contentScale = ContentScale.Crop); if (isUploading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary); IconButton(onClick = { photoPickerLauncher.launch("image/*") }, modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.secondary, CircleShape).size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) } } } }
                if (!profile.premium) item { Card(onClick = onPremiumClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.15f)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFB8860B)); Spacer(modifier = Modifier.width(12.dp)); Column { Text(stringResource(R.string.premium), fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(stringResource(R.string.unlock_all_advantages), fontSize = 11.sp) } } } }
                item { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))) { Column(modifier = Modifier.padding(12.dp)) { Text(stringResource(R.string.invite_code), style = MaterialTheme.typography.labelSmall); Row(verticalAlignment = Alignment.CenterVertically) { Text(profile.inviteCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary); IconButton(onClick = { val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Rejoins mon groupe sur Agenda Padel avec le code : ${profile.inviteCode}"); type = "text/plain" }; context.startActivity(Intent.createChooser(sendIntent, null)) }) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) } } } } }
                item {
                    Text(stringResource(R.string.my_groups), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    profile.groupIds.distinct().forEach { groupId ->
                        val group = groups[groupId]
                        val isOwner = profile.inviteCode == groupId
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if(profile.currentGroupId == groupId) Color(0xFFF3F4F6) else Color.Transparent, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = profile.currentGroupId == groupId, onClick = { onSwitchGroup(groupId) })
                                Column(modifier = Modifier.weight(1f)) { Text(group?.name ?: groupId, fontWeight = if(profile.currentGroupId == groupId) FontWeight.Bold else FontWeight.Normal); if (group != null) Text(groupId, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                                if (isOwner) IconButton(onClick = { editingGroupId = groupId; newGroupName = group?.name ?: "" }) { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp)) }
                                if (profile.groupIds.size > 1) IconButton(onClick = { onLeaveGroup(groupId) }) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) }
                            }
                            if (profile.currentGroupId == groupId) { Spacer(modifier = Modifier.height(8.dp)); Text(stringResource(R.string.participants, groupMembers.size), style = MaterialTheme.typography.labelSmall, color = Color.Gray); groupMembers.forEach { member -> Row(modifier = Modifier.padding(start = 32.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)); Spacer(modifier = Modifier.width(8.dp)); Text(member.displayName, fontSize = 12.sp); if (member.userId == profile.userId) Text(stringResource(R.string.me), fontSize = 10.sp, color = Color.Gray) } } }
                        }
                    }
                    OutlinedButton(onClick = { showJoinDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Default.GroupAdd, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.join_group)) }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(R.string.display_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text(stringResource(R.string.age)) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(R.string.phone)) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }
                item { 
                    Text(stringResource(R.string.level), style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(expanded = expandedLevel, onExpandedChange = { expandedLevel = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLevel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLevel) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedLevel, onDismissRequest = { expandedLevel = false }) {
                    levelsNoP.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level) },
                            onClick = { 
                                selectedLevel = level
                                if (level == "Compétitif" && customPLevel.isBlank()) customPLevel = "P1000"
                                expandedLevel = false
                            }
                        )
                    }
                        }
                    }
                    if (selectedLevel == "Compétitif") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPLevel,
                            onValueChange = { customPLevel = it.uppercase().take(6) },
                            label = { Text(stringResource(R.string.p_level_custom)) },
                            placeholder = { Text("P1000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                item { 
                    Text(stringResource(R.string.gender), style = MaterialTheme.typography.labelMedium)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        genders.forEach { gender -> FilterChip(selected = selectedGender == gender, onClick = { selectedGender = gender }, label = { Text(gender) }) } 
                    } 
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.logout)) }
                        
                        // Suppression locale (Firebase + Data)
                        TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.delete_account), fontSize = 12.sp) }
                        
                        // Lien de suppression WEB (Requis Google Play)
                        TextButton(
                            onClick = { 
                                val intent = Intent(Intent.ACTION_VIEW, "https://forms.gle/bcmT988aWB1SRJA99".toUri())
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                            modifier = Modifier.fillMaxWidth()
                        ) { 
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.request_account_deletion), fontSize = 11.sp, textDecoration = TextDecoration.Underline) 
                        }

                        // LIEN RGPD
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            modifier = Modifier.fillMaxWidth().clickable { 
                                val intent = Intent(Intent.ACTION_VIEW, "https://docs.google.com/document/d/e/2PACX-1vT-VOTRE-LIEN-PUBLIC/pub".toUri())
                                context.startActivity(intent)
                            }.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }
        },
        confirmButton = { 
            val finalLevel = if (selectedLevel == "Compétitif" && customPLevel.isNotBlank()) customPLevel else selectedLevel
            Button(onClick = { onSave(profile.copy(displayName = displayName, age = age, phone = phone, level = finalLevel, gender = selectedGender)); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun JoinGroupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.join_group)) }, text = { OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text(stringResource(R.string.group_code)) }, placeholder = { Text(stringResource(R.string.example_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onConfirm(code) }) { Text(stringResource(R.string.join)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.help_desc), style = MaterialTheme.typography.bodyMedium)
                HelpSection(stringResource(R.string.availability_colors), listOf(HelpItemData(Color(0xFFEF4444), stringResource(R.string.red)), HelpItemData(Color(0xFFF57C00), stringResource(R.string.orange)), HelpItemData(Color(0xFF22C55E), stringResource(R.string.green))))
                HelpSection(stringResource(R.string.how_it_works), listOf(
                    HelpItemData(null, stringResource(R.string.add_availability)), 
                    HelpItemData(null, stringResource(R.string.duo)), 
                    HelpItemData(null, stringResource(R.string.match_validation_desc)),
                    HelpItemData(null, stringResource(R.string.stats_desc)),
                    HelpItemData(null, stringResource(R.string.notifications_desc))
                ))
                HelpSection(stringResource(R.string.groups), listOf(HelpItemData(null, stringResource(R.string.join_desc)), HelpItemData(null, stringResource(R.string.invite_desc)), HelpItemData(null, stringResource(R.string.switch_desc))))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } }
    )
}

data class HelpItemData(val color: Color?, val text: String)

@Composable
fun HelpSection(title: String, items: List<HelpItemData>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.color != null) { Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(item.color)); Spacer(modifier = Modifier.width(8.dp)) }
                else { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF9CA3AF)); Spacer(modifier = Modifier.width(8.dp)) }
                Text(item.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MatchBadge(slots: List<Pair<kotlinx.datetime.LocalTime, kotlinx.datetime.LocalTime>>) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    Surface(color = Color(0xFFFFF1F0), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCCC7))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFCF1322)); Spacer(modifier = Modifier.width(8.dp)); Text(stringResource(R.string.match_possible), color = Color(0xFFCF1322), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            slots.forEach { slot -> Text(text = stringResource(R.string.between_times, slot.first.toJavaLocalTime().format(formatter), slot.second.toJavaLocalTime().format(formatter)), color = Color(0xFFCF1322), fontSize = 12.sp, modifier = Modifier.padding(start = 32.dp, top = 2.dp)) }
        }
    }
}

@Composable
fun AvailabilityItem(
    item: Availability, 
    groupName: String? = null, 
    canDelete: Boolean, 
    canProposeDuo: Boolean, 
    canComplete: Boolean, 
    isPremium: Boolean = false, 
    currentUserId: String = "", 
    partners: List<Pair<String, String>> = emptyList(), 
    allMembers: List<UserProfile> = emptyList(),
    onDelete: () -> Unit, 
    onProposeDuo: () -> Unit, 
    onCompleteMatch: (Map<String?, Int>, String, String, Int) -> Unit, 
    onProfileClick: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val displayDate = try { LocalDate.parse(item.dateString).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } catch(_: Exception) { "" }
    var showReviewDialog by remember { mutableStateOf(false) }
    val isAlreadyConfirmed = item.completedBy.contains(currentUserId)
    val isValidatedByOthers = item.completedBy.isNotEmpty() && !isAlreadyConfirmed
    
    // Récupérer les noms réels des participants pour l'affichage
    val participantNames = remember(item.participantIds, item.userId, allMembers) {
        val ids = if (item.participantIds.isEmpty()) listOf(item.userId) else item.participantIds
        ids.map { id ->
            if (id == currentUserId) "Moi" 
            else allMembers.find { it.userId == id }?.displayName ?: "Joueur"
        }
    }

    if (showReviewDialog) {
        ReviewMatchDialog(
            isPremium = isPremium,
            isAlreadyConfirmed = isAlreadyConfirmed,
            isMe = canDelete,
            initialRating = if (item.rating > 0) item.rating else 4,
            initialMood = item.mood.ifBlank { null },
            initialNotes = item.reviewNotes,
            initialDuration = item.durationMinutes,
            partners = partners,
            currentPartnerRatings = item.partnerRatings,
            onDismiss = { showReviewDialog = false },
            onConfirm = { ratings, mood, notes, duration ->
                onCompleteMatch(ratings, mood, notes, duration)
                showReviewDialog = false
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { onProfileClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if(item.isCompleted) Color(0xFFF0FDF4) else Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.userPhoto != null) AsyncImage(model = item.userPhoto, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) { Text(item.personName.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.personName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (item.isUserPremium) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp)) }
                        if (item.isCompleted) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Le $displayDate", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                if (item.isDuo) { 
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(color = if (item.participantIds.size >= 2) Color(0xFF22C55E).copy(alpha = 0.1f) else Color(0xFFE0F2FE), shape = RoundedCornerShape(4.dp)) { 
                                        Text(
                                            if (item.participantIds.size >= 2) "DUO CONFIRMÉ" else "DUO",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                                            fontSize = 9.sp, 
                                            color = if (item.participantIds.size >= 2) Color(0xFF166534) else Color(0xFF0369A1), 
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false
                                        ) 
                                    } 
                                }
                            }
                            
                            // Liste des noms des participants (très important pour la visibilité)
                            if (item.participantIds.size > 1) {
                                Text(
                                    text = "Avec : ${participantNames.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (isValidatedByOthers) {
                                Text(
                                    text = stringResource(R.string.match_already_validated),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF57C00),
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (groupName != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = groupName,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(6.dp)) { Text(text = "${item.startTime.toJavaLocalTime().format(timeFormatter)} - ${item.endTime.toJavaLocalTime().format(timeFormatter)}", modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                
                if (canComplete) {
                    IconButton(onClick = { showReviewDialog = true }) {
                        Icon(Icons.Default.RateReview, contentDescription = "Valider le match", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.LightGray) }
                else if (canProposeDuo) IconButton(onClick = onProposeDuo) { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            }
            if (item.location.isNotBlank()) { Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary); Spacer(modifier = Modifier.width(4.dp)); Text(item.location, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) } }
            
            if (item.isCompleted && item.rating > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { i ->
                        Icon(
                            Icons.Default.Star, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp), 
                            tint = if (i < item.rating) Color(0xFFFFD700) else Color.LightGray
                        )
                    }
                    if (item.mood.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.mood, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = Color.Gray)
                    }
                }
            }
            
            if (item.comment.isNotBlank() || item.reviewNotes.isNotBlank()) { 
                Spacer(modifier = Modifier.height(4.dp))
                val displayText = if (item.isCompleted && item.reviewNotes.isNotBlank()) item.reviewNotes else item.comment
                Text(text = displayText, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(start = 18.dp)) 
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReviewMatchDialog(
    isPremium: Boolean = false,
    isAlreadyConfirmed: Boolean = false,
    isMe: Boolean = true,
    initialRating: Int = 4,
    initialMood: String? = null,
    initialNotes: String = "",
    initialDuration: Int = 60,
    partners: List<Pair<String, String>> = emptyList(), // List of (partnerId, partnerName)
    currentPartnerRatings: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit, 
    onConfirm: (Map<String?, Int>, String, String, Int) -> Unit,
    onNotPlayed: (String) -> Unit = {}
) {
    val defaultMood = stringResource(R.string.mood_good)
    
    // On utilise une map pour stocker toutes les notes (clé null = Moi)
    val ratingsMap = remember { 
        mutableStateMapOf<String?, Int>().apply {
            this[null] = initialRating
            partners.forEach { (pid, _) ->
                this[pid] = currentPartnerRatings[pid] ?: 4
            }
        }
    }

    var mood by remember { mutableStateOf(initialMood ?: defaultMood) }
    var notes by remember { mutableStateOf(initialNotes) }
    var duration by remember { mutableIntStateOf(initialDuration) }
    
    val durations = listOf(60, 90, 120)
    val moods = listOf(stringResource(R.string.mood_excellent), stringResource(R.string.mood_good), stringResource(R.string.mood_average), stringResource(R.string.mood_difficult))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(stringResource(R.string.validate_match), fontWeight = FontWeight.Bold)
                if (isAlreadyConfirmed) {
                    Text(
                        text = stringResource(R.string.match_already_validated),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF57C00)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Section "MA PERFORMANCE"
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.my_performance), style = MaterialTheme.typography.labelMedium)
                    Row {
                        repeat(5) { i ->
                            IconButton(onClick = { ratingsMap[null] = i + 1 }) {
                                Icon(
                                    if (i < (ratingsMap[null] ?: 0)) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = null,
                                    tint = if (i < (ratingsMap[null] ?: 0)) Color(0xFFFFD700) else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                // Section "NOTER LES AUTRES" (si présents)
                if (partners.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.rate_partner), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
                    partners.forEach { (pid, pname) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pname, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Row {
                                    repeat(5) { i ->
                                        Icon(
                                            if (i < (ratingsMap[pid] ?: 0)) Icons.Default.Star else Icons.Default.StarOutline,
                                            contentDescription = null,
                                            tint = if (i < (ratingsMap[pid] ?: 0)) Color(0xFFFFD700) else Color.Gray,
                                            modifier = Modifier.size(24.dp).clickable { ratingsMap[pid] = i + 1 }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Column {
                    Text(stringResource(R.string.mood_feeling), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        moods.forEach { m ->
                            FilterChip(
                                selected = mood == m,
                                onClick = { mood = m },
                                label = { Text(m) }
                            )
                        }
                    }
                }

                if (isPremium && isMe) {
                    Column {
                        Text(stringResource(R.string.how_long), style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            durations.forEach { d ->
                                FilterChip(
                                    selected = duration == d,
                                    onClick = { duration = d },
                                    label = { Text("${d}min") }
                                )
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.comment)) },
                    placeholder = { Text(stringResource(R.string.comment_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                TextButton(
                    onClick = { onNotPlayed("ANNULÉ") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.did_not_play), color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(ratingsMap.toMap(), mood, notes, if (isPremium) duration else 60) }) {
                Text(stringResource(R.string.validate_match))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun MonthCalendarView(selectedDate: LocalDate, currentMonth: YearMonth, allAvailabilities: List<Availability>, onDateSelected: (LocalDate) -> Unit, onMonthChange: (YearMonth) -> Unit) {
    val daysInMonth = currentMonth.lengthOfMonth(); val firstDayOfMonth = (currentMonth.atDay(1).dayOfWeek.value - 1) % 7
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, contentDescription = null) }; Text(text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold); IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, contentDescription = null) } }
            Spacer(modifier = Modifier.height(12.dp)); Row(modifier = Modifier.fillMaxWidth()) { listOf("L", "M", "M", "J", "V", "S", "D").forEach { day -> Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Color.LightGray) } }
            Spacer(modifier = Modifier.height(8.dp)); val rows = (daysInMonth + firstDayOfMonth + 6) / 7
            for (row in 0 until rows) { Row(modifier = Modifier.fillMaxWidth()) { for (col in 0 until 7) { val cellIndex = row * 7 + col; val dayNumber = cellIndex - firstDayOfMonth + 1; Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { if (dayNumber in 1..daysInMonth) {
                                    val date = currentMonth.atDay(dayNumber)
                                    val isSelected = date == selectedDate
                                    val dayAvailabilities = allAvailabilities.filter { it.dateString == date.toString() }
                                    
                                    // Calcul de la couleur du jour
                                    val maxConcurrent = getMaxConcurrentPlayers(dayAvailabilities)
                                    val hasConfirmedDuo = dayAvailabilities.any { it.isDuo && it.participantIds.size >= 2 }
                                    
                                    val status = when {
                                        maxConcurrent >= 4 || hasConfirmedDuo -> "MATCH"
                                        dayAvailabilities.isNotEmpty() -> "PARTIAL"
                                        else -> "NONE"
                                    }
                                    DayCell(date, isSelected, status) { onDateSelected(date) }
                                } }
 } } }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, isSelected: Boolean, status: String, onClick: () -> Unit) {
    val indicatorColor = when(status) { "MATCH" -> Color(0xFF22C55E); "PARTIAL" -> Color(0xFFF57C00); else -> Color(0xFFEF4444) }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else indicatorColor.copy(alpha = 0.12f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(backgroundColor).then(if (!isSelected) Modifier.border(1.dp, indicatorColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)) else Modifier).clickable { onClick() }.padding(4.dp), verticalArrangement = Arrangement.Center) { Text(text = date.dayOfMonth.toString(), color = if (isSelected) Color.White else Color.Black, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp); Spacer(modifier = Modifier.height(4.dp)); Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isSelected) Color.White else indicatorColor)) }
}

@Composable
fun EmptyState() { Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFE5E7EB)); Text(stringResource(R.string.no_sessions), color = Color.Gray, style = MaterialTheme.typography.bodyMedium) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAvailabilityDialog(initialDate: LocalDate, onDismiss: () -> Unit, onConfirm: (String, LocalDate, LocalTime, LocalTime, String, String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var comment by remember { mutableStateOf("") }; var selectedDate by remember { mutableStateOf(initialDate) }; var startTime by remember { mutableStateOf(LocalTime.of(18, 0)) }; var endTime by remember { mutableStateOf(LocalTime.of(20, 0)) }
    var participantType by remember { mutableIntStateOf(0) }; var showDatePicker by remember { mutableStateOf(false) }; var showStartTimePicker by remember { mutableStateOf(false) }; var showEndTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val enterNameError = stringResource(R.string.enter_name)
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    val startTimePickerState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = startTime.minute, is24Hour = true)
    val endTimePickerState = rememberTimePickerState(initialHour = endTime.hour, initialMinute = endTime.minute, is24Hour = true)

    if (showDatePicker) DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text(stringResource(R.string.ok)) } }) { DatePicker(state = datePickerState) }
    if (showStartTimePicker) AlertDialog(onDismissRequest = { showStartTimePicker = false }, confirmButton = { TextButton(onClick = { startTime = LocalTime.of(startTimePickerState.hour, startTimePickerState.minute); showStartTimePicker = false }) { Text(stringResource(R.string.ok)) } }, text = { TimePicker(state = startTimePickerState) })
    if (showEndTimePicker) AlertDialog(onDismissRequest = { showEndTimePicker = false }, confirmButton = { TextButton(onClick = { endTime = LocalTime.of(endTimePickerState.hour, endTimePickerState.minute); showEndTimePicker = false }) { Text(stringResource(R.string.ok)) } }, text = { TimePicker(state = endTimePickerState) })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_session), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.who_plays), style = MaterialTheme.typography.labelMedium, color = Color.Gray); Spacer(modifier = Modifier.height(8.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { val options = listOf(stringResource(R.string.just_me), stringResource(R.string.me_plus_friend), stringResource(R.string.friend_alone), stringResource(R.string.two_friends)); options.forEachIndexed { index, text -> FilterChip(selected = participantType == index, onClick = { participantType = index }, label = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, modifier = Modifier.fillMaxWidth()) } } }
                if (participantType != 0) item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if(participantType == 1) stringResource(R.string.friend_name) else stringResource(R.string.guest_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text(stringResource(R.string.club)) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }) }
                item { OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text(stringResource(R.string.comment)) }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { val tf = DateTimeFormatter.ofPattern("HH:mm"); OutlinedCard(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) { Column(modifier = Modifier.padding(12.dp)) { Text(stringResource(R.string.start), style = MaterialTheme.typography.labelSmall); Text(startTime.format(tf), fontWeight = FontWeight.Bold) } }; OutlinedCard(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) { Column(modifier = Modifier.padding(12.dp)) { Text(stringResource(R.string.end), style = MaterialTheme.typography.labelSmall); Text(endTime.format(tf), fontWeight = FontWeight.Bold) } } } }
            }
        },
        confirmButton = { Button(onClick = { if (participantType != 0 && name.isBlank()) Toast.makeText(context, enterNameError, Toast.LENGTH_SHORT).show() else { when(participantType) { 0 -> onConfirm("", selectedDate, startTime, endTime, location, comment, false, false); 1 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, false, true); 2 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, false, false); 3 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, true, false) } } }) { Text(stringResource(R.string.confirm)) } }
    )
}
