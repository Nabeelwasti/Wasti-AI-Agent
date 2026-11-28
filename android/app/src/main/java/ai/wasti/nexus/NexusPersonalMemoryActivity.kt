package ai.wasti.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.withContext

class NexusPersonalMemoryActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var notes by remember { mutableStateOf(emptyList<NexusPersonalMemoryNote>()) }
            var draft by remember { mutableStateOf("") }
            var status by remember { mutableStateOf("Only notes you explicitly save are stored.") }
            var loading by remember { mutableStateOf(false) }
            var pendingDelete by remember { mutableStateOf<NexusPersonalMemoryNote?>(null) }
            var confirmClear by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            suspend fun refresh() {
                notes = withContext(Dispatchers.IO) { NexusPersonalMemoryStore.list(applicationContext) }
            }
            LaunchedEffect(Unit) {
                runCatching { refresh() }.onFailure { status = "Could not open encrypted memory: ${it.message}" }
            }

            MaterialTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("Personal memory") }) }) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("NEXUS does not train or change model weights. These are explicit memory notes encrypted with Android Keystore and stored on this phone. They are not synced automatically.")
                        Text("Relevant notes are added to on-device prompts. Online models receive them only if you enable the separate personal-memory sharing option in Chat settings.")
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 2000) draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            label = { Text("Add a memory note") },
                            placeholder = { Text("Example: My preferred writing language is simple Urdu and English.") }
                        )
                        Button(
                            enabled = !loading && draft.isNotBlank(),
                            onClick = {
                                val value = draft.trim()
                                scope.launch {
                                    loading = true
                                    runCatching {
                                        withContext(Dispatchers.IO) { NexusPersonalMemoryStore.add(applicationContext, value) }
                                        draft = ""
                                        refresh()
                                        status = "Memory note saved securely on this phone."
                                        runCatching { NexusActionAuditStore.append(applicationContext, "Personal memory", "COMPLETED", "User explicitly saved a memory note; note content was not audited.") }
                                    }.onFailure { status = "Could not save memory note: ${it.message}" }
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (loading) "Saving…" else "Save memory note") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Saved notes · ${notes.size}/100", style = MaterialTheme.typography.titleMedium)
                            TextButton(enabled = notes.isNotEmpty(), onClick = { confirmClear = true }) { Text("Clear all") }
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (notes.isEmpty()) {
                            Text("No personal memory notes saved.")
                            Spacer(Modifier.weight(1f))
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(notes, key = { it.id }) { note ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAt)), style = MaterialTheme.typography.labelSmall)
                                                TextButton(onClick = { pendingDelete = note }) { Text("Delete") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            pendingDelete?.let { note ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text("Delete memory note?") },
                    text = { Text("This note will be removed from NEXUS local memory.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { NexusPersonalMemoryStore.delete(applicationContext, note.id) }
                                    refresh()
                                    status = "Memory note deleted."
                                    runCatching { NexusActionAuditStore.append(applicationContext, "Personal memory", "COMPLETED", "User deleted a memory note; note content was not audited.") }
                                }.onFailure { status = "Could not delete memory note: ${it.message}" }
                                pendingDelete = null
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
                )
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear all personal memory?") },
                    text = { Text("All saved memory notes will be deleted from this phone. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { NexusPersonalMemoryStore.clear(applicationContext) }
                                    notes = emptyList()
                                    status = "All personal memory notes cleared."
                                    runCatching { NexusActionAuditStore.append(applicationContext, "Personal memory", "COMPLETED", "User cleared all memory notes.") }
                                }.onFailure { status = "Could not clear memory: ${it.message}" }
                                confirmClear = false
                            }
                        }) { Text("Clear all") }
                    },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
                )
            }
        }
    }
}
