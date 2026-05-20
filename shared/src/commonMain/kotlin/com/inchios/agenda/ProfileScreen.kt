package com.inchios.agenda

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import agenda.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile,
    groupMembers: List<UserProfile>,
    groups: Map<String, Group>,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onLogout: () -> Unit,
    onJoinGroup: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onSwitchGroup: (String) -> Unit,
    onUpdateGroupName: (String, String) -> Unit,
    onPremiumClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onDeleteAccount: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onRequestDeletionClick: () -> Unit,
    onShareInviteCode: (String) -> Unit
) {
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
    val genders = listOf(stringResource(Res.string.male), stringResource(Res.string.female), stringResource(Res.string.other))

    if (showJoinDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinDialog = false }, 
            onConfirm = { onJoinGroup(it); showJoinDialog = false }
        )
    }

    if (editingGroupId != null) {
        AlertDialog(
            onDismissRequest = { editingGroupId = null }, 
            title = { Text(stringResource(Res.string.name_group)) }, 
            text = { 
                OutlinedTextField(
                    value = newGroupName, 
                    onValueChange = { newGroupName = it }, 
                    label = { Text(stringResource(Res.string.new_name)) }, 
                    singleLine = true
                ) 
            }, 
            confirmButton = { 
                Button(onClick = { onUpdateGroupName(editingGroupId!!, newGroupName); editingGroupId = null; newGroupName = "" }) { 
                    Text(stringResource(Res.string.confirm)) 
                } 
            }, 
            dismissButton = { 
                TextButton(onClick = { editingGroupId = null }) { 
                    Text(stringResource(Res.string.cancel)) 
                } 
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false }, 
            title = { Text(stringResource(Res.string.delete_account_confirm)) }, 
            text = { Text(stringResource(Res.string.delete_account_desc)) }, 
            confirmButton = { 
                Button(onClick = { showDeleteConfirm = false; onDeleteAccount() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                    Text(stringResource(Res.string.confirm_deletion)) 
                } 
            }, 
            dismissButton = { 
                TextButton(onClick = { showDeleteConfirm = false }) { 
                    Text(stringResource(Res.string.cancel)) 
                } 
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Text(stringResource(Res.string.my_profile), fontWeight = FontWeight.Bold)
                if (profile.premium) { 
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp)) 
                } 
            } 
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { 
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { 
                        Box { 
                            // In shared module, we might need a generic placeholder or wait for a multiplatform image loader
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray.copy(alpha = 0.2f))
                                    .border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(profile.displayName.take(1).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.secondary)
                            }
                            
                            IconButton(
                                onClick = onPhotoClick, 
                                modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.secondary, CircleShape).size(32.dp)
                            ) { 
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) 
                            } 
                        } 
                    } 
                }
                
                if (!profile.premium) {
                    item { 
                        Card(
                            onClick = onPremiumClick, 
                            modifier = Modifier.fillMaxWidth(), 
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.15f)), 
                            border = BorderStroke(1.dp, Color(0xFFFFD700))
                        ) { 
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFB8860B))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column { 
                                    Text(stringResource(Res.string.premium), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(stringResource(Res.string.unlock_all_advantages), fontSize = 11.sp) 
                                } 
                            } 
                        } 
                    }
                }
                
                item { 
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))) { 
                        Column(modifier = Modifier.padding(12.dp)) { 
                            Text(stringResource(Res.string.invite_code), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) { 
                                Text(profile.inviteCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                IconButton(onClick = { onShareInviteCode(profile.inviteCode) }) { 
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) 
                                } 
                            } 
                        } 
                    } 
                }
                
                item {
                    Text(stringResource(Res.string.my_groups), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    profile.groupIds.distinct().forEach { groupId ->
                        val group = groups[groupId]
                        val isOwner = profile.inviteCode == groupId
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(if(profile.currentGroupId == groupId) Color(0xFFF3F4F6) else Color.Transparent, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = profile.currentGroupId == groupId, onClick = { onSwitchGroup(groupId) })
                                Column(modifier = Modifier.weight(1f)) { 
                                    Text(group?.name ?: groupId, fontWeight = if(profile.currentGroupId == groupId) FontWeight.Bold else FontWeight.Normal)
                                    if (group != null) Text(groupId, style = MaterialTheme.typography.labelSmall, color = Color.Gray) 
                                }
                                if (isOwner) {
                                    IconButton(onClick = { editingGroupId = groupId; newGroupName = group?.name ?: "" }) { 
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp)) 
                                    }
                                }
                                if (profile.groupIds.size > 1) {
                                    IconButton(onClick = { onLeaveGroup(groupId) }) { 
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) 
                                    }
                                }
                            }
                            if (profile.currentGroupId == groupId) { 
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(Res.string.participants, groupMembers.size), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                groupMembers.forEach { member -> 
                                    Row(modifier = Modifier.padding(start = 32.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(member.displayName, fontSize = 12.sp)
                                        if (member.userId == profile.userId) {
                                            Text(stringResource(Res.string.me), fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp)) 
                                        }
                                    } 
                                } 
                            }
                        }
                    }
                    OutlinedButton(onClick = { showJoinDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { 
                        Icon(Icons.Default.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.join_group)) 
                    }
                }
                
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(Res.string.display_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text(stringResource(Res.string.age)) }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(stringResource(Res.string.phone)) }, modifier = Modifier.fillMaxWidth()) }
                item { 
                    Text(stringResource(Res.string.level), style = MaterialTheme.typography.labelMedium)
                    // Simplified level selector for shared module (avoids ExposedDropdownMenu for now)
                    Column {
                        levelsNoP.forEach { level ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        selectedLevel = level
                                        if (level == "Compétitif" && customPLevel.isBlank()) customPLevel = "P1000"
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedLevel == level, onClick = null)
                                Text(level, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    
                    if (selectedLevel == "Compétitif") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPLevel,
                            onValueChange = { customPLevel = it.uppercase().take(6) },
                            label = { Text("Classement P (ex: P1500)") },
                            placeholder = { Text("P1000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                item { 
                    Text(stringResource(Res.string.gender), style = MaterialTheme.typography.labelMedium)
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                        genders.forEach { gender -> 
                            FilterChip(
                                selected = selectedGender == gender, 
                                onClick = { selectedGender = gender }, 
                                label = { Text(gender) }
                            ) 
                        } 
                    } 
                }
                
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onLogout, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) { 
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.logout)) 
                        }
                        
                        TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) { 
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.delete_account), fontSize = 12.sp) 
                        }
                        
                        TextButton(
                            onClick = onRequestDeletionClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                            modifier = Modifier.fillMaxWidth()
                        ) { 
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.request_account_deletion), fontSize = 11.sp, textDecoration = TextDecoration.Underline) 
                        }

                        Text(
                            text = stringResource(Res.string.privacy_policy),
                            modifier = Modifier.fillMaxWidth().clickable { onPrivacyPolicyClick() }.padding(vertical = 8.dp),
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
            Button(onClick = { onSave(profile.copy(displayName = displayName, age = age, phone = phone, level = finalLevel, gender = selectedGender)); onDismiss() }) { 
                Text(stringResource(Res.string.save)) 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text(stringResource(Res.string.cancel)) 
            } 
        }
    )
}

@Composable
fun JoinGroupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text(stringResource(Res.string.join_group)) }, 
        text = { 
            OutlinedTextField(
                value = code, 
                onValueChange = { code = it.uppercase() }, 
                label = { Text(stringResource(Res.string.group_code)) }, 
                placeholder = { Text("Ex: AB1234") }, 
                singleLine = true, 
                modifier = Modifier.fillMaxWidth()
            ) 
        }, 
        confirmButton = { 
            Button(onClick = { onConfirm(code) }) { 
                Text(stringResource(Res.string.join)) 
            } 
        }, 
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text(stringResource(Res.string.cancel)) 
            } 
        }
    )
}
