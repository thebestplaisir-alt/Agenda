package com.inchios.agenda.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import agenda.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAvailabilityDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime, location: String, comment: String, isDuo: Boolean, addMe: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf(LocalTime(18, 0)) }
    var endTime by remember { mutableStateOf(LocalTime(20, 0)) }
    var participantType by remember { mutableStateOf(0) }
    
    // Simplification for multiplatform: we use standard Dialogs for selection 
    // because Android-only DatePicker/TimePicker aren't available in commonMain yet without extra libs
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        // Simplified Date Selection for Shared Module
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Sélectionner la date") },
            text = {
                // In a real KMP app, you'd use a multiplatform DatePicker library 
                // or 'expect/actual' to show the native iOS/Android picker.
                Text("Date sélectionnée : ${selectedDate}")
            },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.ok)) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_session), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { 
                    Text(stringResource(Res.string.who_plays), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = listOf(
                            stringResource(Res.string.just_me), 
                            stringResource(Res.string.me_plus_friend), 
                            stringResource(Res.string.friend_alone), 
                            stringResource(Res.string.two_friends)
                        )
                        options.forEachIndexed { index, text ->
                            FilterChip(
                                selected = participantType == index,
                                onClick = { participantType = index },
                                label = { Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                if (participantType != 0) {
                    item { 
                        OutlinedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = { Text(if(participantType == 1) stringResource(Res.string.friend_name) else stringResource(Res.string.guest_name)) }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true
                        ) 
                    }
                }
                
                item { 
                    OutlinedTextField(
                        value = location, 
                        onValueChange = { location = it }, 
                        label = { Text(stringResource(Res.string.club)) }, 
                        modifier = Modifier.fillMaxWidth(), 
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                    ) 
                }
                
                item { 
                    OutlinedTextField(
                        value = comment, 
                        onValueChange = { comment = it }, 
                        label = { Text(stringResource(Res.string.comment)) }, 
                        modifier = Modifier.fillMaxWidth()
                    ) 
                }
                
                item { 
                    OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { 
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${selectedDate.dayOfMonth}/${selectedDate.monthNumber}/${selectedDate.year}") 
                        } 
                    } 
                }
                
                item { 
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedCard(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) { 
                            Column(modifier = Modifier.padding(12.dp)) { 
                                Text(stringResource(Res.string.start), style = MaterialTheme.typography.labelSmall)
                                Text("${startTime.hour}:${startTime.minute.toString().padStart(2, '0')}", fontWeight = FontWeight.Bold) 
                            } 
                        }
                        OutlinedCard(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) { 
                            Column(modifier = Modifier.padding(12.dp)) { 
                                Text(stringResource(Res.string.end), style = MaterialTheme.typography.labelSmall)
                                Text("${endTime.hour}:${endTime.minute.toString().padStart(2, '0')}", fontWeight = FontWeight.Bold) 
                            } 
                        } 
                    } 
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { 
                    when(participantType) {
                        0 -> onConfirm("", selectedDate, startTime, endTime, location, comment, false, false)
                        1 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, false, true)
                        2 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, false, false)
                        3 -> onConfirm(name, selectedDate, startTime, endTime, location, comment, true, false)
                    }
                }
            ) { 
                Text(stringResource(Res.string.confirm)) 
            } 
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
