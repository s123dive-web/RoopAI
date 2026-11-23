package com.suresh.roopai.cloud

import com.suresh.roopai.edit.Category
import com.suresh.roopai.engine.cloud.CloudGroup
import com.suresh.roopai.engine.cloud.CloudPresets
import com.suresh.roopai.engine.cloud.PromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun everyPresetPromptLocksIdentityFaceShapeSkinToneLightingAndBackground() {
        for (preset in CloudPresets.ALL) {
            val prompt = PromptBuilder.forPreset(preset)
            for (word in PromptBuilder.PRESERVED) assertTrue("${preset.id} must mention '$word'", prompt.contains(word, ignoreCase = true))
            assertTrue(preset.id, prompt.contains("Change only the requested attribute"))
            assertTrue(preset.id, prompt.contains("same person", ignoreCase = true))
            assertTrue(preset.id, prompt.endsWith("Requested change: " + preset.instruction))
        }
    }

    @Test
    fun customPromptCarriesTheSameLock() {
        val prompt = PromptBuilder.forCustom("  add a red   scarf\n")!!
        for (word in PromptBuilder.PRESERVED) assertTrue(word, prompt.contains(word))
        assertTrue(prompt.endsWith("Requested change: add a red scarf"))
        assertFalse("no exception clause for plain edits", prompt.contains("Exception:"))
    }

    @Test
    fun blankCustomPromptIsRejected() {
        assertNull(PromptBuilder.forCustom(""))
        assertNull(PromptBuilder.forCustom("  \n\t "))
    }

    @Test
    fun sanitizeStripsControlCharactersAndLimitsLength() {
        assertEquals("a b c", PromptBuilder.sanitize("a\u0000b\n\n  c"))
        assertEquals(PromptBuilder.MAX_CUSTOM_LENGTH, PromptBuilder.sanitize("x".repeat(2000)).length)
    }

    @Test
    fun exceptionClauseOnlyWhenThePresetAllowsExtraChanges() {
        val passport = PromptBuilder.forPreset(CloudPresets.byId("look_passport")!!)
        assertTrue(passport.contains("Exception: this edit may alter background (plain white)"))
        assertTrue("still locks identity", passport.contains("identity"))
        val shave = PromptBuilder.forPreset(CloudPresets.byId("beard_remove")!!)
        assertFalse(shave.contains("Exception:"))
    }

    @Test
    fun presetIdsAreUniqueAndCatalogueCoversTheBrief() {
        assertEquals(CloudPresets.ALL.size, CloudPresets.ALL.map { it.id }.toSet().size)
        val required = listOf(
            // facial hair
            "beard_remove", "beard_full", "moustache", "stubble",
            // hair
            "hair_short", "hair_long", "hair_style_fade", "hair_color_black", "hairline", "hair_volume", "grey_cover", "bald",
            // age, expression
            "age_younger", "age_older", "expr_smile", "expr_neutral", "pose_turn",
            // accessories
            "glasses_add", "glasses_remove", "turban", "jewellery",
            // looks
            "look_wedding", "look_office", "look_party", "look_festive", "look_passport",
        )
        for (id in required) assertNotNull(id, CloudPresets.byId(id))
        for (g in CloudGroup.entries) assertTrue(g.name, CloudPresets.of(g).isNotEmpty())
        assertNull(CloudPresets.byId(CloudPresets.CUSTOM_ID))
    }

    @Test
    fun cloudOnlyCategoriesHavePresetsOrFreeText() {
        assertTrue(CloudPresets.of(Category.FACIAL_HAIR).size >= 4)
        assertTrue(CloudPresets.of(Category.LOOKS).size == 5)
        assertTrue(CloudPresets.of(Category.HAIR).size >= 8)
        assertTrue(CloudPresets.of(Category.ACCESSORIES).size == 4)
        assertTrue(CloudPresets.of(Category.FACE).isNotEmpty())
    }
}
