package ai.wasti.nexus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.app.PendingIntent
import android.provider.OpenableColumns
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexusApkInstallerActivity : ComponentActivity() {
    private var permissionState: androidx.compose.runtime.MutableState<Boolean>? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var canInstall by remember { mutableStateOf(packageManager.canRequestPackageInstalls()) }
            permissionState = remember { mutableStateOf(canInstall) }
            var status by remember {
                mutableStateOf(getSharedPreferences("nexus-install-status", MODE_PRIVATE).getString("message", "APK installation always requires Android's own confirmation screen. NEXUS cannot silently install apps.") ?: "")
            }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    busy = true
                    status = "Copying the selected APK into Android's installer…"
                    try {
                        val result = withContext(Dispatchers.IO) { stageAndCommit(uri) }
                        status = result
                    } catch (error: Exception) {
                        status = "APK installation could not start: ${error.message ?: "unknown error"}"
                    } finally {
                        busy = false
                    }
                }
            }
            canInstall = permissionState?.value ?: canInstall
            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Install APK") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Choose an APK you trust. NEXUS streams it into Android PackageInstaller with a 512 MB limit. Android verifies the package and signature and shows its own install/upgrade confirmation. No silent install or downgrade bypass is attempted.")
                        if (!canInstall) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Allow installs from WASTI NEXUS", style = MaterialTheme.typography.titleMedium)
                                    Text("Android requires the special Install unknown apps setting for this source. You can turn it off again after installation.")
                                    Button(onClick = {
                                        runCatching {
                                            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                                        }.onFailure { status = "Could not open Android's install-source setting." }
                                    }) { Text("Open Android install permission") }
                                }
                            }
                        }
                        Button(
                            enabled = !busy && canInstall,
                            onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Preparing APK…" else "Choose APK file") }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            getSharedPreferences("nexus-install-status", MODE_PRIVATE).edit().remove("message").apply()
                            status = "No recent installer status."
                        }) { Text("Clear status") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionState?.value = packageManager.canRequestPackageInstalls()
    }

    private fun stageAndCommit(uri: Uri): String {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        var session: PackageInstaller.Session? = null
        try {
            session = installer.openSession(sessionId)
            contentResolver.openInputStream(uri)?.use { input ->
                session.openWrite("base.apk", 0, -1).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 512L * 1024 * 1024) { "APK exceeds the 512 MB safety limit." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "The selected APK is empty." }
                    session.fsync(output)
                }
            } ?: error("Android could not open the selected APK.")
            val callback = Intent(this, NexusInstallStatusReceiver::class.java).setPackage(packageName)
            val pending = PendingIntent.getBroadcast(
                this, sessionId, callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
            session.close()
            getSharedPreferences("nexus-install-status", MODE_PRIVATE).edit()
                .putString("message", "APK submitted to Android PackageInstaller. Follow the system confirmation screen to finish.")
                .apply()
            return "APK submitted to Android PackageInstaller. Follow the system confirmation screen to finish."
        } catch (error: Exception) {
            runCatching { session?.abandon() }
            runCatching { installer.abandonSession(sessionId) }
            throw error
        } finally {
            runCatching { session?.close() }
        }
    }
}

class NexusInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Android is waiting for your confirmation to install or update this APK."
            PackageInstaller.STATUS_SUCCESS -> "Android reports that the APK installation completed successfully."
            else -> "Android installer status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}"
        }
        context.getSharedPreferences("nexus-install-status", Context.MODE_PRIVATE).edit()
            .putString("message", message).apply()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirmation != null) runCatching { context.startActivity(confirmation) }
        }
    }
}
