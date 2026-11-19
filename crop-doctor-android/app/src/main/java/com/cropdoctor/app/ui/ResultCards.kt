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
    if (priority == "prioritize_review") "PRIORITIZE REVIEW" else "STANDARD FOLLOW-UP"

fun priorityColor(priority: String): Color =
    if (priority == "prioritize_review") Color(0xFFB71C1C) else Color(0xFF1B5E20)

@Composable
fun StatusPill(modelStatus: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Text(
            "On-device model: $modelStatus",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ObservationCard(obs: FieldObservation, servedBy: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Field observation", fontWeight = FontWeight.Bold)
            Text("Served by: $servedBy", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("Subject: ${obs.subject}")
            if (obs.symptoms.isNotEmpty()) Text("Seen: ${obs.symptoms}")
            if (obs.location.isNotEmpty()) Text("Where: ${obs.location}")
            Text("Extent: ${obs.extent}")
            if (obs.notes.isNotEmpty()) Text("Notes: ${obs.notes}")
        }
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
                "Proposed: ${priorityLabel(proposal.priority)}",
                fontWeight = FontWeight.Bold,
                color = priorityColor(proposal.priority)
            )
            proposal.reasons.forEach { Text("• $it") }
            if (proposal.flags.isNotEmpty()) {
                Text("Flags: ${proposal.flags.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            if (workerPriority.isEmpty()) {
                Text("Worker: confirm or override priority.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConfirm("prioritize_review") }) { Text("Prioritize") }
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
                    val agree = report.observationsAgree()
                    Text(if (agree) "Field and cloud observations: MATCH" else "UPDATED ASSESSMENT — cloud differs")
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
fun EscalationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Show this case to an expert before acting.", fontWeight = FontWeight.Bold)
            Text("Kisan Call Centre: 1800-180-1551 (toll-free, 6 AM–10 PM)")
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
