package ai.wasti.nexus

import android.content.Context
import org.json.JSONObject

internal data class NexusCapabilityModel(
    val id: String,
    val name: String,
    val creator: String,
    val placement: String,
    val capabilities: List<String>,
    val license: String,
    val licenseStatus: String,
    val status: String,
    val commercialUse: String,
    val phoneStatus: String,
    val hardwareClass: String,
    val notes: String,
    val sourceUrl: String
)

internal object NexusModelRegistry {
    fun loadNotices(context: Context): String =
        context.assets.open("nexus-third-party-model-notices.md").bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun load(context: Context): List<NexusCapabilityModel> {
        val raw = context.assets.open("nexus-model-registry.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        require(root.optInt("schema_version") == 2) { "Unsupported extended model registry schema." }
        val entries = root.optJSONArray("entries") ?: error("Extended model registry has no entries.")
        require(root.optInt("entry_count") == entries.length()) { "Extended model registry count does not match its entries." }
        val result = ArrayList<NexusCapabilityModel>(entries.length())
        val seen = HashSet<String>()
        for (index in 0 until entries.length()) {
            val item = entries.getJSONObject(index)
            val id = item.getString("id")
            require(id.matches(Regex("^[a-z0-9][a-z0-9._-]{1,63}$")) && seen.add(id)) { "Invalid or duplicate extended model ID: $id" }
            val capabilities = item.optJSONArray("capabilities") ?: org.json.JSONArray()
            result += NexusCapabilityModel(
                id = id, name = item.optString("name", id), creator = item.optString("creator"),
                placement = item.optString("placement"),
                capabilities = (0 until capabilities.length()).map { capabilities.getString(it) },
                license = item.optString("license"), licenseStatus = item.optString("license_status"),
                status = item.optString("status"), commercialUse = item.optString("commercial_use"),
                phoneStatus = item.optString("phone_status"), hardwareClass = item.optString("hardware_class"),
                notes = item.optString("notes"), sourceUrl = item.optString("source_url")
            )
        }
        return result
    }
}
