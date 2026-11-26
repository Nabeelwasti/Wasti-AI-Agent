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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusHome() }
    }
}

@Composable
private fun NexusHome() {
    var apiBase by rememberSaveable { mutableStateOf("http://10.0.2.2:8080/v1") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("WASTI NEXUS", style = MaterialTheme.typography.headlineLarge)
            Text("One app. Many AI brains. One orchestration layer.",
                style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Orchestrator connection", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = apiBase,
                        onValueChange = { apiBase = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API base URL") },
                        singleLine = true
                    )
                    Text("Connection testing and secure credential storage are not yet implemented.")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hybrid intelligence", style = MaterialTheme.typography.titleMedium)
                    Text("The orchestration API foundation is being built. This screen does not yet dispatch prompts or claim model participation.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}, enabled = false) { Text("Hybrid") }
                        Button(onClick = {}, enabled = false) { Text("Local models") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Foundation build • v0.1.0", style = MaterialTheme.typography.labelMedium)
        }
    }
}
