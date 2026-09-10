package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsTest {
    @Test fun addTodoParse() {
        assertEquals(AddTodo("milk"), parseListCommand("add milk to my shopping list"))
        assertEquals(AddTodo("buy eggs"), parseListCommand("todo buy eggs"))
    }

    @Test fun doneRemoveParse() {
        assertEquals(DoneTodo(2), parseListCommand("done 2"))
        assertEquals(RemoveTodo(1), parseListCommand("remove todo 1"))
    }

    @Test fun showTodosParse() {
        assertTrue(parseListCommand("my todos") is ShowTodos)
        assertTrue(parseListCommand("shopping list") is ShowTodos)
    }

    @Test fun notesParse() {
        assertEquals(AddNote("call bank tomorrow"), parseListCommand("note call bank tomorrow"))
        assertTrue(parseListCommand("my notes") is ShowNotes)
        assertEquals(RemoveNote(3), parseListCommand("delete note 3"))
    }

    @Test fun garbageIsNull() {
        assertNull(parseListCommand("hello there"))
        assertNull(parseListCommand("remind me in 5 min to stretch"))
        assertNull(parseListCommand("what time is it"))
        assertNull(parseListCommand("open YouTube"))
    }

    @Test fun todoItemToggles() {
        assertEquals(true, TodoItem("milk", false).copy(done = true).done)
    }
}
