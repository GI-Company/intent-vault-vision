package space.globalintent.vision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Offline-first camera assistant. EmbeddingGemma handles retrieval, never chat generation.
 * Web browsing is networked and only launched after a deliberate user command.
 */
class MainActivity : ComponentActivity() {
    private lateinit var camera: VisionCamera
    private lateinit var semantic: LocalSemanticEngine

    private var tab by mutableStateOf("Camera")
    private var status by mutableStateOf("Initializing local systems...")
    private var matches by mutableStateOf("Live visual matches appear here when the model is ready.")
    private var autoSmile by mutableStateOf(false)
    private var sceneLabels by mutableStateOf(
        "a smiling person\na group photo\na printed document\na wiring connector\na computer screen"
    )
    private var assistantInput by mutableStateOf("")
    private var assistantReply by mutableStateOf(
        "INTENT Assistant is ready for local commands. Say 'remember ...', 'find ...', " +
        "'search the web for ...', 'open camera', 'smile mode on', or 'take a photo'."
    )
    private var noteInput by mutableStateOf("")
    private val notes = mutableStateListOf<String>()
    private var webQuery by mutableStateOf("")
    private var webUrl by mutableStateOf("")
    private var captureCount by mutableIntStateOf(0)

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) camera.bind()
        else updateStatus("Camera permission denied; Vault and Web remain available.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readNotes()
        captureCount = captureDir().listFiles()?.count { it.extension == "jpg" } ?: 0
        semantic = LocalSemanticEngine(this, ::updateStatus)
        camera = VisionCamera(this, ::updateStatus, { frame ->
            semantic.scoreImage(frame, sceneLabels.lines()) { result ->
                runOnUiThread { matches = result }
            }
        }, { _ -> runOnUiThread { captureCount++ } })

        setContent { IntentVaultVisionApp() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) camera.bind()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun updateStatus(value: String) {
        runOnUiThread { if (!isFinishing && !isDestroyed) status = value }
    }

    @Composable
    private fun IntentVaultVisionApp() {
        val teal = Color(0xFF066B73)
        val colors = lightColorScheme(primary = teal,
            background = Color(0xFFF4F8FB), surface = Color.White,
            onBackground = Color(0xFF172C3C))
        MaterialTheme(colorScheme = colors) {
            Scaffold(
                containerColor = colors.background,
                bottomBar = {
                    NavigationBar {
                        listOf("Camera", "Assistant", "Vault", "Web").forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(when (item) {
                                    "Camera" -> "◉"; "Assistant" -> "✦"; "Vault" -> "▤"; else -> "⌕"
                                }) },
                                label = { Text(item) }
                            )
                        }
                    }
                }
            ) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("INTENT / VAULT VISION",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("Private intelligence. Intelligent camera.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text("LOCAL AI", color = teal,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    when (tab) {
                        "Camera" -> CameraScreen()
                        "Assistant" -> AssistantScreen()
                        "Vault" -> VaultScreen()
                        "Web" -> WebScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun CameraScreen() {
        val context = LocalContext.current
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("LIVE CAMERA", style = MaterialTheme.typography.titleMedium)
            Box(
                Modifier.fillMaxWidth().height(300.dp)
                    .background(Color(0xFF111E27)),
                contentAlignment = Alignment.TopStart
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            contentDescription = "Live device camera, analyzed on-device"
                            camera.attach(this)
                        }
                    }
                )
                Text("●  CAMERA LIVE  ·  DEVICE ONLY",
                    modifier = Modifier.padding(12.dp)
                        .background(Color(0xDD153340)).padding(8.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { camera.capture() },
                    modifier = Modifier.weight(1f)) { Text("Capture photo") }
                OutlinedButton(onClick = { camera.flip() },
                    modifier = Modifier.weight(1f)) { Text("Switch camera") }
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto Smile shutter", fontWeight = FontWeight.SemiBold)
                        Text("Snap when all detected faces smile for 3 samples.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoSmile, onCheckedChange = {
                        autoSmile = it
                        camera.setSmileMode(it)
                    })
                }
            }
            Text("Photos saved privately: $captureCount",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = sceneLabels, onValueChange = { sceneLabels = it },
                label = { Text("Visual concepts — one per line") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
            )
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Live semantic matches", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(matches, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Face smiles use bundled ML Kit. EmbeddingGemma similarity is not a smile probability.",
                style = MaterialTheme.typography.labelSmall)
        }
    }

    @Composable
    private fun AssistantScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("INTENT ASSISTANT", style = MaterialTheme.typography.titleMedium)
            Text("Offline camera controls and memory retrieval, with explicit web search.",
                style = MaterialTheme.typography.bodySmall)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(assistantReply, Modifier.padding(16.dp))
            }
            OutlinedTextField(
                value = assistantInput, onValueChange = { assistantInput = it },
                label = { Text("Ask or command") }, minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val prompt = assistantInput.trim()
                assistantInput = ""
                runCommand(prompt)
            }, enabled = assistantInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Send to assistant") }
            Text("Try: remember my blue toolbox is in the garage",
                style = MaterialTheme.typography.bodySmall)
            Text("Try: find toolbox",
                style = MaterialTheme.typography.bodySmall)
            Text("Try: search the web for Android camera tips",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("This command assistant is not yet a generative chatbot. It uses EmbeddingGemma " +
                "for semantic retrieval; conversational writing requires an additional local LLM.",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun VaultScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PRIVATE VAULT", style = MaterialTheme.typography.titleMedium)
            Text("Your notes stay in app-private storage.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = noteInput, onValueChange = { noteInput = it },
                label = { Text("New note") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { saveNote(noteInput); noteInput = "" },
                enabled = noteInput.isNotBlank()) { Text("Save note") }
            HorizontalDivider()
            Text("${notes.size} notes  ·  $captureCount private images",
                style = MaterialTheme.typography.labelMedium)
            if (notes.isEmpty()) Text("Save a note or use 'remember ...' in Assistant.")
            notes.asReversed().forEach { note ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(note, Modifier.padding(12.dp))
                }
            }
        }
    }

    @Composable
    private fun WebScreen() {
        Column(Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WEB SEARCH", style = MaterialTheme.typography.titleMedium)
            Text("Online mode: search terms are sent to Google Search, not to the on-device model.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = webQuery, onValueChange = { webQuery = it },
                label = { Text("Search the web") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { openWebSearch(webQuery) },
                enabled = webQuery.isNotBlank()) { Text("Search online") }
            if (webUrl.isBlank()) {
                Text("No web request until you press Search or issue a web-search command.")
            } else {
                val context = LocalContext.current
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ): Boolean = request.url.scheme != "https"
                            }
                            loadUrl(webUrl)
                        }
                    },
                    update = { if (it.url != webUrl) it.loadUrl(webUrl) }
                )
            }
        }
    }

    private fun openWebSearch(query: String) {
        if (query.isBlank()) return
        webQuery = query.trim()
        webUrl = "https://www.google.com/search?q=" +
            URLEncoder.encode(webQuery, "UTF-8")
        tab = "Web"
    }

    private fun runCommand(prompt: String) {
        if (prompt.isBlank()) return
        val lower = prompt.lowercase()
        when {
            lower.startsWith("remember ") -> {
                val note = prompt.substringAfter(' ').trim()
                saveNote(note)
                assistantReply = "Saved privately to your Vault: $note"
            }
            lower.startsWith("find ") -> {
                assistantReply = "Searching locally..."
                semantic.findNotes(prompt.substringAfter(' ').trim(), notes.toList()) {
                    runOnUiThread { assistantReply = it }
                }
            }
            lower.startsWith("search the web for ") -> {
                openWebSearch(prompt.substring(19).trim())
                assistantReply = "Opening external web search."
            }
            lower.startsWith("search web ") -> {
                openWebSearch(prompt.substringAfter("search web ").trim())
                assistantReply = "Opening external web search."
            }
            "smile" in lower && ("on" in lower || "enable" in lower) -> {
                tab = "Camera"; autoSmile = true; camera.setSmileMode(true)
                assistantReply = "Smile shutter enabled. All detected faces must smile."
            }
            "smile" in lower && ("off" in lower || "disable" in lower) -> {
                autoSmile = false; camera.setSmileMode(false)
                assistantReply = "Smile shutter disabled."
            }
            "photo" in lower || "shutter" in lower -> {
                tab = "Camera"; camera.capture()
                assistantReply = "Requested camera shutter. Check Camera for status."
            }
            "camera" in lower -> {
                tab = "Camera"; assistantReply = "Opened live camera."
            }
            "vault" in lower || "notes" in lower -> {
                tab = "Vault"; assistantReply = "Opened your private Vault."
            }
            else -> assistantReply =
                "I support: remember ..., find ..., search the web for ..., open camera, " +
                "take a photo, smile mode on/off, and open vault. " +
                "Open-ended chat will need a separate bundled generative language model."
        }
    }

    private fun captureDir() = java.io.File(filesDir, "captures")

    private fun readNotes() {
        val serialized = getSharedPreferences("vault", MODE_PRIVATE).getString("notes", "[]")
        try {
            val data = JSONArray(serialized)
            for (i in 0 until data.length()) notes.add(data.getString(i))
        } catch (_: Exception) { notes.clear() }
    }

    private fun saveNote(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        notes.add(value)
        getSharedPreferences("vault", MODE_PRIVATE).edit()
            .putString("notes", JSONArray(notes.toList()).toString()).apply()
    }

    override fun onDestroy() {
        camera.close()
        semantic.close()
        super.onDestroy()
    }
}
