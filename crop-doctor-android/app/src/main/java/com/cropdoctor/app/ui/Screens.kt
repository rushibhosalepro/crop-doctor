package com.cropdoctor.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.cropdoctor.app.data.AiClient
import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.LeafContext
import com.cropdoctor.app.data.Report
import com.cropdoctor.app.data.ReportStore
import com.cropdoctor.app.data.RulesEngine
import com.cropdoctor.app.util.Net
import com.cropdoctor.app.util.Photo
import kotlinx.coroutines.launch

private data class ChipQ(val key: String, val label: String, val options: List<Pair<String, String>>)

private val QUESTIONS = listOf(
    ChipQ("crop", "Crop", listOf("paddy" to "Paddy", "wheat" to "Wheat", "tomato" to "Tomato", "cotton" to "Cotton", "sugarcane" to "Sugarcane", "soybean" to "Soybean", "maize" to "Maize", "other" to "Other", "unknown" to "Not sure")),
    ChipQ("watered", "Last watered", listOf("today" to "Today", "week" to "This week", "older" to "Longer ago", "not_sure" to "Not sure")),
    ChipQ("fertilizer", "Last fertilizer", listOf("recent" to "Recent", "older" to "Longer ago", "none" to "None", "not_sure" to "Not sure")),
    ChipQ("spray", "Last spray", listOf("today" to "Today", "this_week" to "This week", "older" to "Longer ago", "none_4w" to "None in 4 weeks", "not_sure" to "Not sure")),
    ChipQ("weather", "Weather lately", listOf("rain" to "Rain", "dry" to "Dry", "normal" to "Normal", "not_sure" to "Not sure")),
    ChipQ("stage", "Crop stage", listOf("seedling" to "Seedling", "vegetative" to "Growing", "flowering" to "Flowering", "harvest" to "Near harvest", "not_sure" to "Not sure")),
    ChipQ("spread", "Spreading?", listOf("fast" to "Fast", "slow" to "Slow", "not_sure" to "Not sure"))
)

