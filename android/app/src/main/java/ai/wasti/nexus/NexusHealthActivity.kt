package ai.wasti.nexus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class NexusHealthActivity : ComponentActivity(), SensorEventListener {
    private var stepCounterSensor: Sensor? = null
    private var sensorManager: SensorManager? = null
    private var stepCounterState: androidx.compose.runtime.MutableState<Long?>? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var sdkStatus by remember { mutableStateOf(HealthConnectClient.getSdkStatus(this@NexusHealthActivity)) }
            var healthAccess by remember { mutableStateOf(false) }
            var summary by remember { mutableStateOf("Health data is not read until you grant the requested Health Connect permissions.") }
            var loading by remember { mutableStateOf(false) }
            val sensorStepsState = remember { mutableStateOf<Long?>(null) }
            stepCounterState = sensorStepsState
            val scope = rememberCoroutineScope()
            val requiredHealthPermissions = remember {
                setOf(
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getReadPermission(HeartRateRecord::class)
                )
            }
            val activityPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startStepCounter() else stepCounterState?.value = null
            }
            val healthPermissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract()
            ) { granted ->
                healthAccess = granted.containsAll(requiredHealthPermissions)
                if (!healthAccess) summary = "Health Connect permission was not granted. No health data was read."
            }
            LaunchedEffect(Unit) {
                sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                sdkStatus = HealthConnectClient.getSdkStatus(this@NexusHealthActivity)
            }
            LaunchedEffect(healthAccess) {
                if (healthAccess) {
                    loading = true
                    summary = runCatching { readHealthSummary(requiredHealthPermissions) }
                        .getOrElse { "Health Connect could not return data: ${it.message}" }
                    loading = false
                }
            }
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Health & sensors") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Health information is sensitive. NEXUS reads it locally only after you choose the specific permissions. It does not run background health tracking. A seven-day summary is shared with an online model only if you separately enable AI tools and approve the Health summary tool call.")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Health Connect · last 7 days", style = MaterialTheme.typography.titleMedium)
                                Text(when (sdkStatus) {
                                    HealthConnectClient.SDK_AVAILABLE -> "Health Connect is available."
                                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Health Connect needs to be installed or updated."
                                    else -> "Health Connect is not available on this device."
                                })
                                Button(
                                    enabled = !loading && sdkStatus == HealthConnectClient.SDK_AVAILABLE,
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val client = HealthConnectClient.getOrCreate(this@NexusHealthActivity)
                                                val alreadyGranted = client.permissionController.getGrantedPermissions()
                                                if (alreadyGranted.containsAll(requiredHealthPermissions)) {
                                                    healthAccess = true
                                                } else healthPermissionLauncher.launch(requiredHealthPermissions)
                                            } catch (error: Exception) {
                                                summary = "Could not open Health Connect: ${error.message}"
                                            }
                                        }
                                    }
                                ) { Text(if (loading) "Reading…" else "Connect & read steps / heart rate") }
                                if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ||
                                    sdkStatus == HealthConnectClient.SDK_UNAVAILABLE
                                ) {
                                    OutlinedButton(onClick = {
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                                        }
                                    }) { Text("Find Health Connect") }
                                }
                                Text(summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("On-device step counter", style = MaterialTheme.typography.titleMedium)
                                Text("Reads the device step-counter sensor only while this screen is open. The value is cumulative since the last device reboot, not today's steps.")
                                Button(onClick = {
                                    if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                                        startStepCounter()
                                    } else activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }) { Text("Enable live step sensor") }
                                Text(sensorStepsState.value?.let { "Steps since reboot: $it" } ?: "Step sensor is not active.")
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun readHealthSummary(requiredPermissions: Set<String>): String {
        val client = HealthConnectClient.getOrCreate(this)
        val granted = client.permissionController.getGrantedPermissions()
        require(granted.containsAll(requiredPermissions)) { "Health Connect permissions were revoked. Reconnect to grant access." }
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(7))
        val timeRange = TimeRangeFilter.between(start, end)
        val steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), timeRange))[StepsRecord.COUNT_TOTAL] ?: 0L
        val heartRecords = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange,
                pageSize = 100
            )
        ).records
        val readings = heartRecords.flatMap { it.samples }.map { it.beatsPerMinute }
        val heartText = if (readings.isEmpty()) "No heart-rate samples were available in the selected period."
        else "Average recorded heart rate: ${readings.average().toInt()} bpm across ${readings.size} samples."
        return "Steps recorded in the last 7 days: $steps\n$heartText\nData remains on this phone."
    }

    private fun startStepCounter() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        val manager = sensorManager ?: (getSystemService(SENSOR_SERVICE) as SensorManager).also { sensorManager = it }
        val sensor = stepCounterSensor ?: manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).also { stepCounterSensor = it }
        if (sensor != null) manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        else stepCounterState?.value = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val value = event?.values?.firstOrNull()?.toLong() ?: return
        stepCounterState?.value = value
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onStop() {
        sensorManager?.unregisterListener(this)
        stepCounterState?.value = null
        super.onStop()
    }
}
