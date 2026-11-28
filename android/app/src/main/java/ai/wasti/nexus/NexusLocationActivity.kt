package ai.wasti.nexus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class NexusLocationActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var granted by remember { mutableStateOf(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
            var location by remember { mutableStateOf<Location?>(null) }
            var status by remember { mutableStateOf("Location is not accessed until you press the button.") }
            var loading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
                granted = allowed
                status = if (allowed) "Permission granted. Press Get current location to request a one-time fix." else "Location permission denied. Nothing was accessed."
            }
            fun requestLocation() {
                if (!granted) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    return
                }
                scope.launch {
                    loading = true
                    location = null
                    status = "Requesting one foreground location fix…"
                    try {
                        val result = withContext(Dispatchers.IO) { getOneShotLocation() }
                        location = result
                        status = if (result == null) "No location fix was returned within 25 seconds. Check Location services and try again." else "One-time location received. NEXUS does not track you in the background."
                    } catch (error: SecurityException) {
                        granted = false
                        status = "Android revoked location access. Grant approximate location again if you want to retry."
                    } catch (error: Exception) {
                        status = "Location unavailable: ${error.message ?: "unknown error"}"
                    } finally {
                        loading = false
                    }
                }
            }
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("My location") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("NEXUS requests approximate location only while this screen is open. It does not request background location or keep a location history. The online agent may request a separate one-time fix; coordinates are shared only after you approve that tool call.")
                        Button(onClick = { requestLocation() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (loading) "Getting location…" else if (granted) "Get current location" else "Allow approximate location")
                        }
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        location?.let { fix ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Current location", style = MaterialTheme.typography.titleMedium)
                                    Text("Latitude: ${fix.latitude}")
                                    Text("Longitude: ${fix.longitude}")
                                    Text("Accuracy: ${fix.accuracy.toInt()} metres")
                                    Text("Fix time: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(fix.time))}")
                                    OutlinedButton(onClick = {
                                        val uri = Uri.parse("geo:${fix.latitude},${fix.longitude}?q=${fix.latitude},${fix.longitude}")
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }) { Text("Open in Maps") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getOneShotLocation(): Location? {
        val manager = getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val cancellation = CancellationSignal()
        return try {
            withTimeoutOrNull(25_000) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    manager.getCurrentLocation(provider, cancellation, mainExecutor) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
            }
        } finally {
            cancellation.cancel()
        }
    }
}