@Composable
private fun ChipRow(q: ChipQ, selected: String, onPick: (String) -> Unit) {
    Text(q.label, style = MaterialTheme.typography.labelLarge)
    q.options.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onPick(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun CropDoctorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var modelStatus by remember { mutableStateOf("checking…") }
    var photoTaken by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharpScore by remember { mutableStateOf<Double?>(null) }
    var sharpDismissed by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, String>() }
    var note by remember { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Report?>(null) }
    var error by remember { mutableStateOf("") }
    var onDeviceOnly by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Report>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    fun answerFor(key: String, def: String): String = answers.getOrPut(key) { def }

    fun currentContext() = LeafContext(
        crop = answerFor("crop", "unknown"),
        watered = answerFor("watered", "not_sure"),
        fertilizer = answerFor("fertilizer", "not_sure"),
        spray = answerFor("spray", "not_sure"),
        weather = answerFor("weather", "not_sure"),
        stage = answerFor("stage", "not_sure"),
        spread = answerFor("spread", "not_sure")
    )

    LaunchedEffect(Unit) {
        scope.launch {
            ReportStore.signIn()
            modelStatus = AiClient.modelStatusText()
            AiClient.ensureModelDownloaded { modelStatus = it }
        }
    }

    val photoFile = remember { Photo.newFile(context) }
    val photoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val bmp = Photo.decodeSampled(photoFile.absolutePath)
            bitmap = bmp
            photoTaken = bmp != null
            sharpScore = bmp?.let { Photo.sharpnessScore(it) }
            sharpDismissed = false
            report = null
            error = ""
        }
    }

    fun analyze(explicit: Bitmap? = null) {
        val bmp = explicit ?: bitmap ?: return
        analyzing = true
        error = ""
        scope.launch {
            try {
                val ctx = currentContext()
                android.util.Log.d("CropDoctor", "analyze: calling triage")
                val (obs, servedBy) = AiClient.triage(bmp, ctx, note, onDeviceOnly)
                android.util.Log.d("CropDoctor", "analyze: triage done via $servedBy")
                val proposal = RulesEngine.propose(ctx, obs)
                var rep = Report(
                    note = note, context = ctx, observation = obs,
                    proposal = proposal, servedBy = servedBy
                )
                if (Net.isOnline(context) && obs.isUsableCase()) {
                    verifying = true
                    try {
                        android.util.Log.d("CropDoctor", "analyze: calling verify")
                        val (cloud, _) = AiClient.verify(bmp, ctx, note)
                        android.util.Log.d("CropDoctor", "analyze: verify done")
                        rep = rep.copy(verified = true, cloud = cloud)
                    } catch (e: Exception) {
                        android.util.Log.w("CropDoctor", "analyze: verify failed", e)
                        rep = rep.copy(
                            cloud = CloudVerification(
                                reviewNote = "Cloud review failed: ${e.message}. Retry when online."
                            )
                        )
                    } finally {
                        verifying = false
                    }
                }
                val id = try {
                    kotlinx.coroutines.withTimeoutOrNull(15_000L) { ReportStore.save(rep) }.orEmpty()
                } catch (e: Exception) {
                    android.util.Log.w("CropDoctor", "analyze: save failed", e)
                    ""
                }
                android.util.Log.d("CropDoctor", "analyze: done, showing report")
                report = rep.copy(id = id)
            } catch (e: Exception) {
                android.util.Log.w("CropDoctor", "analyze: FAILED", e)
                error = "Analysis failed: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    fun verifyNow(target: Report, bmp: Bitmap?) {
        if (bmp == null) return
        verifying = true
        scope.launch {
            try {
                val (cloud, _) = AiClient.verify(bmp, target.context, target.note)
                val updated = target.copy(verified = true, cloud = cloud)
                if (updated.id == report?.id) report = updated
                runCatching { ReportStore.save(updated) }
                history = ReportStore.list()
            } catch (e: Exception) {
                error = "Verification failed: ${e.message}"
            } finally {
                verifying = false
            }
        }
    }

    fun resetCase() {
        bitmap = null
        photoTaken = false
        sharpScore = null
        sharpDismissed = false
        answers.clear()
        note = ""
        report = null
        error = ""
        feedback = ""
    }

    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        StatusPill(modelStatus)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tab = 0 }, enabled = tab != 0) { Text("New case") }
            OutlinedButton(
                onClick = { tab = 1; selectedId = null; scope.launch { history = ReportStore.list() } },
                enabled = tab != 1
            ) { Text("History") }
        }
        Spacer(Modifier.height(8.dp))

        if (error.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (tab == 1) {
            HistoryView(
                history = history,
                selectedId = selectedId,
                online = Net.isOnline(context),
                verifying = verifying,
                onSelect = { selectedId = it },
                onBack = { selectedId = null },
                onVerify = {
                    error = "Re-verify needs the original photo, which this MVP " +
                        "keeps on-device only. Start a new case to verify again."
                },
                onConfirm = { rep, choice ->
                    scope.launch {
                        ReportStore.confirmPriority(rep.id, choice, choice != rep.proposal.priority)
                        history = ReportStore.list()
                    }
                }
            )
            return@Column
        }

        val rep = report
        if (rep != null) {
            CaseDetail(
                report = rep,
                online = Net.isOnline(context),
                verifying = verifying,
                feedback = feedback,
                onFeedbackChange = { feedback = it },
                onVerify = { verifyNow(rep, bitmap) },
                onConfirm = { choice ->
                    val updated = rep.copy(
                        workerPriority = choice,
                        workerOverrode = choice != rep.proposal.priority
                    )
                    report = updated
                    scope.launch {
                        ReportStore.confirmPriority(rep.id, choice, updated.workerOverrode)
                    }
                },
                onDelivered = {
                    report = rep.copy(delivered = true)
                    scope.launch { ReportStore.markDelivered(rep.id) }
                },
                onFeedbackSave = {
                    scope.launch { ReportStore.saveFeedback(rep.id, feedback) }
                },
                photo = bitmap
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { resetCase() }, modifier = Modifier.fillMaxWidth()) {
                Text("Start new case")
            }
            SafetyFooter()
            return@Column
        }

        // Capture branch.
        Text("Crop Doctor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Snap a leaf. Get a case file, with or without signal.")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onDeviceOnly,
                onClick = { onDeviceOnly = false },
                label = { Text("Auto (hybrid)") }
            )
            FilterChip(
                selected = onDeviceOnly,
                onClick = { onDeviceOnly = true },
                label = { Text("On-device only") }
            )
        }
        if (onDeviceOnly) {
            Text(
                "On-device only proves offline mode on Nano phones.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { camera.launch(photoUri) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text("Take leaf photo", fontSize = 18.sp) }
        Spacer(Modifier.height(8.dp))
        bitmap?.let { Image(it.asImageBitmap(), "leaf photo", Modifier.fillMaxWidth().height(220.dp)) }
        Spacer(Modifier.height(8.dp))
        Text("OR TRY A SAMPLE", style = MaterialTheme.typography.labelLarge)
        val sampleThumbs = remember {
            mapOf(
                "Spotted" to Photo.sampleBitmap(context, "sample_spotted"),
                "Healthy" to Photo.sampleBitmap(context, "sample_healthy"),
                "Not plant" to Photo.sampleBitmap(context, "sample_notplant")
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            sampleThumbs.forEach { (label, bmp) ->
                Card(
                    onClick = {
                        if (bmp != null) {
                            bitmap = bmp
                            sharpScore = Photo.sharpnessScore(bmp)
                            sharpDismissed = true
                            report = null
                            error = ""
                            analyze(bmp)
                        } else {
                            error = "Sample image missing."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(6.dp)) {
                        bmp?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxWidth().height(64.dp)) }
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
        }
        val score = sharpScore
        if (score != null && score < Photo.SHARPNESS_THRESHOLD && !sharpDismissed) {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Photo looks blurry. Retake for a better result.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { camera.launch(photoUri) }) { Text("Retake") }
                        OutlinedButton(onClick = { sharpDismissed = true }) { Text("Use anyway") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        QUESTIONS.forEach { q ->
            ChipRow(q, answerFor(q.key, if (q.key == "crop") "unknown" else "not_sure")) { answers[q.key] = it }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = note, onValueChange = { note = it },
            label = { Text("Note (optional): what do you see?") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val blocked = analyzing || bitmap == null ||
            (score != null && score < Photo.SHARPNESS_THRESHOLD && !sharpDismissed)
        Button(onClick = { analyze() }, enabled = !blocked, modifier = Modifier.fillMaxWidth()) {
            Text(if (analyzing) "Analyzing…" else "Analyze (30 seconds)")
        }
        SafetyFooter()
    }
}
@Composable
fun CaseDetail(
    report: Report,
    online: Boolean,
    verifying: Boolean,
    feedback: String,
    onFeedbackChange: (String) -> Unit,
    onVerify: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelivered: () -> Unit,
    onFeedbackSave: () -> Unit,
    showFollowup: Boolean = true,
    photo: Bitmap? = null
) {
    VerdictHeadline(report)
    Spacer(Modifier.height(8.dp))
    photo?.let {
        Image(it.asImageBitmap(), "case photo", Modifier.fillMaxWidth().height(200.dp))
        Spacer(Modifier.height(8.dp))
    }
    val pri = report.workerPriority.ifEmpty { report.proposal.priority }
    if (pri == "prioritize_review") {
        val urgent = report.proposal.flags.any {
            it == "LARGE_EXTENT" || it == "FAST_SPREAD" || it == "RECENT_SPRAY"
        }
        EscalationCard(
            reason = report.proposal.reasons.firstOrNull() ?: "See reasons below.",
            urgent = urgent
        )
        Spacer(Modifier.height(8.dp))
    }
    ProposalCard(report.proposal, report.workerPriority, onConfirm)
    Spacer(Modifier.height(8.dp))
    ObservationCard(report.observation, report.servedBy)
    Spacer(Modifier.height(8.dp))
    CloudCard(report, online, verifying, onVerify)
    Spacer(Modifier.height(8.dp))
    if (showFollowup) {
        if (!report.delivered) {
            Button(onClick = onDelivered, modifier = Modifier.fillMaxWidth()) {
                Text("Mark answer delivered to farmer")
            }
        } else {
            Text("Delivered to farmer.")
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = feedback, onValueChange = onFeedbackChange,
            label = { Text("Was this right? (feedback)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onFeedbackSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save feedback")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun CropDoctorDesignPreview() {
    MaterialTheme {
        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: 30-second understanding banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🌿 Crop Doctor AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF1B5E20)
                        )
                        Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = Color(0xFFC8E6C9)
                            )
                        ) {
                            Text(
                                "● Offline Ready",
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Text(
                        "30-Second Field Leaf Diagnosis",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF388E3C)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Step 1: Big 1-Tap Photo Capture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFF1F8E9)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "1. Take Leaf Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Capture a clear photo of the spots or damaged leaf area.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("📷 Open Camera / Snap Leaf", fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Step 2: Quick Field Details (3 Chip rows)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "2. Quick Field Context",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Crop Type", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Paddy") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Wheat") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Tomato") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Other") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Weather Lately", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Rain") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Dry") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Normal") })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Step 3: Big CTA
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("✨ Analyze Leaf (Instant AI)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // Sample Output Card (what user sees after 30s)
            Text("Sample AI Result", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "PROPOSED: STANDARD FOLLOW-UP",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("• Yellowing observed on leaf tip and margins.")
                    Text("• Weather pattern indicates possible fungal onset.")
                    Text(
                        "Served by: On-device Gemini Nano",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryView(
    history: List<Report>,
    selectedId: String?,
    online: Boolean,
    verifying: Boolean,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    onVerify: (Report) -> Unit,
    onConfirm: (Report, String) -> Unit
) {
    val selected = history.find { it.id == selectedId }
    if (selected == null) {
        if (history.isEmpty()) {
            Text("No cases yet. New cases appear here after analysis.")
        }
        history.forEach { rep ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { onSelect(rep.id) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${rep.observation.subject} / ${rep.observation.extent}")
                    Text("Proposed: ${priorityLabel(rep.proposal.priority)}")
                    if (rep.delivered) Text("Delivered to farmer")
                    if (!rep.verified) Text("Verification pending")
                }
            }
        }
    } else {
        OutlinedButton(onClick = onBack) { Text("Back to list") }
        Spacer(Modifier.height(8.dp))
        CaseDetail(
            report = selected, online = online, verifying = verifying,
            feedback = "", onFeedbackChange = {},
            onVerify = { onVerify(selected) },
            onConfirm = { onConfirm(selected, it) },
            onDelivered = {}, onFeedbackSave = {}, showFollowup = false
        )
    }
}
