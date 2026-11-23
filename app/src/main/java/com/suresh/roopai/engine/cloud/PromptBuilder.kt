package com.suresh.roopai.engine.cloud

/**
 * Builds the text sent with the photo. Every prompt carries the same identity lock so the
 * model changes only the requested attribute.
 */
object PromptBuilder {
    const val MAX_CUSTOM_LENGTH = 500

    /** Attributes every prompt must tell the model to preserve. */
    val PRESERVED = listOf("identity", "face shape", "skin tone", "lighting", "background")

    private const val LOCK =
        "You are a precise photo retoucher. Edit the attached photo of a real person.\n" +
            "Strict rules:\n" +
            "1. Keep the same person. Preserve their identity, face shape, facial features, skin tone, " +
            "eye colour and apparent age exactly.\n" +
            "2. Preserve the lighting, background, camera angle, pose, framing, clothing and image " +
            "composition exactly.\n" +
            "3. Change only the requested attribute described below. Do not beautify, slim, " +
            "whiten or restyle anything that was not requested.\n" +
            "4. The result must be a photorealistic photograph with the same aspect ratio as the input, " +
            "with no text, watermark, border or collage.\n"

    fun build(instruction: String, allows: String = ""): String {
        val sb = StringBuilder(LOCK)
        if (allows.isNotBlank()) {
            sb.append("5. Exception: this edit may alter ").append(allows.trim())
                .append(" as far as the request needs; everything else in rules 1 and 2 still applies.\n")
        }
        sb.append("\nRequested change: ").append(instruction.trim())
        return sb.toString()
    }

    fun forPreset(preset: CloudPreset): String = build(preset.instruction, preset.allows)

    /** Free-text edit typed by the user. Returns null when there is nothing usable. */
    fun forCustom(text: String): String? {
        val clean = sanitize(text)
        return if (clean.isEmpty()) null else build(clean)
    }

    /** Collapses whitespace, strips control characters and limits the length. */
    fun sanitize(text: String): String =
        text.replace(Regex("[\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(MAX_CUSTOM_LENGTH)
}
