package com.suresh.roopai.engine.cloud

import com.suresh.roopai.edit.Category

/** Groups shown in the AI Edit grid. */
enum class CloudGroup(val title: String, val category: Category) {
    FACIAL_HAIR("Facial hair", Category.FACIAL_HAIR),
    HAIR("Hair", Category.HAIR),
    AGE("Age", Category.FACE),
    EXPRESSION("Expression", Category.FACE),
    ACCESSORIES("Accessories", Category.ACCESSORIES),
    LOOKS("Complete looks", Category.LOOKS),
}

/**
 * A one-tap generative edit. [instruction] only describes the change; [PromptBuilder] wraps it
 * with the identity-preservation rules. [allows] lists what the edit may alter besides the
 * named attribute (e.g. a look may change clothing).
 */
data class CloudPreset(
    val id: String,
    val group: CloudGroup,
    val title: String,
    val instruction: String,
    val allows: String = "",
)

object CloudPresets {
    const val CUSTOM_ID = "custom"

    val ALL: List<CloudPreset> = listOf(
        // ---- facial hair
        CloudPreset("beard_remove", CloudGroup.FACIAL_HAIR, "Clean shave", "Remove all facial hair (beard, moustache and stubble) so the face is clean-shaven. Reveal natural skin and the true jawline and chin underneath."),
        CloudPreset("beard_full", CloudGroup.FACIAL_HAIR, "Full beard", "Add a full, well-groomed medium-length beard with a connected moustache, matching the person's natural hair colour."),
        CloudPreset("beard_boxed", CloudGroup.FACIAL_HAIR, "Short boxed", "Add a short, neatly trimmed boxed beard with sharp cheek and neck lines and a connected moustache, matching the natural hair colour."),
        CloudPreset("beard_goatee", CloudGroup.FACIAL_HAIR, "Goatee", "Add a neat goatee with a connected moustache; cheeks clean-shaven. Match the natural hair colour."),
        CloudPreset("moustache", CloudGroup.FACIAL_HAIR, "Moustache only", "Add a neat, natural moustache matching the natural hair colour. The chin, jaw and cheeks must be clean-shaven."),
        CloudPreset("moustache_handlebar", CloudGroup.FACIAL_HAIR, "Twirled moustache", "Add a thick moustache with upward-twirled ends in a classic Indian style, matching the natural hair colour. Chin and cheeks clean-shaven."),
        CloudPreset("stubble", CloudGroup.FACIAL_HAIR, "Stubble", "Add light, even three-day stubble across the jaw, chin and upper lip, matching the natural hair colour."),

        // ---- hair
        CloudPreset("hair_short", CloudGroup.HAIR, "Shorter", "Make the hair clearly shorter in a neat, natural cut. Keep the hair colour and texture."),
        CloudPreset("hair_long", CloudGroup.HAIR, "Longer", "Make the hair clearly longer, falling naturally past the shoulders. Keep the hair colour and texture."),
        CloudPreset("hair_style_fade", CloudGroup.HAIR, "Side fade", "Change the hairstyle to a modern short cut with faded sides and a textured top. Keep the hair colour."),
        CloudPreset("hair_style_waves", CloudGroup.HAIR, "Soft waves", "Change the hairstyle to soft, loose waves with natural volume. Keep the hair colour and approximate length."),
        CloudPreset("hair_style_bun", CloudGroup.HAIR, "Bun", "Change the hairstyle to a neat bun tied at the back of the head. Keep the hair colour."),
        CloudPreset("hair_style_curly", CloudGroup.HAIR, "Curls", "Change the hairstyle to defined natural curls. Keep the hair colour and approximate length."),
        CloudPreset("hair_style_straight", CloudGroup.HAIR, "Sleek straight", "Change the hairstyle to sleek, straight hair. Keep the hair colour and approximate length."),
        CloudPreset("hair_color_black", CloudGroup.HAIR, "Jet black", "Change the hair colour to natural jet black. Keep the hairstyle and length exactly."),
        CloudPreset("hair_color_brown", CloudGroup.HAIR, "Chestnut brown", "Change the hair colour to natural chestnut brown. Keep the hairstyle and length exactly."),
        CloudPreset("hair_color_highlights", CloudGroup.HAIR, "Highlights", "Add subtle caramel highlights to the hair. Keep the hairstyle and length exactly."),
        CloudPreset("hairline", CloudGroup.HAIR, "Fuller hairline", "Restore a fuller, lower and natural-looking hairline with denser hair at the temples. Keep the hairstyle and colour."),
        CloudPreset("hair_volume", CloudGroup.HAIR, "More volume", "Make the hair visibly thicker and fuller with more volume. Keep the hairstyle, length and colour."),
        CloudPreset("grey_cover", CloudGroup.HAIR, "Cover grey", "Recolour all grey and white hair (including in any beard or moustache) to the person's natural darker hair colour. Keep the hairstyle exactly."),
        CloudPreset("bald", CloudGroup.HAIR, "Bald preview", "Show the person completely bald with a smooth, natural scalp that matches their skin tone. Eyebrows and any facial hair stay as they are."),

        // ---- age
        CloudPreset("age_younger", CloudGroup.AGE, "Younger", "Make the person look about 15 years younger: smoother skin, fewer wrinkles, fuller face and hair. They must still be clearly recognisable as the same person.", allows = "apparent age"),
        CloudPreset("age_older", CloudGroup.AGE, "Older", "Make the person look about 25 years older: natural wrinkles, slightly sagging skin and greying hair. They must still be clearly recognisable as the same person.", allows = "apparent age and hair greyness"),

        // ---- expression
        CloudPreset("expr_smile", CloudGroup.EXPRESSION, "Smile", "Change the expression to a warm, natural smile showing a little teeth, with matching eyes and cheeks.", allows = "facial expression"),
        CloudPreset("expr_neutral", CloudGroup.EXPRESSION, "Neutral", "Change the expression to a calm, neutral, relaxed face with the mouth closed.", allows = "facial expression"),
        CloudPreset("pose_turn", CloudGroup.EXPRESSION, "Slight turn", "Turn the head slightly (about 15 degrees) towards the camera's left, as a natural small change in head pose.", allows = "head pose"),

        // ---- accessories
        CloudPreset("glasses_add", CloudGroup.ACCESSORIES, "Add glasses", "Add stylish, thin-framed prescription glasses that fit the face naturally, with realistic lens reflections."),
        CloudPreset("glasses_remove", CloudGroup.ACCESSORIES, "Remove glasses", "Remove the glasses completely and reconstruct the eyes and skin behind them naturally."),
        CloudPreset("turban", CloudGroup.ACCESSORIES, "Turban / pagdi", "Add a neatly tied traditional Indian turban (pagdi) in a rich colour on the head.", allows = "the hair covered by the turban"),
        CloudPreset("jewellery", CloudGroup.ACCESSORIES, "Jewellery set", "Add an elegant traditional gold jewellery set: earrings, a necklace and a small maang tikka on the forehead.", allows = "the neckline area where the necklace sits"),

        // ---- complete looks
        CloudPreset("look_wedding", CloudGroup.LOOKS, "Wedding", "Give the person a complete Indian wedding look: elegant traditional wedding attire, matching jewellery or safa as appropriate, refined grooming and festive styling.", allows = "clothing, accessories and grooming"),
        CloudPreset("look_office", CloudGroup.LOOKS, "Office", "Give the person a polished professional office look: smart formal attire, neat hair and subtle, natural grooming.", allows = "clothing, hair neatness and grooming"),
        CloudPreset("look_party", CloudGroup.LOOKS, "Party", "Give the person a glamorous evening party look: stylish party outfit, styled hair and confident, polished grooming.", allows = "clothing, hairstyle and grooming"),
        CloudPreset("look_festive", CloudGroup.LOOKS, "Festive", "Give the person a festive Indian look: colourful traditional festive attire with tasteful accessories and fresh, bright grooming.", allows = "clothing, accessories and grooming"),
        CloudPreset("look_passport", CloudGroup.LOOKS, "Passport photo", "Turn this into a compliant passport photo: head and shoulders centred, facing the camera, neutral expression with mouth closed, even lighting without shadows, plain white background, simple collared clothing.", allows = "background (plain white), framing, lighting evenness, expression and clothing"),
    )

    fun byId(id: String): CloudPreset? = ALL.firstOrNull { it.id == id }
    fun of(group: CloudGroup): List<CloudPreset> = ALL.filter { it.group == group }
    fun of(category: Category): List<CloudPreset> = ALL.filter { it.group.category == category }
}
