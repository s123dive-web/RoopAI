package com.suresh.roopai.edit

/** The ten editor categories from the brief plus Background and Adjust (basics). */
enum class Category(val title: String, val cloudOnly: Boolean = false) {
    SKIN("Skin"),
    FACE("Face"),
    EYES("Eyes"),
    LIPS("Lips"),
    HAIR("Hair"),
    FACIAL_HAIR("Facial Hair", cloudOnly = true),
    MAKEUP("Makeup"),
    ACCESSORIES("Accessories"),
    LOOKS("Looks", cloudOnly = true),
    AI_EDIT("AI Edit", cloudOnly = true),
    BACKGROUND("Background"),
    ADJUST("Adjust"),
}

/** What a local tool needs from the on-device analysis to do anything. */
enum class Needs { NOTHING, FACE, SEGMENTATION }

/**
 * Every offline tool. [bipolar] tools use -1..1 with 0 as neutral, the rest use 0..1 with 0 as
 * off. Enum names are persisted in saved edit stacks - never rename, only add.
 */
enum class Tool(
    val category: Category,
    val title: String,
    val needs: Needs = Needs.FACE,
    val bipolar: Boolean = false,
    val defaultAmount: Float = 0.5f,
    val palette: List<Int> = emptyList(),
    val variants: List<String> = emptyList(),
) {
    SKIN_SMOOTH(Category.SKIN, "Smooth"),
    SKIN_BLEMISH(Category.SKIN, "Blemish"),
    SKIN_TONE(Category.SKIN, "Even tone"),
    SKIN_DARK_CIRCLES(Category.SKIN, "Dark circles"),
    SKIN_WRINKLES(Category.SKIN, "Wrinkles"),

    FACE_SLIM(Category.FACE, "Slim", bipolar = true, defaultAmount = 0.4f),
    FACE_JAW(Category.FACE, "Jaw", bipolar = true, defaultAmount = 0.4f),
    FACE_CHIN(Category.FACE, "Chin", bipolar = true, defaultAmount = 0.4f),
    FACE_FOREHEAD(Category.FACE, "Forehead", bipolar = true, defaultAmount = 0.4f),
    NOSE_WIDTH(Category.FACE, "Nose width", bipolar = true, defaultAmount = -0.4f),

    EYE_BRIGHTEN(Category.EYES, "Brighten"),
    EYE_SIZE(Category.EYES, "Size", bipolar = true, defaultAmount = 0.4f),
    BROW_THICKNESS(Category.EYES, "Brow thickness", bipolar = true, defaultAmount = 0.4f),
    BROW_SHAPE(Category.EYES, "Brow arch", bipolar = true, defaultAmount = 0.4f),
    BROW_COLOR(Category.EYES, "Brow colour", palette = Palettes.BROWS),

    LIP_FULLNESS(Category.LIPS, "Fullness", bipolar = true, defaultAmount = 0.4f),
    TEETH_WHITEN(Category.LIPS, "Whiten teeth"),
    MAKEUP_LIPSTICK(Category.LIPS, "Lipstick", palette = Palettes.LIPS, defaultAmount = 0.6f),

    HAIR_TINT(Category.HAIR, "Colour tint", needs = Needs.SEGMENTATION, palette = Palettes.HAIR, defaultAmount = 0.6f),

    MAKEUP_FOUNDATION(Category.MAKEUP, "Foundation", palette = Palettes.FOUNDATION),
    MAKEUP_BLUSH(Category.MAKEUP, "Blush", palette = Palettes.BLUSH),
    MAKEUP_EYELINER(Category.MAKEUP, "Eyeliner", palette = Palettes.LINER, defaultAmount = 0.7f),
    MAKEUP_KAJAL(Category.MAKEUP, "Kajal", palette = Palettes.LINER, defaultAmount = 0.7f),
    MAKEUP_EYESHADOW(Category.MAKEUP, "Eyeshadow", palette = Palettes.SHADOW),

    ACC_BINDI(Category.ACCESSORIES, "Bindi", palette = Palettes.BINDI, variants = listOf("round", "teardrop", "long")),
    ACC_EARRINGS(Category.ACCESSORIES, "Earrings", palette = Palettes.JEWEL, variants = listOf("stud", "drop", "hoop", "jhumka")),
    ACC_NOSE_PIN(Category.ACCESSORIES, "Nose pin", palette = Palettes.JEWEL, variants = listOf("stud left", "stud right", "ring left", "ring right")),
    ACC_GLASSES(Category.ACCESSORIES, "Glasses", palette = Palettes.FRAMES, variants = listOf("round", "square", "aviator", "sunglasses")),
    ACC_CAP(Category.ACCESSORIES, "Cap", palette = Palettes.CAPS, variants = listOf("baseball", "beanie")),

    BG_BLUR(Category.BACKGROUND, "Blur", needs = Needs.SEGMENTATION),
    BG_REPLACE(
        Category.BACKGROUND, "Replace", needs = Needs.SEGMENTATION, defaultAmount = 1f,
        palette = Palettes.BACKDROP, variants = listOf("colour", "gradient", "studio", "photo"),
    ),

    FILTER(
        Category.ADJUST, "Filter", needs = Needs.NOTHING, defaultAmount = 0.8f,
        variants = listOf("mono", "noir", "sepia", "warm", "cool", "vivid", "fade", "rose", "golden", "teal"),
    ),
    ADJ_BRIGHTNESS(Category.ADJUST, "Brightness", needs = Needs.NOTHING, bipolar = true, defaultAmount = 0.2f),
    ADJ_CONTRAST(Category.ADJUST, "Contrast", needs = Needs.NOTHING, bipolar = true, defaultAmount = 0.2f),
    ADJ_SATURATION(Category.ADJUST, "Saturation", needs = Needs.NOTHING, bipolar = true, defaultAmount = 0.2f),
    ADJ_WARMTH(Category.ADJUST, "Warmth", needs = Needs.NOTHING, bipolar = true, defaultAmount = 0.2f),
    ;

    val hasColor get() = palette.isNotEmpty()
    val minAmount get() = if (bipolar) -1f else 0f

    companion object {
        fun of(category: Category): List<Tool> = entries.filter { it.category == category }
    }
}

