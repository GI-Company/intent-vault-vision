package space.globalintent.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serialized, on-device EmbeddingGemma inference. Neither camera data nor notes
 * leave the phone. Embeddings are similarities, never calibrated probabilities.
 */
class LocalSemanticEngine(
    private val context: Context,
    private val notify: (String) -> Unit
) {
    companion object {
        const val MODEL = "models/embeddinggemma-2-740m.litertlm"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val imageBusy = AtomicBoolean(false)
    private var engine: UniversalEmbedder? = null // worker thread only
    private val textCache = mutableMapOf<String, Embedding>()
    @Volatile var ready = false
        private set

    init {
        worker.execute {
            try {
                // Fail visibly rather than pretending the model is installed.
                context.assets.openFd(MODEL).use {
                    require(it.length > 100_000_000L) { "Bundled model is incomplete" }
                }
                val opts = UniversalEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                    .setTextDelegate(Delegate.CPU)
                    .setVisionDelegate(Delegate.CPU)
                    .setL2Normalize(true)
                    .build()
                engine = UniversalEmbedder.createFromOptions(context, opts)
                ready = true
                notify("EmbeddingGemma 2 loaded locally. No runtime model download.")
            } catch (e: Exception) {
                ready = false
                notify("Model unavailable: ${e.message}. Build the model-included APK.")
            }
        }
    }

    fun scoreImage(bitmap: Bitmap, descriptions: List<String>, callback: (String) -> Unit) {
        if (!ready || !imageBusy.compareAndSet(false, true)) return
        worker.execute {
            try {
                val model = requireNotNull(engine)
                val targets = descriptions.map(String::trim).filter(String::isNotBlank).take(10)
                if (targets.isEmpty()) { callback("Add a scene description."); return@execute }
                val mpImage = BitmapImageBuilder(bitmap).build()
                try {
                    val imageVector = model.embedImage(mpImage).embeddings().first()
                    val ranked = targets.map { label ->
                        val textVector = textCache.getOrPut(label) {
                            model.embedText(label).embeddings().first()
                        }
                        label to UniversalEmbedder.cosineSimilarity(imageVector, textVector)
                    }.sortedByDescending { it.second }.take(3)
                    callback(ranked.joinToString("\n") {
                        "${it.first}  ·  similarity ${String.format(Locale.US, "%.3f", it.second)}"
                    })
                } finally {
                    mpImage.close()
                }
            } catch (e: Exception) {
                callback("Unable to score image: ${e.message}")
            } finally {
                imageBusy.set(false)
            }
        }
    }

    fun findNotes(query: String, notes: List<String>, callback: (String) -> Unit) {
        if (notes.isEmpty()) { callback("Your private vault is empty."); return }
        worker.execute {
            val model = engine
            if (model == null) {
                val matches = notes.filter { it.contains(query, ignoreCase = true) }.take(5)
                callback(if (matches.isEmpty()) "No literal matches. The local model is unavailable."
                    else matches.joinToString("\n\n"))
                return@execute
            }
            try {
                val q = model.embedText(query).embeddings().first()
                val found = notes.map { note ->
                    val vec = textCache.getOrPut(note) {
                        model.embedText(note).embeddings().first()
                    }
                    note to UniversalEmbedder.cosineSimilarity(q, vec)
                }.sortedByDescending { it.second }.take(5)
                callback(found.joinToString("\n\n") {
                    "${it.first}\nSimilarity: ${String.format(Locale.US, "%.3f", it.second)}"
                })
            } catch (e: Exception) {
                callback("Vault search failed: ${e.message}")
            }
        }
    }

    fun close() {
        ready = false
        worker.execute {
            engine?.close()
            engine = null
            textCache.clear()
        }
        worker.shutdown()
    }
}
