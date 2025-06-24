package com.project.smartcoastercompanion

import android.bluetooth.BluetoothAdapter
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

// BOTTOM NAVIGATION NAV HOST
@Composable
fun BottomNavGraph(
    navController: NavHostController,
    temperatureViewModel: TemperatureViewModel,
    alarmViewModel: AlarmViewModel,
    bluetoothAdapter: BluetoothAdapter?,
    onBluetoothToggle: () -> Unit
) {
    NavHost(navController, startDestination = NavItem.Main.screenRoute) {
        composable(NavItem.Main.screenRoute) {
            MainScreen(
                temperatureViewModel = temperatureViewModel,
                alarmViewModel = alarmViewModel,
                bluetoothAdapter = bluetoothAdapter,
                onBluetoothToggle = onBluetoothToggle,
                navController = navController
            )
        }
        composable(NavItem.Reminders.screenRoute) {
            AlarmScreen(
                navController = navController,
                alarmViewModel = alarmViewModel
            )
        }

    }
}