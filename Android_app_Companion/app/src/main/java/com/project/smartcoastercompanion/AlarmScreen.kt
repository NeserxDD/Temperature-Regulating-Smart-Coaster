package com.project.smartcoastercompanion

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun AlarmScreen(navController: NavHostController, alarmViewModel: AlarmViewModel) {
    val context = LocalContext.current
    val gradientColorList = listOf(
        Color(0xFFc0bfc7), Color(0xFFd5d4dd), Color(0xFFe7e6ee),
        Color(0xFFf0eff7), Color(0xFFfaf9ff)
    )

    var alarmName by remember { mutableStateOf("") }
    var selectedInterval by remember { mutableIntStateOf(5) }
    var showActiveAlarms by remember { mutableStateOf(false) }
    val alarmIntervals = listOf(5, 10, 15, 30, 60)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = BackGroundColorGradient(true, gradientColorList))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp), // Adjust for bottom navigation
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!showActiveAlarms) {
                AlarmSetupSection(
                    alarmName = alarmName,
                    onAlarmNameChange = { alarmName = it },
                    selectedInterval = selectedInterval,
                    alarmIntervals = alarmIntervals,
                    onIntervalSelect = { selectedInterval = it },
                    onRegularAlarmSet = { hour, minute ->
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                        }
                        alarmViewModel.setRegularAlarm(calendar, alarmName)
                        Toast.makeText(context, "Regular alarm set at $hour:$minute", Toast.LENGTH_SHORT).show()
                    },
                    onIntervalAlarmConfirm = {
                        if (alarmViewModel.activeAlarms.value?.any { it.type == "interval" } == true) {
                            Toast.makeText(context, "Active interval alarm already set", Toast.LENGTH_SHORT).show()
                        } else {
                            alarmViewModel.setIntervalAlarm(selectedInterval * 60000L, alarmName)
                            Toast.makeText(context, "Interval alarm set every $selectedInterval minutes", Toast.LENGTH_SHORT).show()
                        }
                    },
                    context = context
                )
            } else {
                ActiveAlarmsScreen(
                    onBack = { showActiveAlarms = false },
                    alarmViewModel = alarmViewModel
                )
            }
        }

        // Footer Button for "View Active Alarms" - Only visible on AlarmScreen
        if (!showActiveAlarms) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 75.dp) // Extra padding for bottom navigation space
            ) {
                Button(
                    onClick = { showActiveAlarms = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)
                ) {
                    Text("View Active Alarms")
                }
            }
        }
    }
}

@Composable
fun ActiveAlarmsScreen(
    onBack: () -> Unit,
    alarmViewModel: AlarmViewModel
) {
    val activeAlarms by alarmViewModel.activeAlarms.observeAsState(emptyList())
    val currentTime = System.currentTimeMillis()
    var showConfirmationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Active Alarms",
                style = MaterialTheme.typography.h5,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (activeAlarms.isEmpty()) {
                Text("No active alarms.", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(activeAlarms) { alarm ->
                        val intervalDisplay = alarm.intervalMillis?.let {
                            " - Interval: ${TimeUnit.MILLISECONDS.toMinutes(it)} minutes"
                        } ?: ""
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            backgroundColor = Color.LightGray,
                            elevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${alarm.name} - ${alarm.type} - $intervalDisplay",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.subtitle1
                                    )
                                    if (alarm.type == "interval") {
                                        CountdownTimer(
                                            targetTime = alarm.timeInMillis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "At ${formatTime(alarm.timeInMillis)}",
                                            color = Color.DarkGray,
                                            style = MaterialTheme.typography.caption
                                        )
                                    }
                                }
                                Button(
                                    onClick = { alarmViewModel.cancelAlarm(alarm) },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Footer Buttons at the Bottom
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
        ) {
            Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back to Alarm Setup")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (activeAlarms.isEmpty()) {
                        Toast.makeText(context, "No active alarms", Toast.LENGTH_SHORT).show()
                    } else {
                        showConfirmationDialog = true  // Show dialog if there are active alarms
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel All Alarms")
            }
        }
        // Confirmation Dialog
        if (showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmationDialog = false },
                title = { Text("Cancel All Alarms") },
                text = { Text("Are you sure you want to cancel all alarms?") },
                confirmButton = {
                    Button(
                        onClick = {
                            alarmViewModel.cancelAllAlarms()
                            showConfirmationDialog = false  // Dismiss dialog after confirming
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmationDialog = false }) {
                        Text("No")
                    }
                }
            )
        }
    }
}

@Composable
fun AlarmSetupSection(
    alarmName: String,
    onAlarmNameChange: (String) -> Unit,
    selectedInterval: Int,
    alarmIntervals: List<Int>,
    onIntervalSelect: (Int) -> Unit,
    onRegularAlarmSet: (Int, Int) -> Unit,
    onIntervalAlarmConfirm: () -> Unit,
    context: Context
) {
    Column(horizontalAlignment = Alignment.Start) {
        Card(
            backgroundColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Set Regular Alarm", style = MaterialTheme.typography.h6, color = Color.DarkGray)
                OutlinedTextField(
                    value = alarmName,
                    onValueChange = onAlarmNameChange,
                    label = { Text("Alarm Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val calendar = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onRegularAlarmSet(hour, minute) },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false
                    ).show()
                }) {
                    Text("Select Time")
                }
            }
        }

        Card(
            backgroundColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Choose Interval for Repeating Alarm", style = MaterialTheme.typography.h6, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text("Interval: $selectedInterval min")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    alarmIntervals.forEach { interval ->
                        DropdownMenuItem(onClick = {
                            onIntervalSelect(interval)
                            expanded = false
                        }) {
                            Text("$interval min")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = onIntervalAlarmConfirm) {
                    Text("Confirm Interval Alarm")
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun CountdownTimer(
    targetTime: Long,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timeLeft by remember { mutableLongStateOf(0L) }

    // Update current time every second
    LaunchedEffect(targetTime) {
        while (true) {
            currentTime = System.currentTimeMillis()
            timeLeft = maxOf(0L, targetTime - currentTime)
            delay(1000) // Update every second
        }
    }

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60

    Text(
        text = String.format("%02d:%02d", minutes, seconds),
        color = if (timeLeft == 0L) Color.Red else Color.DarkGray,
        style = MaterialTheme.typography.caption,
        modifier = modifier
    )
}


@SuppressLint("DefaultLocale")
fun formatTime(timeInMillis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timeInMillis
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}
