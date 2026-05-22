package com.inchios.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.inchios.agendapadel.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidStatsScreen(
    isPremium: Boolean,
    stats: UserStats,
    groupRatings: List<MemberRatingSummary>,
    onBack: () -> Unit,
    onPremiumClick: () -> Unit
) {
    // We use the shared version to ensure it works on iOS
    // But for Android, we keep the original implementation for now to avoid resource issues
    // OR we just call the SharedStatsScreen if we are sure about the resource handling.
    
    // To respect "no impact on current android app", let's keep the original UI code here 
    // but the shared module now has a copy that can be evolved for iOS.
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.my_stats), stringResource(R.string.group_ranking))

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.performance_dashboard), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A1C1E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A1C1E),
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = if (isPremium) Color(0xFFFFD700) else MaterialTheme.colorScheme.secondary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.6f))
                                    if (index == 1 && !isPremium) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
        ) {
            if (selectedTab == 0) {
                if (!isPremium) {
                    AndroidPremiumLockView(onPremiumClick)
                } else {
                    AndroidStatsContentView(stats)
                }
            } else {
                if (!isPremium) {
                    AndroidPremiumLockView(onPremiumClick, isRanking = true)
                } else {
                    AndroidGroupRankingView(groupRatings)
                }
            }
        }
    }
}

@Composable
fun AndroidGroupRankingView(ratings: List<MemberRatingSummary>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.player_ranking),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(ratings.size) { index ->
            val member = ratings[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rang
                    Text(
                        text = "${index + 1}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = when(index) {
                            0 -> Color(0xFFFFD700) // Or
                            1 -> Color(0xFFC0C0C0) // Argent
                            2 -> Color(0xFFCD7F32) // Bronze
                            else -> Color.Gray
                        },
                        modifier = Modifier.width(24.dp)
                    )

                    // Photo
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        if (member.photoUrl != null) {
                            AsyncImage(
                                model = member.photoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE5E7EB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (index == 0 && member.averageRating > 0) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape).padding(2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (member.ratingCount > 0) stringResource(R.string.ratings_received, member.ratingCount) else stringResource(R.string.no_ratings),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (member.averageRating > 0) Color(0xFFFFD700) else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (member.averageRating > 0) "%.1f".format(member.averageRating) else "-",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color(0xFF1A1C1E)
                            )
                        }
                        if (member.averageRating >= 4.5f) {
                            Surface(
                                color = Color(0xFF22C55E).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.top_player),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    fontSize = 8.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun AndroidStatsContentView(stats: UserStats) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête avec progression
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.activity_this_month), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Text("${stats.matchesThisMonth} ${stringResource(R.string.matches)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        if (stats.growthPercentage != 0) {
                            Surface(
                                color = if (stats.growthPercentage > 0) Color(0xFF22C55E).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${if (stats.growthPercentage > 0) "+" else ""}${stats.growthPercentage}%",
                                    color = if (stats.growthPercentage > 0) Color(0xFF22C55E) else Color.Red,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Type de Matchs (Duo vs Quatuor)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = "Matchs Duo (1v1)",
                    value = stats.duoMatches.toString(),
                    icon = Icons.Default.Person,
                    color = Color(0xFF3B82F6)
                )
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = "Matchs Quatuor (2v2)",
                    value = stats.quatuorMatches.toString(),
                    icon = Icons.Default.Groups,
                    color = Color(0xFF8B5CF6)
                )
            }
        }

        // Section Effort & Temps (Heures vs Calories)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.play_hours),
                    value = "${stats.estimatedHours}h",
                    icon = Icons.Default.Timer,
                    color = Color(0xFF22C55E)
                )
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = "Calories (est.)",
                    value = "${stats.caloriesBurned} kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFFEF4444)
                )
            }
        }

        // Section Localisation & Niveau
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.my_performance),
                    value = if (stats.averageRating > 0) "%.1f/5".format(stats.averageRating) else "-",
                    icon = Icons.Default.Person,
                    color = Color(0xFFF59E0B)
                )
                AndroidStatSquareCard(
                    modifier = Modifier.weight(1f),
                    label = "Niveau Groupe",
                    value = if (stats.averageReceivedRating > 0) "%.1f/5".format(stats.averageReceivedRating) else "-",
                    icon = Icons.Default.Groups,
                    color = Color(0xFFFFD700)
                )
            }
        }

        item {
            AndroidStatSquareCard(
                modifier = Modifier.fillMaxWidth(),
                label = "Club Favori",
                value = stats.favoriteLocation,
                icon = Icons.Default.Place,
                color = Color(0xFF3B82F6)
            )
        }

        // Section Répartition Horaire
        item {
            Text(stringResource(R.string.repartition_per_period), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AndroidTimePeriodRow(stringResource(R.string.morning), stats.morningMatches, stats.totalMatches, Color(0xFF60A5FA))
                    AndroidTimePeriodRow(stringResource(R.string.afternoon), stats.afternoonMatches, stats.totalMatches, Color(0xFFFBBF24))
                    AndroidTimePeriodRow(stringResource(R.string.evening), stats.eveningMatches, stats.totalMatches, Color(0xFF818CF8))
                }
            }
        }

        // Section Mes partenaires notés
        item {
            Text(stringResource(R.string.partners_rated), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (stats.partnersGiven.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(stringResource(R.string.no_partners_rated), modifier = Modifier.padding(16.dp), color = Color.Gray)
                }
            }
        } else {
            // On ne montre que les 3 derniers pour gagner de la place
            items(stats.partnersGiven.take(3)) { pr ->
                AndroidPartnerRatingItem(pr, isGiven = true)
            }
        }

        // Section Notes reçues
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.partners_ratings_received), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (stats.partnersReceived.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(stringResource(R.string.no_ratings_received), modifier = Modifier.padding(16.dp), color = Color.Gray)
                }
            }
        } else {
            // On ne montre que les 3 derniers pour gagner de la place
            items(stats.partnersReceived.take(3)) { pr ->
                AndroidPartnerRatingItem(pr, isGiven = false)
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun AndroidPartnerRatingItem(pr: PartnerRating, isGiven: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGiven) Icons.Default.PersonOutline else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pr.partnerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (pr.mood.isNotBlank()) {
                    Text(pr.mood, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Text(pr.date, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (i < pr.rating) Color(0xFFFFD700) else Color(0xFFE5E7EB)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pr.rating.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1C1E)
                )
            }
        }
    }
}

@Composable
fun AndroidTimePeriodRow(label: String, count: Int, total: Int, color: Color) {
    val percentage = if (total > 0) count.toFloat() / total else 0f
    val matchesLabel = stringResource(R.string.matches).lowercase()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$count $matchesLabel", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun AndroidStatSquareCard(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                color = color.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun AndroidPremiumLockView(onPremiumClick: () -> Unit, isRanking: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .alpha(0.2f)
                .blur(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(6) { Card(modifier = Modifier.fillMaxWidth().height(80.dp)) {} }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (isRanking) Icons.Default.EmojiEvents else Icons.Default.Analytics, 
                contentDescription = null, 
                tint = Color(0xFFFFD700), 
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                if (isRanking) stringResource(R.string.player_ranking) else stringResource(R.string.pro_dashboard), 
                style = MaterialTheme.typography.headlineSmall, 
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isRanking) stringResource(R.string.player_ranking_desc) else stringResource(R.string.unlock_pro),
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onPremiumClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(stringResource(R.string.unlock_premium), fontWeight = FontWeight.Bold)
            }
        }
    }
}
