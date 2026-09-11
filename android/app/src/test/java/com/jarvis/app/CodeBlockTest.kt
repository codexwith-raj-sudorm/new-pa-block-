package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class CodeBlockTest {
    @Test fun plainTextIsOneSeg() {
        val segs = splitCodeBlocks("hello world")
        assertEquals(1, segs.size)
        assertFalse(segs[0].isCode)
        assertEquals("hello world", segs[0].text)
    }

    @Test fun fencedBlockSplits() {
        val segs = splitCodeBlocks("try this:\n```python\nprint(1)\n```\ndone")
        assertEquals(3, segs.size)
        assertFalse(segs[0].isCode)
        assertTrue(segs[1].isCode)
        assertEquals("python", segs[1].lang)
        assertEquals("print(1)", segs[1].text)
        assertFalse(segs[2].isCode)
    }

    @Test fun blankLangDefaults() {
        val segs = splitCodeBlocks("```\nx = 1\n```")
        assertEquals(1, segs.size)
        assertTrue(segs[0].isCode)
        assertEquals("code", segs[0].lang)
    }

    @Test fun unclosedFenceStaysProse() {
        val segs = splitCodeBlocks("oops ```python\nprint(1)")
        assertEquals(1, segs.size)
        assertFalse(segs[0].isCode)
    }

    @Test fun speechSkipsCode() {
        val s = cleanForSpeech("Here:\n```js\nlet x = 1;\n```\nbye")
        assertFalse(s.contains("let x"))
        assertTrue(s.contains("code snippet"))
    }
}
