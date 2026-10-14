package space.globalintent.vision

/** Count every detected face, require sustained smiles, never identify people. */
class SmileGate(private val threshold: Float = 0.75f,
                private val framesRequired: Int = 3,
                private val cooldownMs: Long = 8_000) {
    private var priorCount = 0
    private var streak = 0
    private var lastCapture = -cooldownMs

    fun sample(probabilities: List<Float?>, now: Long): Boolean {
        if (probabilities.isEmpty() || probabilities.any { it == null || it < threshold }) {
            streak = 0
            priorCount = 0
            return false
        }
        if (probabilities.size != priorCount) streak = 0
        priorCount = probabilities.size
        streak++
        if (streak < framesRequired || now - lastCapture < cooldownMs) return false
        lastCapture = now
        streak = 0
        return true
    }
    fun reset() { streak = 0; priorCount = 0; lastCapture = -cooldownMs }
}