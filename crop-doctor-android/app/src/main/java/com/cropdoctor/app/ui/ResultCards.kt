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
import com.cropdoctor.app.util.AppLang
import com.cropdoctor.app.util.LangStrings

fun priorityLabel(priority: String, s: LangStrings): String =
    if (priority == "prioritize_review") s.priorityExpert else s.priorityStandard

fun priorityColor(priority: String): Color =
    if (priority == "prioritize_review") Color(0xFFB71C1C) else Color(0xFF1B5E20)

@Composable
fun StatusPill(s: LangStrings, modelStatus: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        val label = if (modelStatus == "UNAVAILABLE") s.modelUnavailable
        else s.modelStatusPrefix + modelStatus
        Text(
            label,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ObservationCard(s: LangStrings, lang: AppLang, obs: FieldObservation, servedBy: String) {
    val path = servedByLabel(servedBy, s)
    val clean = obs.subject == "plant" &&
        obs.symptoms.trim().lowercase() in listOf("", "none", "none.") &&
        obs.extent == "none"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${s.whatWeSee} ($path)", fontWeight = FontWeight.Bold)
            if (obs.cropGuess.isNotEmpty() && obs.cropGuess != "unknown") {
                Text(s.looksLike + obs.cropGuess + s.notConfirmed)
            }
            if (clean) {
                Text(s.nothingWrong)
            } else {
                if (obs.subject != "plant") Text(s.subjectLabel + obs.subject)
                if (obs.symptoms.isNotEmpty()) Text(s.seenLabel + obs.shownSymptoms(lang))
                if (obs.location.isNotEmpty()) Text(s.whereLabel + obs.shownLocation(lang))
                Text(s.extentLabel + obs.extent)
                if (obs.notes.isNotEmpty()) Text(s.notesLabel + obs.shownNotes(lang))
            }
        }
    }
}

fun servedByLabel(servedBy: String, s: LangStrings): String {
    val l = servedBy.lowercase()
    return when {
        l.contains("cloud") -> s.checkedOnline
        l.contains("device") -> s.checkedOnPhone
        else -> servedBy
    }
}

@Composable
fun ProposalCard(
    s: LangStrings,
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
                s.recommendation + priorityLabel(proposal.priority, s),
                fontWeight = FontWeight.Bold,
                color = priorityColor(proposal.priority)
            )
            proposal.reasons.forEach { Text("• $it") }
            Spacer(Modifier.height(8.dp))
            if (workerPriority.isEmpty()) {
                Text(s.workerConfirm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onConfirm("prioritize_review") }) { Text(s.expertSoon) }
                    OutlinedButton(onClick = { onConfirm("standard_followup") }) { Text(s.standardBtn) }
                }
            } else {
                Text(s.workerSet + priorityLabel(workerPriority, s))
            }
        }
    }
}

@Composable
fun CloudCard(
    s: LangStrings,
    lang: AppLang,
    report: Report,
    online: Boolean,
    verifying: Boolean,
    onVerify: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(s.cloudTitle, fontWeight = FontWeight.Bold)
            val cloud: CloudVerification? = report.cloud
            when {
                cloud != null -> {
                    val bothClean = report.isCleanObservation() &&
                        cloud.subject == "plant" && cloud.extent == "none"
                    if (bothClean) {
                        Text(s.bothClean)
                    } else when (report.agreementLevel()) {
                        "match" -> Text(s.agreeMatch)
                        "small_diff" -> Text(
                            s.agreeSmallPrefix + report.observation.extent +
                                s.agreeSmallMid + cloud.extent + s.agreeSmallSuffix
                        )
                        else -> Text(s.agreeUpdated)
                    }
                    Text(cloud.shownReviewNote(lang))
                    cloud.shownQuestions(lang).forEach { Text(s.askExpertPrefix + it) }
                    Text(s.cloudPriority + priorityLabel(cloud.priority, s))
                }
                verifying -> Text(s.verifying)
                !online -> {
                    Text(s.verifyPendingTitle)
                    Text(s.verifyPendingNote)
                }
                else -> {
                    Text(s.notVerified)
                    Button(onClick = onVerify) { Text(s.verifyNow) }
                }
            }
        }
    }
}

@Composable
fun EscalationCard(s: LangStrings, reason: String, urgent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (urgent) s.escalateUrgent
                else s.escalateReview,
                fontWeight = FontWeight.Bold
            )
            Text(s.whyLabel + reason)
            Text(s.helpline)
        }
    }
}

@Composable
fun VerdictHeadline(
    s: LangStrings,
    lang: AppLang,
    report: Report,
    onCropConfirm: (String) -> Unit
) {
    if (report.showGreen()) {
        GreenCard(s, report, onCropConfirm)
        return
    }
    val h = report.headline(lang)
    val color = headlineColor(h.color)
    val action = when (h.severity) {
        "HIGH" -> s.actionHigh
        "MEDIUM" -> s.actionMedium
        "LOW" -> s.actionLow
        "UNCONFIRMED" -> s.actionUnconfirmed
        else -> s.actionRetake
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(12.dp)) {
            SeverityBadge(s, h.severity, color)
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
fun SeverityBadge(s: LangStrings, severity: String, color: Color) {
    val label = when (severity) {
        "HIGH" -> s.badgeHigh
        "MEDIUM" -> s.badgeMedium
        "LOW" -> s.badgeLow
        "NONE" -> s.badgeNone
        "UNCONFIRMED" -> s.badgeUnconfirmed
        else -> s.badgeRetake
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
    s: LangStrings,
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
            SeverityBadge(s, "NONE", Color(0xFF1B5E20))
            Text(
                s.looksHealthy,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20),
                style = MaterialTheme.typography.titleLarge
            )
            if (guess.isNotEmpty() && guess != "unknown") {
                Text(s.thisLooksLike + guess + ".")
            }
            Text(s.noProblems)
            Text(s.watchFor)
            Text(s.newPhoto3Days)
            Text(s.notSureCall)
            if (chipUnknown && guess.isNotEmpty() && guess != "unknown") {
                Spacer(Modifier.height(8.dp))
                Text(s.notSureIt + guess + "?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCropConfirm(guess) }) { Text(s.yesBtn) }
                    OutlinedButton(onClick = { onCropConfirm("other") }) { Text(s.noBtn) }
                }
            }
        }
    }
}

@Composable
fun SafetyFooter(s: LangStrings) {
    Text(
        s.safetyFooter,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
