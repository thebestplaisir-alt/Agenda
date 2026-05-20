package com.inchios.agenda.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inchios.agenda.*
import kotlinx.datetime.*
import agenda.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MonthCalendarView(
    selectedDate: LocalDate,
    currentMonth: LocalDate, // Using first day of month to represent currentMonth
    allAvailabilities: List<Availability>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit
) {
    val daysInMonth = getDaysInMonth(currentMonth.monthNumber, currentMonth.year)
    val firstDayOfMonth = getFirstDayOfWeek(currentMonth.year, currentMonth.monthNumber)
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(minusMonth(currentMonth)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                }
                Text(
                    text = "${currentMonth.month.name} ${currentMonth.year}",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onMonthChange(plusMonth(currentMonth)) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { day ->
                    Text(
                        day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val rows = (daysInMonth + firstDayOfMonth + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - firstDayOfMonth + 1
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..daysInMonth) {
                                val date = LocalDate(currentMonth.year, currentMonth.monthNumber, dayNumber)
                                val isSelected = date == selectedDate
                                val dayAvailabilities = allAvailabilities.filter { it.dateString == date.toString() }
                                
                                val status = when {
                                    hasMatch(dayAvailabilities) >= 1 -> "MATCH"
                                    dayAvailabilities.isNotEmpty() -> "PARTIAL"
                                    else -> "NONE"
                                }
                                DayCell(date, isSelected, status) { onDateSelected(date) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, isSelected: Boolean, status: String, onClick: () -> Unit) {
    val indicatorColor = when(status) {
        "MATCH" -> Color(0xFF22C55E)
        "PARTIAL" -> Color(0xFFF57C00)
        else -> Color(0xFFEF4444)
    }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else indicatorColor.copy(alpha = 0.12f)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .then(if (!isSelected) Modifier.border(1.dp, indicatorColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onClick() }
            .padding(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (isSelected) Color.White else Color.Black,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else indicatorColor)
        )
    }
}

// Helper functions for dates in commonMain
fun getDaysInMonth(month: Int, year: Int): Int {
    return when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

fun getFirstDayOfWeek(year: Int, month: Int): Int {
    val date = LocalDate(year, month, 1)
    return (date.dayOfWeek.isoDayNumber - 1) % 7
}

fun plusMonth(date: LocalDate): LocalDate {
    return if (date.monthNumber == 12) LocalDate(date.year + 1, 1, 1)
    else LocalDate(date.year, date.monthNumber + 1, 1)
}

fun minusMonth(date: LocalDate): LocalDate {
    return if (date.monthNumber == 1) LocalDate(date.year - 1, 12, 1)
    else LocalDate(date.year, date.monthNumber - 1, 1)
}

fun hasMatch(availabilities: List<Availability>): Int {
    if (availabilities.isEmpty()) return 0
    // Simplified match detection for commonMain (logic from getMaxConcurrentPlayers)
    val processedAvails = availabilities.map { 
        if (it.participantIds.isEmpty()) it.copy(participantIds = listOf(it.userId)) else it
    }
    
    // Check for confirmed Duos
    if (processedAvails.any { it.isDuo && it.participantIds.size >= 2 }) return 1
    
    // Check for 4+ concurrent players (Simplified)
    // In a real scenario, we'd port the full overlap logic.
    val uniqueParticipants = processedAvails.flatMap { it.participantIds }.distinct()
    return if (uniqueParticipants.size >= 4) 1 else 0
}
