plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = System.getenv("NEXUS_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("NEXUS_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("NEXUS_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("NEXUS_RELEASE_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storeType = "JKS"
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    namespace = "ai.wasti.nexus"
    compileSdk = 36
    defaultConfig {
        applicationId = "ai.wasti.nexus"
        minSdk = 33
        targetSdk = 36
        versionCode = 21
        versionName = "0.5.16"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.arm:ai-chat:0.1.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


// Build a private, read-only source index into the app so every connected brain can
// retrieve the exact architecture, screen, control and implementation snippets it needs.
val nexusRepositoryRoot = rootProject.projectDir.parentFile
val nexusKnowledgePatterns = listOf(
    "README.md",
    "android/app/src/main/java/**/*.kt",
    "android/app/src/main/AndroidManifest.xml",
    "android/app/src/main/res/**/*.xml",
    "android/app/src/test/**/*.kt",
    "android/app/build.gradle.kts",
    "android/settings.gradle.kts",
    "cloudflare/README.md",
    "cloudflare/src/**/*.js",
    "cloudflare/scripts/**/*.mjs",
    "cloudflare/wrangler.toml",
    "cloudflare/providers.example.json",
    "cloudflare/tests/**/*.mjs",
    "orchestrator/README.md",
    "orchestrator/app/**/*.py",
    "orchestrator/tests/**/*.py",
    "orchestrator/requirements*.txt",
    "docs/**/*.md",
    "models/registry.json",
    "models/extended-model-registry.json",
    "models/**/*.py",
    "models/requirements.txt",
    "sdks/**/*.ts",
    "sdks/**/*.py",
    "termux/README.md",
    "termux/nexus",
    "termux/env.example",
    "tests/**/*.py",
    ".github/workflows/**/*.yml"
)
val nexusKnowledgeFiles = fileTree(nexusRepositoryRoot) {
    include(*nexusKnowledgePatterns.toTypedArray())
    exclude(
        "**/build/**", "**/.gradle/**", "**/.env*", "**/local.properties",
        "**/*secret*", "**/*.jks", "**/*.keystore", "**/*.pem", "**/*.key"
    )
}
val nexusGeneratedAssets = layout.buildDirectory.dir("generated/nexusKnowledge/assets")
val generateNexusAppKnowledge by tasks.registering {
    inputs.files(nexusKnowledgeFiles)
    val outputFile = nexusGeneratedAssets.map { it.file("nexus-app-source-index.json") }
    outputs.file(outputFile)
    doLast {
        val chunks = mutableListOf<Map<String, Any>>()
        nexusKnowledgeFiles.files.sortedBy { it.relativeTo(nexusRepositoryRoot).invariantSeparatorsPath }.forEach { source ->
            val relativePath = source.relativeTo(nexusRepositoryRoot).invariantSeparatorsPath
            val lines = source.readLines(Charsets.UTF_8)
            lines.chunked(64).forEachIndexed { index, section ->
                chunks += mapOf("path" to relativePath, "part" to (index + 1), "text" to section.joinToString("\n"))
            }
        }
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(groovy.json.JsonOutput.toJson(chunks), Charsets.UTF_8)
    }
}
android.sourceSets.getByName("main").assets.srcDir(nexusGeneratedAssets.get().asFile)
tasks.named("preBuild").configure { dependsOn(generateNexusAppKnowledge) }

// 0.5.6: chat streaming is frame-batched and new-chat reset is asynchronous.
