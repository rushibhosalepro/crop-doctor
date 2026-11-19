package com.cropdoctor.app.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import kotlinx.coroutines.withTimeout

/**
 * All AI inference goes through Firebase AI Logic (hackathon requirement).
 * Two SEPARATE requests form the pipeline (SDK fallback is not the pipeline):
 * 1. triage(): PREFER_ON_DEVICE — field observations, works offline on Nano.
 * 2. verify(): ONLY_IN_CLOUD — independent review, no Nano JSON shown
 *    (anti-anchoring). Queued until online.
 */
@OptIn(PublicPreviewAPI::class)
object AiClient {
    private const val TAG = "AiClient"

    /**
     * Ultra-fast free-tier model, no billing required.
     * gemini-3.8-flash was tried first but spends minutes "thinking" per
     * request (see AI monitoring traces) — unusable with a 60s app timeout.
     */
    const val CLOUD_MODEL = "gemini-3.5-flash-lite"

    private val triageModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_ON_DEVICE)
        )
    }

    private val verifyModel: GenerativeModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.ONLY_IN_CLOUD)
        )
    }

    suspend fun modelStatusText(): String {
        return try {
            statusName(FirebaseAIOnDevice.checkStatus())
        } catch (e: Exception) {
            Log.w(TAG, "status check failed", e)
            "CHECK_FAILED"
        }
    }

    private fun statusName(status: OnDeviceModelStatus): String = when (status) {
        OnDeviceModelStatus.AVAILABLE -> "AVAILABLE"
        OnDeviceModelStatus.DOWNLOADABLE -> "DOWNLOADABLE"
        OnDeviceModelStatus.DOWNLOADING -> "DOWNLOADING"
        OnDeviceModelStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> status.javaClass.simpleName
    }

    /** Best-effort pre-download at startup. Never blocks the UI. */
    suspend fun ensureModelDownloaded(onProgress: (String) -> Unit) {
        try {
            val status = FirebaseAIOnDevice.checkStatus()
            onProgress(statusName(status))
            if (status == OnDeviceModelStatus.DOWNLOADABLE) {
                FirebaseAIOnDevice.download().collect { dl ->
                    onProgress("Downloading… (${dl.javaClass.simpleName})")
                }
                onProgress(statusName(FirebaseAIOnDevice.checkStatus()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "model download check failed", e)
            onProgress("CHECK_FAILED")
        }
    }

    /**
     * Field observations. Returns (observation, servedByPath).
     * Routes explicitly: on-device is attempted only when the model reports
     * AVAILABLE, because PREFER_ON_DEVICE can hang on devices without AICore
     * instead of falling back quickly.
     */
    suspend fun triage(bitmap: Bitmap, context: LeafContext, note: String): Pair<FieldObservation, String> {
        val prompt = content {
            image(bitmap)
            text(TRIAGE_PROMPT + contextBlock(context, note))
        }
        val onDeviceReady = try {
            FirebaseAIOnDevice.checkStatus() == OnDeviceModelStatus.AVAILABLE
        } catch (e: Exception) {
            Log.w(TAG, "status check failed, using cloud", e)
            false
        }
        val model = if (onDeviceReady) triageModel else verifyModel
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        return parseObservation(response.text.orEmpty()) to response.inferenceSource.toString()
    }

    /** Independent cloud review. Returns (verification, servedByPath). */
    suspend fun verify(bitmap: Bitmap, context: LeafContext, note: String): Pair<CloudVerification, String> {
        val prompt = content {
            image(bitmap)
            text(VERIFY_PROMPT + contextBlock(context, note))
        }
        val response = withTimeout(60_000L) {
            verifyModel.generateContent(prompt)
        }
        return parseVerification(response.text.orEmpty()) to response.inferenceSource.toString()
    }

    /** Temporary diagnostic: text-only cloud call, no image. */
    suspend fun ping(): String {
        val response = withTimeout(60_000L) {
            verifyModel.generateContent("Reply with exactly: OK")
        }
        return response.text.orEmpty()
    }

    private fun contextBlock(context: LeafContext, note: String): String {
        return "\nContext: crop=${context.crop}, watered=${context.watered}, " +
            "fertilizer=${context.fertilizer}, spray=${context.spray}, " +
            "weather=${context.weather}, stage=${context.stage}, " +
            "spread=${context.spread}. Worker note: $note"
    }

    private const val TRIAGE_PROMPT =
        "You are a crop observation assistant. Describe ONLY what is visible. " +
            "Answer with ONLY this JSON, no other text: " +
            "{\"subject\": one of plant|not_plant|unclear, " +
            "\"symptoms\": \"visible marks in plain words\", " +
            "\"location\": \"where on the plant\", " +
            "\"extent\": one of none|small|moderate|large|unknown, " +
            "\"photoQuality\": one of ok|poor, " +
            "\"notes\": \"anything uncertain\"}. " +
            "Rules: never name a disease or pest; never suggest treatment, " +
            "severity, or urgency; if not a plant use not_plant; " +
            "if unsure use unclear; English only."

    private const val VERIFY_PROMPT =
        "You are an expert crop reviewer. Analyze this leaf photo INDEPENDENTLY. " +
            "Answer with ONLY this JSON, no other text: " +
            "{\"subject\": one of plant|not_plant|unclear, " +
            "\"extent\": one of none|small|moderate|large|unknown, " +
            "\"reviewNote\": \"2-3 lines for the extension worker\", " +
            "\"questionsForExpert\": [\"up to 3 questions\"], " +
            "\"priority\": one of prioritize_review|standard_followup}. " +
            "Rules: never name a confirmed disease; never recommend chemicals, " +
            "dosage, or spraying; never say the case can be ignored; " +
            "English only."
}
