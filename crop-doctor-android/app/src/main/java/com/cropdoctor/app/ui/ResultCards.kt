package com.cropdoctor.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.data.CloudVerification
import com.cropdoctor.app.data.FieldObservation
import com.cropdoctor.app.data.PriorityProposal
import com.cropdoctor.app.data.Report

fun priorityLabel(priority: String): String =
    if (priority == "prioritize_review") "Show to an expert soon" else "Standard follow-up"

fun priorityColor(priority: String): Color =
    if (priority == "prioritize_review") Color(0xFFB71C1C) else Color(0xFF1B5E20)

@Composable
fun StatusPill(modelStatus: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        val label = if (modelStatus == "UNAVAILABLE") "On-device model unavailable — using cloud"
        else "On-device model: $modelStatus"
        Text(
            label,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ObservationCard(obs: FieldObservation, servedBy: String) {
    val path = servedByLabel(servedBy)
    val clean = obs.subject == "plant" &&
        obs.symptoms.trim().lowercase() in listOf("", "none", "none.") &&
        obs.extent == "none"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("What we see ($path)", fontWeight = FontWeight.Bold)
            if (obs.cropGuess.isNotEmpty() && obs.cropGuess != "unknown") {
                Text("Looks like: ${obs.cropGuess} (not confirmed)")
            }
            if (clean) {
                Text("Nothing wrong seen on the leaves.")
            } else {
                if (obs.subject != "plant") Text("Subject: ${obs.subject}")
                if (obs.symptoms.isNotEmpty()) Text("Seen: ${obs.symptoms}")
                if (obs.location.isNotEmpty()) Text("Where: ${obs.location}")
                Text("Extent: ${obs.extent}")
                if (obs.notes.isNotEmpty()) Text("Notes: ${obs.notes}")
            }
        }
    }
}

fun servedByLabel(servedBy: String): String {
    val s = servedBy.lowercase()
    return when {
        s.contains("cloud") -> "Checked online"
        s.contains("device") -> "Checked on this phone"
        else -> servedBy
    }
}

@Composable
fun ProposalCard(
    proposal: PriorityProposal,
    workerPriority: String,
    onConfirm: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Recommendation: ${priorityLabel(proposal.priority)}",
                fontWeight = FontWeight.Bold,
                color = priorityColor(proposal.priority)
            )
            proposal.reasons.forEach { Text("• $it") }
            Spacer(Modifier.height(8.dp))
            if (workerPriority.isEmpty()) {
                Text("Worker: confirm or override priority.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConfirm("prioritize_review") }) { Text("Expert soon") }
                    OutlinedButton(onClick = { onConfirm("standard_followup") }) { Text("Standard") }
                }
            } else {
                Text("Worker set: ${priorityLabel(workerPriority)}")
            }
        }
    }
}

@Composable
fun CloudCard(report: Report, online: Boolean, verifying: Boolean, onVerify: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Cloud verification", fontWeight = FontWeight.Bold)
            val cloud: CloudVerification? = report.cloud
            when {
                cloud != null -> {
                    val bothClean = report.isCleanObservation() &&
                        cloud.subject == "plant" && cloud.extent == "none"
                    if (bothClean) {
                        Text("Both checks found no problem.")
                    } else when (report.agreementLevel()) {
                        "match" -> Text("Field and cloud agree.")
                        "small_diff" -> Text(
                            "Mostly agree — small detail difference " +
                                "(field: ${report.observation.extent}, cloud: ${cloud.extent})."
                        )
                        else -> Text("Updated check — the cloud sees it differently.")
                    }
                    Text(cloud.reviewNote)
                    cloud.questionsForExpert.forEach { Text("• Ask expert: $it") }
                    Text("Cloud priority: ${priorityLabel(cloud.priority)}")
                }
                verifying -> Text("Verifying in cloud…")
                !online -> {
                    Text("Verification PENDING — no connection.")
                    Text("This case is recorded and will verify when online.")
                }
                else -> {
                    Text("Not verified yet.")
                    Button(onClick = onVerify) { Text("Verify now") }
                }
            }
        }
    }
}

@Composable
fun EscalationCard(reason: String, urgent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (urgent) "Show this case to an expert before acting."
                else "An expert should review this case.",
                fontWeight = FontWeight.Bold
            )
            Text("Why: $reason")
            Text("Kisan Call Centre: 1800-180-1551 (toll-free, 6 AM–10 PM)")
        }
    }
}

@Composable
fun VerdictHeadline(
    report: Report,
    onCropConfirm: (String) -> Unit
) {
    if (report.showGreen()) {
        GreenCard(report, onCropConfirm)
        return
    }
    val h = report.headline()
    val color = headlineColor(h.color)
    val action = when (h.severity) {
        "HIGH" -> "Show an expert before spraying. Call 1800-180-1551."
        "MEDIUM" -> "Check nearby plants. Ask an expert within 2–3 days."
        "LOW" -> "Watch it. Take a new photo in 3 days."
        "UNCONFIRMED" -> "Go online to confirm this check."
        else -> "Take a clear, close-up photo of one leaf."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(12.dp)) {
            SeverityBadge(h.severity, color)
            Text(
                h.issue,
                fontWeight = FontWeight.Bold,
                color = color,
                style = MaterialTheme.typography.titleLarge
            )
            Text(action)
        }
    }
}

fun headlineColor(name: String): Color = when (name) {
    "red" -> Color(0xFFB71C1C)
    "orange" -> Color(0xFFE65100)
    "yellow" -> Color(0xFF8D6E00)
    "green" -> Color(0xFF1B5E20)
    else -> Color(0xFF616161)
}

@Composable
fun SeverityBadge(severity: String, color: Color) {
    val label = when (severity) {
        "HIGH" -> "HIGH RISK"
        "MEDIUM" -> "MEDIUM RISK"
        "LOW" -> "LOW RISK"
        "NONE" -> "HEALTHY"
        "UNCONFIRMED" -> "NOT CONFIRMED"
        else -> "RETAKE PHOTO"
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun GreenCard(
    report: Report,
    onCropConfirm: (String) -> Unit
) {
    val obs = report.observation
    val guess = obs.cropGuess
    val chipUnknown = report.context.crop == "unknown" || report.context.crop == "other"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(Modifier.padding(12.dp)) {
            SeverityBadge("NONE", Color(0xFF1B5E20))
            Text(
                "Looks healthy",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20),
                style = MaterialTheme.typography.titleLarge
            )
            if (guess.isNotEmpty() && guess != "unknown") {
                Text("This looks like $guess.")
            }
            Text("No problems seen in this photo. This is not a guarantee.")
            Text("Watch for: spots, holes, curling, or white powder.")
            Text("Take a new photo in 3 days, or sooner if leaves change.")
            Text("Not sure? Call Kisan Call Centre 1800-180-1551.")
            if (chipUnknown && guess.isNotEmpty() && guess != "unknown") {
                Spacer(Modifier.height(8.dp))
                Text("Not sure it's $guess?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCropConfirm(guess) }) { Text("Yes") }
                    OutlinedButton(onClick = { onCropConfirm("other") }) { Text("No") }
                }
            }
        }
    }
}

@Composable
fun SafetyFooter() {
    Text(
        "AI guess — confirm with a local expert before spraying.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
