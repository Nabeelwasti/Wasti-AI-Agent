package ai.wasti.nexus

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val alias = "wasti.nexus.gateway.key"
    private val prefs by lazy { getSharedPreferences("nexus-config", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme {
            NexusScreen(
                initialUrl = prefs.getString("url", "") ?: "",
                initialKey = decryptSecret(),
                onSave = { url, key -> prefs.edit().putString("url", url).apply(); encryptSecret(key) },
                onSend = { url, key, prompt, hybrid -> send(url, key, prompt, hybrid) }
            )
        } }
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun encryptSecret(value: String) {
        if (value.isBlank()) { prefs.edit().remove("key_iv").remove("key_ciphertext").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        prefs.edit().putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("key_ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)).apply()
    }

    private fun decryptSecret(): String = try {
        val iv = Base64.decode(prefs.getString("key_iv", null), Base64.NO_WRAP)
        val ciphertext = Base64.decode(prefs.getString("key_ciphertext", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext))
    } catch (_: Exception) { "" }

    private suspend fun send(url: String, key: String, prompt: String, hybrid: Boolean): String = withContext(Dispatchers.IO) {
        val root = url.trim().trimEnd('/')
        require(root.startsWith("https://")) { "Gateway URL must use HTTPS." }
        require(key.length >= 32) { "API key must be at least 32 characters." }
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject().put("model", if (hybrid) "nexus-hybrid" else "qwen")
            .put("messages", messages).put("stream", false)
        if (hybrid) payload.put("nexus", JSONObject().put("mode", "hybrid").put("review_rounds", 1))
        val request = Request.Builder().url(root + "/v1/chat/completions")
            .header("Authorization", "Bearer " + key)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Gateway HTTP " + response.code + ": " + text)
            JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: throw IllegalStateException("Gateway response did not contain an answer.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexusScreen(
    initialUrl: String,
    initialKey: String,
    onSave: (String, String) -> Unit,
    onSend: suspend (String, String, String, Boolean) -> String
) {
    var url by remember { mutableStateOf(initialUrl) }
    var key by remember { mutableStateOf(initialKey) }
    var prompt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("Connect a NEXUS gateway or install an offline model to begin.") }
    var busy by remember { mutableStateOf(false) }
    var hybrid by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("WASTI NEXUS") }) }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("One intelligence. Many brains.", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Gateway HTTPS URL") }, singleLine = true)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Gateway API key") }, singleLine = true)
            Button(onClick = { onSave(url, key); answer = "Connection settings saved. API key encrypted with Android Keystore." }, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = hybrid, onClick = { hybrid = true }, label = { Text("Hybrid — all brains") })
                FilterChip(selected = !hybrid, onClick = { hybrid = false }, label = { Text("Single brain") })
            }
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Your prompt") })
            Button(enabled = !busy && prompt.isNotBlank(), onClick = {
                onSave(url, key); busy = true
                scope.launch {
                    answer = try { onSend(url, key, prompt, hybrid) }
                    catch (e: Exception) { "Request failed: " + e.message }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else if (hybrid) "✦ HYBRID — ALL BRAINS" else "Send") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Answer", style = MaterialTheme.typography.titleMedium)
            Text(answer)
        }
    }
}
