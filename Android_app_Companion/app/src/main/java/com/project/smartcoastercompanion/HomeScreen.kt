package com.project.smartcoastercompanion

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Icon
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay


// BluetoothStatusIcon Composable
@Composable
fun BluetoothStatusIcon(
    bluetoothAdapter: BluetoothAdapter?,
    onBluetoothToggle: () -> Unit
) {
    val isBluetoothEnabled = remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }
    val context = LocalContext.current

    IconButton(onClick = {
        if (isBluetoothEnabled.value) {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)

        } else {
            onBluetoothToggle()
        }
    }) {
        Icon(
            painter = painterResource(
                if (isBluetoothEnabled.value) R.drawable.ic_bluetooth_on else R.drawable.ic_bluetooth_off
            ),
            contentDescription = "Bluetooth Status",
            tint = if (isBluetoothEnabled.value) Color.Blue else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}

// MainScreen Composable
@Composable
fun MainScreen(
    temperatureViewModel: TemperatureViewModel,
    alarmViewModel: AlarmViewModel,
    bluetoothAdapter: BluetoothAdapter?,
    onBluetoothToggle: () -> Unit,
    navController: NavController
) {
    //log
    Log.d("MainScreen", "Current temp: ${temperatureViewModel.currentTemperature}")
    val gradientColorList = listOf(
        Color(0xFFABA9BD), Color(0xFFd5d4dd), Color(0xFFe7e6ee),
        Color(0xFFf0eff7), Color(0xFFfaf9ff)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = BackGroundColorGradient(
                    isVerticalGradient = true,
                    colors = gradientColorList
                )
            )
    ) {
        // Bluetooth Icon in the top-right corner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            BluetoothStatusIcon(
                bluetoothAdapter = bluetoothAdapter,
                onBluetoothToggle = onBluetoothToggle
            )
        }

        // Main content below
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Display temperature status
            CurrentTemperatureDisplay(
                temperature = temperatureViewModel.currentTemperature,
                isHeating = temperatureViewModel.currentTemperature <
                        temperatureViewModel.preferredTemperature - temperatureViewModel.threshold
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Temperature Controls
            TemperatureControl(
                preferredTemperature = temperatureViewModel.preferredTemperature,
                threshold = temperatureViewModel.threshold,
                onPreferredChange = { temperatureViewModel.updatePreferredTemperature(it) },
                onThresholdChange = { temperatureViewModel.updateThreshold(it) }
            )
        }
    }
}


// UPDATED: TemperatureControl uses stepper buttons for preferred temperature adjustment
@Composable
fun TemperatureControl(
    preferredTemperature: Float,
    threshold: Float,
    onPreferredChange: (Float) -> Unit,
    onThresholdChange: (Float) -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        // Preferred Temperature Stepper with long-press support
        Text(
            text = "Preferred Temperature: ${preferredTemperature.toInt()}°C",
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            // Decrease button with long-press support
            StepperButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Decrease Temperature",
                enabled = preferredTemperature > 30,
                onStep = { onPreferredChange(preferredTemperature - 1) }
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Display current preferred temperature
            Text(
                text = "${preferredTemperature.toInt()}°C",
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Increase button with long-press support
            StepperButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Increase Temperature",
                enabled = preferredTemperature < 60,
                onStep = { onPreferredChange(preferredTemperature + 1) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Threshold Slider
        Text(
            text = "Threshold ± : ${threshold.toInt()}°C",
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Slider(
            value = threshold,
            onValueChange = { onThresholdChange(it) },
            valueRange = 0f..15f,  // Threshold limited between 0 and 15°C
            steps = 14,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// NEW: StepperButton handles both single press and long-press interactions
@Composable
fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()


    //PRESSED DELAY!! CHANGE!!
    LaunchedEffect(isPressed) {
        if (isPressed) {
            while (isPressed) {
                onStep()
                delay(100)  // Adjust speed: 100ms for quick adjustments
            }
        }
    }

    IconButton(
        onClick = {},
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}


@Composable
fun CurrentTemperatureDisplay(temperature: Float, isHeating: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 128.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_thermostat),
                contentDescription = "Temperature Icon",
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )

            Text(
                text = "${temperature}°C",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = if (isHeating) Color.Red else Color.Blue,
                        offset = Offset(0.1f, 0.1f),
                        blurRadius = 14f
                    )
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = if (isHeating) "Heating On" else "Heating Off",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (isHeating) Color.Red else Color.Blue,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}


@Composable
fun BackGroundColorGradient(isVerticalGradient: Boolean, colors: List<Color>): Brush {
    val endOffset = if (isVerticalGradient) {
        Offset(0f, Float.POSITIVE_INFINITY)
    } else {
        Offset(Float.POSITIVE_INFINITY, 0f)
    }

    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = endOffset
    )
}
