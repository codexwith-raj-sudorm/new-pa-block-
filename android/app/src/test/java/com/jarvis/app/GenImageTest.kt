package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenImageTest {

    @Test
    fun matcherAcceptsImageCommands() {
        assertEquals("image a red dragon", genImagePromptOf("generate an image of a red dragon"))
        assertEquals("logo for a coffee shop", genImagePromptOf("draw me a logo for a coffee shop"))
        assertEquals("image a cat", genImagePromptOf("draw a cat"))
        assertEquals("image sunset over the sea", genImagePromptOf("paint sunset over the sea"))
        assertEquals("wallpaper mountain lake", genImagePromptOf("create a wallpaper mountain lake"))
        assertEquals("picture my dog as an astronaut", genImagePromptOf("please generate me a picture of my dog as an astronaut"))
    }

    @Test
    fun matcherRejectsLookalikes() {
        assertNull(genImagePromptOf("make a call to mom"))
        assertNull(genImagePromptOf("create a reminder to stretch"))
        assertNull(genImagePromptOf("create a smart action"))
        assertNull(genImagePromptOf("take a picture"))
        assertNull(genImagePromptOf("generate image"))
        assertNull(genImagePromptOf("what time is it"))
    }

    @Test
    fun requestBodyAsksForImage() {
        val b = genImageRequestBody("image a red dragon")
        assertTrue(b.contains("responseModalities"))
        assertTrue(b.contains("IMAGE"))
        assertTrue(b.contains("image a red dragon"))
    }

    @Test
    fun parserFindsInlineImage() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"here you go"},{"inlineData":{"mimeType":"image/png","data":"aGVsbG8="}}]}}]}"""
        val got = parseGenImageData(json)
        assertNotNull(got)
        assertEquals("image/png", got!!.first)
        assertEquals("hello", String(got.second))
        assertNull(parseGenImageData("""{"candidates":[{"content":{"parts":[{"text":"no image"}]}}]}"""))
        assertNull(parseGenImageData("garbage"))
    }

    @Test
    fun imageModelsFirst() {
        val m = genImageModels(listOf("gemini-2.5-flash", "gemini-2.5-flash-image"))
        assertEquals("gemini-2.5-flash-image", m[0])
        assertEquals("gemini-2.0-flash-preview-image-generation", m[1])
        assertEquals(3, m.size)
    }
}
