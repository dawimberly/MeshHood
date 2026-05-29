package com.example.myphone

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONObject
import java.io.File

/**
 * Optional on-device LLM brain (Gemma via MediaPipe LLM Inference).
 *
 * MeshHood works fully without it — the rule-based [Coordinator] is the default.
 * When a Gemma `.task` model is present on the device, this engine takes over
 * message understanding for far better handling of messy, real-world phrasing
 * ("my kid's been without their puffer since noon" -> need / medical).
 *
 * Everything runs ON DEVICE, offline — no servers, matching the privacy ideology.
 *
 * To enable, push a model to one of MODEL_CANDIDATES, e.g.:
 *   adb push gemma-3-1b-it-int4.task /data/local/tmp/llm/
 *
 * Note: Google is migrating this API to LiteRT-LM; tasks-genai still works and
 * is the simplest path for this prototype. The rest of the app is agnostic to
 * which engine produced the classification.
 */
object LlmEngine {

    private const val TAG = "LlmEngine"

    private val MODEL_CANDIDATES = listOf(
        "gemma-3-1b-it-int4.task",
        "gemma.task",
    )

    private val ALLOWED_CATEGORIES = setOf(
        "power", "water", "medical", "cooling", "food",
        "fuel", "shelter", "warmth", "transport", "tools",
    )

    @Volatile
    var isReady: Boolean = false
        private set

    private var llm: LlmInference? = null

    data class Parsed(
        val intent: Coordinator.Intent,
        val categories: Set<String>,
        val location: String?,
    )

    /** Find a model file in app files dir or /data/local/tmp/llm. */
    private fun findModel(context: Context): File? {
        val dirs = listOf(context.filesDir, File("/data/local/tmp/llm"))
        for (dir in dirs) {
            for (name in MODEL_CANDIDATES) {
                val f = File(dir, name)
                if (f.exists() && f.length() > 0) return f
            }
        }
        return null
    }

    /** Load the model once (call on a background thread). Safe to call if absent. */
    fun tryLoad(context: Context): Boolean {
        if (isReady) return true
        val model = findModel(context) ?: run {
            Log.i(TAG, "No on-device model found — using rule-based Coordinator.")
            return false
        }
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.absolutePath)
                .setMaxTokens(512)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            isReady = true
            Log.i(TAG, "Loaded on-device LLM: ${model.absolutePath}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load LLM, falling back to rules", t)
            isReady = false
            false
        }
    }

    /** Classify a neighborhood message. Returns null on any failure (caller falls back). */
    fun extract(text: String): Parsed? {
        val engine = llm ?: return null
        val prompt = buildPrompt(text)
        return try {
            val raw = engine.generateResponse(prompt) ?: return null
            parseResponse(raw)
        } catch (t: Throwable) {
            Log.e(TAG, "inference failed", t)
            null
        }
    }

    private fun buildPrompt(text: String): String = """
        You classify short neighborhood messages during a disaster.
        Reply with ONLY compact JSON, no prose:
        {"intent":"offer|need|none","categories":[],"location":""}
        categories may include only: power, water, medical, cooling, food, fuel, shelter, warmth, transport, tools.
        "offer" = the sender HAS or can provide something. "need" = the sender WANTS or lacks something.
        location = a short place/address if present, else "".
        Message: "${text.replace("\"", "'")}"
    """.trimIndent()

    private fun parseResponse(raw: String): Parsed? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = JSONObject(raw.substring(start, end + 1))

        val intent = when (obj.optString("intent", "none").lowercase()) {
            "offer" -> Coordinator.Intent.OFFER
            "need" -> Coordinator.Intent.NEED
            else -> Coordinator.Intent.NONE
        }
        val cats = linkedSetOf<String>()
        obj.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optString(i, "").lowercase().trim()
                if (c in ALLOWED_CATEGORIES) cats.add(c)
            }
        }
        val loc = obj.optString("location", "").trim().ifEmpty { null }
        return Parsed(intent, cats, loc)
    }
}
