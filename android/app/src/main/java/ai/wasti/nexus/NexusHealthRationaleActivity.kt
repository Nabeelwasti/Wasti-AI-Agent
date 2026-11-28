package ai.wasti.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class NexusHealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("WASTI NEXUS · Health data privacy", style = MaterialTheme.typography.headlineSmall)
                        Text("NEXUS requests read-only access to Steps and Heart Rate records through Android Health Connect. It reads a seven-day summary only after you choose Connect & read.")
                        Text("NEXUS does not write health records, request background health access, build a health history in its own storage, or send health records to online AI providers.")
                        Text("The optional step-counter sensor uses ACTIVITY_RECOGNITION only while the Health & sensors screen is open. It reports the device counter since reboot and stops when the screen closes.")
                        Text("You can revoke Health Connect access at any time in Android Health Connect settings.")
                        Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
    }
}
