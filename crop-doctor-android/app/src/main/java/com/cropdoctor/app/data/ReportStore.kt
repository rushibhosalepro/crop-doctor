package com.cropdoctor.app.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Case records in Firestore. Anonymous auth, one user's cases.
 * Query avoids composite indexes (single orderBy + client-side uid filter).
 * Firestore caches offline automatically; writes sync when online.
 */
object ReportStore {
    private const val TAG = "ReportStore"
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    suspend fun signIn(): Boolean {
        return try {
            if (auth.currentUser == null) auth.signInAnonymously().await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "anonymous sign-in failed", e)
            false
        }
    }

    fun uid(): String = auth.currentUser?.uid ?: "offline"

    suspend fun save(report: Report): String {
        val col = db.collection("reports")
        val doc = if (report.id.isEmpty()) col.document() else col.document(report.id)
        val data = hashMapOf<String, Any?>(
            "uid" to uid(),
            "createdAt" to FieldValue.serverTimestamp(),
            "note" to report.note,
            "context" to mapOf(
                "crop" to report.context.crop,
                "watered" to report.context.watered,
                "fertilizer" to report.context.fertilizer,
                "spray" to report.context.spray,
                "weather" to report.context.weather,
                "stage" to report.context.stage,
                "spread" to report.context.spread
            ),
            "observation" to mapOf(
                "subject" to report.observation.subject,
                "symptoms" to report.observation.symptoms,
                "location" to report.observation.location,
                "extent" to report.observation.extent,
                "photoQuality" to report.observation.photoQuality,
                "notes" to report.observation.notes,
                "symptomsLocal" to report.observation.symptomsLocal,
                "locationLocal" to report.observation.locationLocal,
                "notesLocal" to report.observation.notesLocal,
                "lang" to report.observation.lang
            ),
            "proposal" to mapOf(
                "priority" to report.proposal.priority,
                "reasons" to report.proposal.reasons,
                "flags" to report.proposal.flags
            ),
            "workerPriority" to report.workerPriority,
            "workerOverrode" to report.workerOverrode,
            "servedBy" to report.servedBy,
            "verified" to report.verified,
            "cloud" to report.cloud?.let {
                mapOf(
                    "subject" to it.subject,
                    "extent" to it.extent,
                    "reviewNote" to it.reviewNote,
                    "questionsForExpert" to it.questionsForExpert,
                    "priority" to it.priority,
                    "reviewNoteLocal" to it.reviewNoteLocal,
                    "questionsForExpertLocal" to it.questionsForExpertLocal,
                    "lang" to it.lang
                )
            },
            "delivered" to report.delivered,
            "feedback" to report.feedback
        )
        doc.set(data).await()
        return doc.id
    }

    suspend fun confirmPriority(id: String, priority: String, overrode: Boolean) {
        runCatching {
            db.collection("reports").document(id)
                .update(mapOf("workerPriority" to priority, "workerOverrode" to overrode))
                .await()
        }.onFailure { Log.w(TAG, "confirmPriority failed", it) }
    }

    suspend fun markDelivered(id: String) {
        runCatching {
            db.collection("reports").document(id).update("delivered", true).await()
        }.onFailure { Log.w(TAG, "markDelivered failed", it) }
    }

    suspend fun saveFeedback(id: String, text: String) {
        runCatching {
            db.collection("reports").document(id).update("feedback", text).await()
        }.onFailure { Log.w(TAG, "saveFeedback failed", it) }
    }

    suspend fun list(limit: Long = 50): List<Report> {
        return try {
            val snap = db.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit).get().await()
            val mine = uid()
            snap.documents.mapNotNull { fromMap(it.id, it.data, mine) }
        } catch (e: Exception) {
            Log.w(TAG, "history load failed (offline?)", e)
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromMap(id: String, m: Map<String, Any?>?, mine: String): Report? {
        if (m == null) return null
        if ((m["uid"] as? String) != mine) return null
        val ctx = (m["context"] as? Map<String, Any?>) ?: emptyMap()
        val obs = (m["observation"] as? Map<String, Any?>) ?: emptyMap()
        val prop = (m["proposal"] as? Map<String, Any?>) ?: emptyMap()
        val cloud = m["cloud"] as? Map<String, Any?>
        fun s(map: Map<String, Any?>, key: String, default: String): String =
            (map[key] as? String) ?: default
        return Report(
            id = id,
            createdAt = (m["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            note = (m["note"] as? String).orEmpty(),
            context = LeafContext(
                crop = s(ctx, "crop", "unknown"),
                watered = s(ctx, "watered", "not_sure"),
                fertilizer = s(ctx, "fertilizer", "not_sure"),
                spray = s(ctx, "spray", "not_sure"),
                weather = s(ctx, "weather", "not_sure"),
                stage = s(ctx, "stage", "not_sure"),
                spread = s(ctx, "spread", "not_sure")
            ),
            observation = FieldObservation(
                subject = s(obs, "subject", "unclear"),
                symptoms = s(obs, "symptoms", ""),
                location = s(obs, "location", ""),
                extent = s(obs, "extent", "unknown"),
                photoQuality = s(obs, "photoQuality", "unknown"),
                notes = s(obs, "notes", ""),
                symptomsLocal = s(obs, "symptomsLocal", ""),
                locationLocal = s(obs, "locationLocal", ""),
                notesLocal = s(obs, "notesLocal", ""),
                lang = s(obs, "lang", "en")
            ),
            proposal = PriorityProposal(
                priority = s(prop, "priority", "standard_followup"),
                reasons = (prop["reasons"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                flags = (prop["flags"] as? List<*>)?.map { it.toString() } ?: emptyList()
            ),
            workerPriority = (m["workerPriority"] as? String).orEmpty(),
            workerOverrode = (m["workerOverrode"] as? Boolean) ?: false,
            servedBy = (m["servedBy"] as? String) ?: "unknown",
            verified = (m["verified"] as? Boolean) ?: false,
            cloud = cloud?.let {
                CloudVerification(
                    subject = s(it, "subject", "unclear"),
                    extent = s(it, "extent", "unknown"),
                    reviewNote = s(it, "reviewNote", ""),
                    questionsForExpert = (it["questionsForExpert"] as? List<*>)
                        ?.map { q -> q.toString() } ?: emptyList(),
                    priority = s(it, "priority", "standard_followup"),
                    reviewNoteLocal = s(it, "reviewNoteLocal", ""),
                    questionsForExpertLocal = (it["questionsForExpertLocal"] as? List<*>)
                        ?.map { q -> q.toString() } ?: emptyList(),
                    lang = s(it, "lang", "en")
                )
            },
            delivered = (m["delivered"] as? Boolean) ?: false,
            feedback = (m["feedback"] as? String).orEmpty()
        )
    }
}