object Palettes {
    private fun c(hex: Long) = (0xFF000000 or hex).toInt()
    val BROWS = listOf(c(0x1E1410), c(0x3B2A20), c(0x5A4030), c(0x7A5C44), c(0x9B8262), c(0x4A4A4E))
    val LIPS = listOf(c(0xB3202C), c(0xD23C5A), c(0xE0607E), c(0xA8433D), c(0x8E2E4C), c(0xC96A55), c(0x6E1F2C), c(0xE58A8A))
    val HAIR = listOf(c(0x1A1512), c(0x4B2E1E), c(0x7B4A2A), c(0xB5803E), c(0xD9B36A), c(0x8C2F1B), c(0xB0413E), c(0x5B3A8C), c(0x2F5FA8), c(0x2E8B6A), c(0xC0C0C8))
    val FOUNDATION = listOf(c(0xF6DCC6), c(0xEBC5A5), c(0xDDAE88), c(0xC99670), c(0xAE7B55), c(0x8C5E3C), c(0x6B452B))
    val BLUSH = listOf(c(0xE98A8F), c(0xF2A0A6), c(0xE2796A), c(0xD9608A), c(0xC8695B), c(0xB95068))
    val LINER = listOf(c(0x0C0C0E), c(0x2A1B12), c(0x1B2A55), c(0x1F4A3A), c(0x4B2A6A))
    val SHADOW = listOf(c(0x8C6A56), c(0xB98A6A), c(0x6A4A7A), c(0x3F5A8A), c(0x2E6A5A), c(0xC9A24A), c(0xB95A7A), c(0x3A3A40))
    val BINDI = listOf(c(0xC0121F), c(0x7A0E1A), c(0x101010), c(0xD4A017), c(0x1E7A46), c(0x243C9C))
    val JEWEL = listOf(c(0xE2B33C), c(0xD8D8DE), c(0xE8A0A8), c(0xC0121F), c(0x1E7A46), c(0x2A4FB8), c(0xF4F4F0))
    val FRAMES = listOf(c(0x101012), c(0x5A3A22), c(0xB8922E), c(0x9AA0A8), c(0x8A1C2C), c(0x1F3A6A))
    val CAPS = listOf(c(0x1C1C20), c(0xB5202A), c(0x1F3F8A), c(0x2E6A3E), c(0xE8E4DA), c(0xD98A1E))
    val BACKDROP = listOf(c(0xFFFFFF), c(0xE6EEF8), c(0xCFE3F5), c(0xF3E6D8), c(0xBFD8C2), c(0x2A2D3A), c(0x7A1F2E), c(0x101014))
}
