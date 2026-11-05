package com.flowpilot.automation.data

import com.flowpilot.automation.util.BlockSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSummaryTest {
    @Test
    fun tapByText_isHumanReadable() {
        val b = Block(type = BlockType.CLICK_BY_TEXT, params = mapOf("text" to "Login"))
        val s = BlockSummary.sentence(b)
        assertTrue(s.contains("Login"))
        assertTrue(s.startsWith("Tap"))
    }

    @Test
    fun ifImage_sentence() {
        val b = Block(
            type = BlockType.IF,
            params = mapOf("kind" to "image_found", "template" to "play.png")
        )
        val s = BlockSummary.sentence(b)
        assertTrue(s.contains("play.png"))
        assertTrue(s.contains("image"))
    }

    @Test
    fun openApp_withoutContext_showsPackage() {
        val b = Block(type = BlockType.OPEN_APP, params = mapOf("package" to "com.android.settings"))
        val s = BlockSummary.sentence(b)
        assertTrue(s.contains("com.android.settings") || s.contains("Open"))
    }
}
