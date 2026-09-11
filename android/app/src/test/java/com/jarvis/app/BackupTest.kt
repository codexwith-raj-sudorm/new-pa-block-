package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTrip() {
        val chats = listOf(
            ChatData(
                "c1", "Hello",
                mutableListOf(Triple("user", "hi", 100L), Triple("model", "hey", 200L))
            )
        )
        val json = buildBackup(
            chats, listOf("likes chai"),
            listOf(TodoItem("milk", false), TodoItem("gym", true)),
            listOf("note1"), listOf(HookAction("lamp", "http://x", "GET")),
            listOf(ReminderItem(1, 999L, "stretch"))
        )
        val o = org.json.JSONObject(json)
        assertEquals("jarvis", o.getString("app"))
        assertEquals(1, o.getJSONArray("chats").length())
        assertEquals("Hello", o.getJSONArray("chats").getJSONObject(0).getString("title"))
        assertEquals(2, o.getJSONArray("chats").getJSONObject(0).getJSONArray("msgs").length())
        assertEquals(1, o.getJSONArray("facts").length())
        assertEquals(2, o.getJSONArray("todos").length())
        assertEquals(true, o.getJSONArray("todos").getJSONObject(1).getBoolean("done"))
        assertEquals(1, o.getJSONArray("notes").length())
        assertEquals("lamp", o.getJSONArray("hooks").getJSONObject(0).getString("n"))
        assertEquals(999L, o.getJSONArray("reminders").getJSONObject(0).getLong("at"))
    }

    @Test fun emptySafe() {
        val o = org.json.JSONObject(
            buildBackup(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        )
        assertEquals(0, o.getJSONArray("chats").length())
    }
}
