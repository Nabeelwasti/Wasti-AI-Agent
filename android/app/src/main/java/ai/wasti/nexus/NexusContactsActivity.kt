package ai.wasti.nexus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NexusContact(val name: String, val phone: String)

class NexusContactsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var hasPermission by remember { mutableStateOf(checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
            var permissionDenied by remember { mutableStateOf(false) }
            var contacts by remember { mutableStateOf(emptyList<NexusContact>()) }
            var query by remember { mutableStateOf("") }
            var loadError by remember { mutableStateOf("") }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasPermission = granted
                permissionDenied = !granted
            }
            LaunchedEffect(Unit) {
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            LaunchedEffect(hasPermission) {
                if (hasPermission) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val found = ArrayList<NexusContact>()
                            contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                    ContactsContract.CommonDataKinds.Phone.NUMBER
                                ),
                                null, null,
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                            )?.use { cursor ->
                                val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                                val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                while (cursor.moveToNext() && found.size < 1000) {
                                    val name = cursor.getString(nameColumn).orEmpty().trim()
                                    val number = cursor.getString(numberColumn).orEmpty().trim()
                                    if (name.isNotBlank() && number.isNotBlank()) found += NexusContact(name, number)
                                }
                            } ?: error("Android did not return the contacts list.")
                            found.distinctBy { it.name.lowercase() + "|" + it.phone }
                        }
                    }
                    result.onSuccess { contacts = it }.onFailure { loadError = it.message ?: "Could not read contacts." }
                }
            }
            val filtered = contacts.filter {
                query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true)
            }
            MaterialTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Contacts & calling") })
                }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Contacts are read only when you open this screen and grant access. NEXUS does not upload or save your address book.", style = MaterialTheme.typography.bodySmall)
                        if (!hasPermission) {
                            Text(if (permissionDenied) "Contacts permission was denied. You can enable it in Android App permissions." else "Contacts permission is needed to search your address book.")
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts access") }
                        } else {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Search name or number") },
                                singleLine = true
                            )
                            if (loadError.isNotBlank()) Text(loadError, color = MaterialTheme.colorScheme.error)
                            Text("${filtered.size} contact entries", style = MaterialTheme.typography.labelMedium)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered, key = { it.name + "|" + it.phone }) { contact ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(contact.name, style = MaterialTheme.typography.titleMedium)
                                            Text(contact.phone, style = MaterialTheme.typography.bodyMedium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(onClick = {
                                                    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.phone, null)))
                                                }) { Text("Open dialer") }
                                                OutlinedButton(onClick = {
                                                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(contact.phone))))
                                                }) { Text("Compose SMS") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Android-protected records", style = MaterialTheme.typography.titleSmall)
                                Text("SMS inbox/history and call logs are not read by NEXUS. Android restricts these permissions to qualifying default SMS/dialer roles or installer-allowlisted apps. NEXUS does not take over your default phone or messaging app.")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)) }
                                    }) { Text("Open SMS app") }
                                    OutlinedButton(onClick = {
                                        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_PHONE)) }
                                    }) { Text("Open Phone app") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
