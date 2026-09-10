package com.jarvis.app

/**
 * Todo + notes parsing. 100% JVM-pure (no android imports) so it is
 * unit-tested (see ListsTest). The ViewModel executes the command.
 *
 * Understood (case-insensitive):
 * - "add milk to my shopping list" / "todo buy eggs"
 * - "my todos" / "shopping list" / "done 2" / "remove todo 1"
 * - "note call bank tomorrow" / "my notes" / "delete note 3"
 */
data class TodoItem(val text: String, val done: Boolean)

sealed interface ListCommand
data class AddTodo(val text: String) : ListCommand
data class DoneTodo(val index: Int) : ListCommand
data class RemoveTodo(val index: Int) : ListCommand
object ShowTodos : ListCommand
data class AddNote(val text: String) : ListCommand
data class RemoveNote(val index: Int) : ListCommand
object ShowNotes : ListCommand

fun parseListCommand(raw: String): ListCommand? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val low = t.lowercase()

    Regex("""^(add|put)\s+(.+?)\s+to\s+(?:my\s+|the\s+)?(?:shopping\s+|todo\s+|to-do\s+|task\s+)?list$""")
        .find(low)?.let {
            val item = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
            if (item.isNotEmpty()) return AddTodo(item)
        }
    Regex("""^(?:todo|task)\s+(.+)$""").find(t)?.let {
        val item = it.groupValues[1].trim().trimEnd('?', '.', '!').trim()
        if (item.isNotEmpty()) return AddTodo(item)
    }
    Regex("""^(?:done|complete|finish|check off|tick off)\s+(?:todo\s+|task\s+|item\s+)?(\d+)$""")
        .find(low)?.let { return DoneTodo(it.groupValues[1].toInt()) }
    Regex("""^(?:remove|delete)\s+(?:todo\s+|task\s+|item\s+)?(\d+)$""")
        .find(low)?.let { return RemoveTodo(it.groupValues[1].toInt()) }
    if (Regex("""^(?:my\s+|the\s+|show\s+|list\s+)*(?:todos?|to-dos?|tasks?|shopping(?:\s+list)?|grocery(?:\s+list)?|lists?)$""")
        .matches(low)
    ) return ShowTodos

    Regex("""^(?:note|jot down|write down)\s+(.+)$""").find(t)?.let {
        val item = it.groupValues[1].trim().trimEnd('?', '.', '!').trim()
        if (item.isNotEmpty()) return AddNote(item)
    }
    Regex("""^(?:remove|delete)\s+note\s+(\d+)$""")
        .find(low)?.let { return RemoveNote(it.groupValues[1].toInt()) }
    if (Regex("""^(?:my\s+|show\s+(?:my\s+)?|list\s+(?:my\s+)?)?notes?$""").matches(low)) return ShowNotes

    return null
}
