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
import androidx.compose.ui.Alignment
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
import com.cropdoctor.app.util.AppLang
import com.cropdoctor.app.util.LangStrings
import com.cropdoctor.app.util.Net
import com.cropdoctor.app.util.Photo
import com.cropdoctor.app.util.loadLang
import com.cropdoctor.app.util.saveLang
import com.cropdoctor.app.util.stringsFor
import kotlinx.coroutines.launch

private data class ChipQ(val key: String, val label: String, val options: List<Pair<String, String>>)

private fun questionsFor(s: LangStrings) = listOf(
    ChipQ("crop", s.qCrop, s.cropOpts),
    ChipQ("watered", s.qWatered, s.wateredOpts),
    ChipQ("fertilizer", s.qFertilizer, s.fertilizerOpts),
    ChipQ("spray", s.qSpray, s.sprayOpts),
    ChipQ("weather", s.qWeather, s.weatherOpts),
    ChipQ("stage", s.qStage, s.stageOpts),
    ChipQ("spread", s.qSpread, s.spreadOpts)
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
private fun LangRow(s: LangStrings, lang: AppLang, onPick: (AppLang) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(s.langLabel, style = MaterialTheme.typography.labelLarge)
        AppLang.entries.forEach { l ->
            FilterChip(
                selected = lang == l,
                onClick = { onPick(l) },
                label = { Text(l.label) }
            )
        }
    }
}

