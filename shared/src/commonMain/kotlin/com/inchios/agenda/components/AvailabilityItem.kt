package com.inchios.agenda.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inchios.agenda.*
import agenda.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    onCompleteMatch: () -> Unit, 
    onProfileClick: () -> Unit
) {
    val isAlreadyConfirmed = item.completedBy.contains(currentUserId)
    val isValidatedByOthers = item.completedBy.isNotEmpty() && !isAlreadyConfirmed
    
    val participantNames = remember(item.participantIds, item.userId, allMembers) {
        val ids = if (item.participantIds.isEmpty()) listOf(item.userId) else item.participantIds
        ids.map { id ->
            if (id == currentUserId) "Moi" 
            else allMembers.find { it.userId == id }?.displayName ?: "Joueur"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onProfileClick() }, 
        shape = RoundedCornerShape(12.dp), 
        colors = CardDefaults.cardColors(containerColor = if(item.isCompleted) Color(0xFFF0FDF4) else Color.White), 
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Image placeholder (multiplatform image loading needed for full iOS)
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE5E7EB)), 
                    contentAlignment = Alignment.Center
                ) { 
                    Text(item.personName.take(1).uppercase(), fontWeight = FontWeight.Bold) 
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.personName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (item.isUserPremium) { 
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp)) 
                        }
                        if (item.isCompleted) { 
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp)) 
                        }
                    }
                    
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Le ${item.dateString}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            if (item.isDuo) { 
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (item.participantIds.size >= 2) Color(0xFF22C55E).copy(alpha = 0.1f) else Color(0xFFE0F2FE), 
                                    shape = RoundedCornerShape(4.dp)
                                ) { 
                                    Text(
                                        if (item.participantIds.size >= 2) "DUO CONFIRMÉ" else "DUO",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                                        fontSize = 9.sp, 
                                        color = if (item.participantIds.size >= 2) Color(0xFF166534) else Color(0xFF0369A1), 
                                        fontWeight = FontWeight.Bold
                                    ) 
                                } 
                            }
                        }
                        
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
                                text = stringResource(Res.string.match_already_validated),
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
                
                Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(6.dp)) { 
                    Text(
                        text = "${item.startTimeString} - ${item.endTimeString}", 
                        modifier = Modifier.padding(6.dp), 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold
                    ) 
                }
                
                if (canComplete) {
                    IconButton(onClick = onCompleteMatch) {
                        Icon(Icons.Default.RateReview, contentDescription = stringResource(Res.string.validate_match), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.LightGray) }
                else if (canProposeDuo) IconButton(onClick = onProposeDuo) { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            }
            
            if (item.location.isNotBlank()) { 
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(item.location, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) 
                } 
            }
            
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
