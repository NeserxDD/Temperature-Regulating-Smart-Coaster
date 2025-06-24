@file:Suppress("DEPRECATION")

package com.project.smartcoastercompanion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.ScaffoldDefaults.contentWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.rememberNavController
import com.project.smartcoastercompanion.ui.theme.SmartCoasterCompanionTheme
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Modifier
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val temperatureViewModel: TemperatureViewModel by viewModels()
    private val REQUEST_ENABLE_BT = 1  // Bluetooth enable request code
    private val alarmViewModel: AlarmViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize permission launcher
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                permissions[Manifest.permission.BLUETOOTH_SCAN] == true
            ) {
                checkAndConnectToDevice("Smart_Coaster")
            } else {
                Toast.makeText(this, "Bluetooth permissions denied.", Toast.LENGTH_SHORT).show()
            }
        }

        // Register Bluetooth state change receiver
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        if (checkBluetoothPermissions()) {
            if (isBluetoothEnabled()) {
                checkAndConnectToDevice("Smart_Coaster")
            } else {
                promptUserToEnableBluetooth()
            }
        } else {
            requestBluetoothPermissions()
        }

        setContent {
            val navController = rememberNavController()
            SmartCoasterCompanionTheme {

                Scaffold(

                    bottomBar = {

                        BottomNavigationBar(navController = navController, viewModel = temperatureViewModel)
                    }
                ) {
                    BottomNavGraph(

                        navController = navController,
                        temperatureViewModel = temperatureViewModel,
                        alarmViewModel = alarmViewModel,
                        bluetoothAdapter = bluetoothAdapter,
                        onBluetoothToggle = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                        }
                    )
                }
            }
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    private fun promptUserToEnableBluetooth() {
        if (!isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkAndConnectToDevice("Smart_Coaster")
            } else {
                Toast.makeText(this, "Bluetooth is required to connect to the device.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // Bluetooth state change receiver
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF) {
                Toast.makeText(context, "Bluetooth is off. Please enable it.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Check if device is already paired or needs discovery
    @SuppressLint("MissingPermission")
    private fun checkAndConnectToDevice(deviceName: String) {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        val isAlreadyPaired = pairedDevices?.any { it.name == deviceName } == true

        if (isAlreadyPaired) {
            connectToDevice(deviceName)  // Connect directly if paired
        } else {
            discoverAndPairDevice(deviceName)  // Discover and pair if not already paired
        }
    }

    // Discover and pair with the Arduino device
    @SuppressLint("MissingPermission")
    private fun discoverAndPairDevice(deviceName: String) {
        val discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_FOUND) {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.name == deviceName) {
                        Toast.makeText(context, "Found $deviceName. Pairing...", Toast.LENGTH_SHORT).show()
                        pairDevice(device)
                        bluetoothAdapter?.cancelDiscovery()  // Stop discovery after finding the device
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryReceiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    // Initiate pairing with the found device
    @SuppressLint("MissingPermission")
    private fun pairDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("createBond")
            method.invoke(device)
            Toast.makeText(this, "Pairing initiated with ${device.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Pairing failed with ${device.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // Connect to the paired Arduino device
    @SuppressLint("MissingPermission")
    private fun connectToDevice(deviceName: String) {
        val pairedDevices = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            if (device.name == deviceName) {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()

                    outputStream = bluetoothSocket?.outputStream
                    inputStream = bluetoothSocket?.inputStream
                    temperatureViewModel.setBluetoothOutputStream(outputStream!!)
                    listenToArduinoFeedback()

                    Toast.makeText(this, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error connecting to $deviceName", Toast.LENGTH_SHORT).show()
                }
            }

        }

    }

    // Listen for feedback from the Arduino device
    //changes
    private fun listenToArduinoFeedback() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ByteArray(1024)
                while (bluetoothSocket?.isConnected == true) {
                    val bytesRead = inputStream?.read(buffer) ?: 0
                    val receivedMessage = String(buffer, 0, bytesRead).trim() //trim whitespace
                    Log.d("Mainscreen", "recieve message: ${receivedMessage}")
                    // Parse the temperature from the received message
                    val newTemperature = receivedMessage.toFloatOrNull() ?: 0f

                    // Update the temperature in the ViewModel (UI thread)
                    withContext(Dispatchers.Main) {
                        temperatureViewModel.updateTemperature(newTemperature)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun checkBluetoothPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocket?.close()
        unregisterReceiver(bluetoothStateReceiver)
    }
}


//SEALED CLASS FOR ROUTES/NAVIGATION ITEMS
sealed class NavItem(val title: String, val icon: Int, val screenRoute: String) {
    data object Main : NavItem("Main", R.drawable.ic_temperature, "main")
    data object Reminders : NavItem("Reminders", R.drawable.ic_clock, "reminders")

}