@Composable
fun CropDoctorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(loadLang(context)) }
    val s = stringsFor(lang)
    var tab by remember { mutableStateOf(0) }
    var modelStatus by remember { mutableStateOf(s.checking) }
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
                val (obs, servedBy) = AiClient.triage(bmp, ctx, note, onDeviceOnly, lang)
                android.util.Log.d("CropDoctor", "analyze: triage done via $servedBy")
                val proposal = RulesEngine.propose(ctx, obs, lang)
                var rep = Report(
                    note = note, context = ctx, observation = obs,
                    proposal = proposal, servedBy = servedBy
                )
                if (Net.isOnline(context) && obs.isUsableCase()) {
                    verifying = true
                    try {
                        android.util.Log.d("CropDoctor", "analyze: calling verify")
                        val (cloud, _) = AiClient.verify(bmp, ctx, note, lang)
                        android.util.Log.d("CropDoctor", "analyze: verify done")
                        rep = rep.copy(verified = true, cloud = cloud)
                    } catch (e: Exception) {
                        android.util.Log.w("CropDoctor", "analyze: verify failed", e)
                        rep = rep.copy(
                            cloud = CloudVerification(
                                reviewNote = s.cloudFailedPrefix + e.message + s.cloudFailedSuffix
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
                error = s.analysisFailed + e.message
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
                val (cloud, _) = AiClient.verify(bmp, target.context, target.note, lang)
                val updated = target.copy(verified = true, cloud = cloud)
                if (updated.id == report?.id) report = updated
                runCatching { ReportStore.save(updated) }
                history = ReportStore.list()
            } catch (e: Exception) {
                error = s.verifyFailed + e.message
            } finally {
                verifying = false
            }
        }
    }

    fun chipForGuess(guess: String): String {
        val g = guess.lowercase()
        val chips = listOf(
            "paddy", "wheat", "tomato", "cotton", "sugarcane", "soybean",
            "maize", "mango", "chili", "papaya", "chikoo", "fenugreek"
        )
        chips.firstOrNull { g.contains(it) }?.let { return it }
        if (g.contains("corn")) return "maize"
        if (g.contains("rice")) return "paddy"
        if (g.contains("mirchi")) return "chili"
        return "other"
    }

    fun confirmCrop(target: Report, choice: String) {
        if (choice.isEmpty()) return
        val chip = chipForGuess(choice)
        if (target.context.crop == chip) return
        val updatedCtx = target.context.copy(crop = chip)
        val proposal = RulesEngine.propose(updatedCtx, target.observation, lang)
        val updated = target.copy(context = updatedCtx, proposal = proposal)
        if (updated.id == report?.id) report = updated
        scope.launch {
            runCatching { ReportStore.save(updated) }
            if (tab == 1) history = ReportStore.list()
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
        StatusPill(s, modelStatus)
        Spacer(Modifier.height(8.dp))
        LangRow(s, lang) { lang = it; saveLang(context, it) }
        Spacer(Modifier.height(8.dp))
        if (report == null || tab == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { tab = 0 }, enabled = tab != 0) { Text(s.tabCapture) }
                OutlinedButton(
                    onClick = { tab = 1; selectedId = null; scope.launch { history = ReportStore.list() } },
                    enabled = tab != 1
                ) { Text(s.tabHistory) }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(error, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (tab == 1) {
            HistoryView(
                s = s,
                lang = lang,
                history = history,
                selectedId = selectedId,
                online = Net.isOnline(context),
                verifying = verifying,
                onSelect = { selectedId = it },
                onBack = { selectedId = null },
                onVerify = {
                    error = s.reverifyNeedsPhoto
                },
                onConfirm = { rep, choice ->
                    scope.launch {
                        ReportStore.confirmPriority(rep.id, choice, choice != rep.proposal.priority)
                        history = ReportStore.list()
                    }
                },
                onCropConfirm = { rep, choice -> confirmCrop(rep, choice) }
            )
            return@Column
        }

        val rep = report
        if (rep != null) {
            CaseDetail(
                s = s,
                lang = lang,
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
                photo = bitmap,
                onCropConfirm = { confirmCrop(rep, it) }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { resetCase() }, modifier = Modifier.fillMaxWidth()) {
                Text(s.startNewCase)
            }
            SafetyFooter(s)
            return@Column
        }

        // Capture branch.
        Text("Crop Doctor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(s.appTag)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onDeviceOnly,
                onClick = { onDeviceOnly = false },
                label = { Text(s.modeAuto) }
            )
            FilterChip(
                selected = onDeviceOnly,
                onClick = { onDeviceOnly = true },
                label = { Text(s.modeDevice) }
            )
        }
        if (onDeviceOnly) {
            Text(
                s.modeDeviceNote,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { camera.launch(photoUri) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) { Text(s.takePhoto, fontSize = 18.sp) }
        Spacer(Modifier.height(8.dp))
        bitmap?.let { Image(it.asImageBitmap(), "leaf photo", Modifier.fillMaxWidth().height(220.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(s.trySample, style = MaterialTheme.typography.labelLarge)
        val sampleThumbs = remember {
            mapOf(
                "sample_spotted" to Photo.sampleBitmap(context, "sample_spotted"),
                "sample_healthy" to Photo.sampleBitmap(context, "sample_healthy"),
                "sample_notplant" to Photo.sampleBitmap(context, "sample_notplant")
            )
        }
        val sampleLabels = mapOf(
            "sample_spotted" to s.sampleSpotted,
            "sample_healthy" to s.sampleHealthy,
            "sample_notplant" to s.sampleNotPlant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            sampleThumbs.forEach { (key, bmp) ->
                val label = sampleLabels[key] ?: key
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
                            error = s.sampleMissing
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
                    Text(s.blurryTitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { camera.launch(photoUri) }) { Text(s.retake) }
                        OutlinedButton(onClick = { sharpDismissed = true }) { Text(s.useAnyway) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        questionsFor(s).forEach { q ->
            ChipRow(q, answerFor(q.key, if (q.key == "crop") "unknown" else "not_sure")) { answers[q.key] = it }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = note, onValueChange = { note = it },
            label = { Text(s.noteLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val blocked = analyzing || bitmap == null ||
            (score != null && score < Photo.SHARPNESS_THRESHOLD && !sharpDismissed)
        Button(onClick = { analyze() }, enabled = !blocked, modifier = Modifier.fillMaxWidth()) {
            Text(if (analyzing) s.analyzing else s.analyzeBtn)
        }
        SafetyFooter(s)
    }
}
@Composable
fun CaseDetail(
    s: LangStrings,
    lang: AppLang,
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
    photo: Bitmap? = null,
    onCropConfirm: (String) -> Unit
) {
    VerdictHeadline(s, lang, report, onCropConfirm)
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
            s = s,
            reason = report.proposal.reasons.firstOrNull() ?: s.seeReasons,
            urgent = urgent
        )
        Spacer(Modifier.height(8.dp))
    }
    ProposalCard(s, report.proposal, report.workerPriority, onConfirm)
    Spacer(Modifier.height(8.dp))
    ObservationCard(s, report.observation, report.servedBy)
    Spacer(Modifier.height(8.dp))
    CloudCard(s, report, online, verifying, onVerify)
    Spacer(Modifier.height(8.dp))
    if (showFollowup) {
        if (!report.delivered) {
            Button(onClick = onDelivered, modifier = Modifier.fillMaxWidth()) {
                Text(s.markDelivered)
            }
        } else {
            Text(s.delivered)
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = feedback, onValueChange = onFeedbackChange,
            label = { Text(s.feedbackLabel) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onFeedbackSave, modifier = Modifier.fillMaxWidth()) {
            Text(s.saveFeedback)
        }
        Spacer(Modifier.height(8.dp))
    }
    var showDetails by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showDetails = !showDetails },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (showDetails) s.detailsHide else s.detailsShow) }
    if (showDetails) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(s.flagsLabel + report.proposal.flags.joinToString().ifEmpty { s.flagsNone })
                Text(s.servedByRaw + report.servedBy)
                Text(s.agreementLabel + report.agreementLevel())
            }
        }
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
    s: LangStrings,
    lang: AppLang,
    history: List<Report>,
    selectedId: String?,
    online: Boolean,
    verifying: Boolean,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    onVerify: (Report) -> Unit,
    onConfirm: (Report, String) -> Unit,
    onCropConfirm: (Report, String) -> Unit
) {
    val selected = history.find { it.id == selectedId }
    if (selected == null) {
        if (history.isEmpty()) {
            Text(s.histEmpty)
        }
        history.forEach { rep ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { onSelect(rep.id) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("${rep.observation.subject} / ${rep.observation.extent}")
                    Text(s.proposedLabel + priorityLabel(rep.proposal.priority, s))
                    if (rep.delivered) Text(s.delivered)
                    if (!rep.verified) Text(s.verifyPending)
                }
            }
        }
    } else {
        OutlinedButton(onClick = onBack) { Text(s.backToList) }
        Spacer(Modifier.height(8.dp))
        CaseDetail(
            s = s, lang = lang,
            report = selected, online = online, verifying = verifying,
            feedback = "", onFeedbackChange = {},
            onVerify = { onVerify(selected) },
            onConfirm = { onConfirm(selected, it) },
            onDelivered = {}, onFeedbackSave = {}, showFollowup = false,
            onCropConfirm = { onCropConfirm(selected, it) }
        )
    }
}
