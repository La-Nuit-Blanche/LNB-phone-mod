package fr.bde_teknantes.lnb.phone.client.ui

import org.lwjgl.glfw.GLFW

/** What a key does in a text field. */
internal enum class EditCommand {
    LEFT, RIGHT, UP, DOWN, LINE_START, LINE_END, TEXT_START, TEXT_END,
    DELETE_BACKWARD, DELETE_FORWARD, SELECT_ALL, COPY, CUT, PASTE, NEWLINE, SUBMIT,
}

/** A key press read as a text-editing command, with its modifiers. */
internal data class EditKey(val command: EditCommand, val select: Boolean = false, val word: Boolean = false)

/**
 * Turns key presses into editing commands, the way text fields do on the player's system. Plain logic over GLFW codes,
 * so it can be tested: [mac] is Minecraft's `ON_OSX`, where Cmd plays Ctrl's part and Alt moves by words.
 */
internal object TextKeys {
    fun read(keyCode: Int, modifiers: Int, multiline: Boolean, mac: Boolean): EditKey? {
        val shift = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val ctrl = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        val alt = modifiers and GLFW.GLFW_MOD_ALT != 0
        val cmd = modifiers and GLFW.GLFW_MOD_SUPER != 0
        // The shortcut key; with Alt too it is AltGr on Windows, which types characters instead.
        val shortcut = if (mac) cmd else ctrl && !alt
        val word = if (mac) alt else ctrl
        return when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> if (mac && cmd) EditKey(EditCommand.LINE_START, shift) else EditKey(EditCommand.LEFT, shift, word)
            GLFW.GLFW_KEY_RIGHT -> if (mac && cmd) EditKey(EditCommand.LINE_END, shift) else EditKey(EditCommand.RIGHT, shift, word)
            GLFW.GLFW_KEY_UP -> if (multiline && !(mac && cmd)) EditKey(EditCommand.UP, shift) else EditKey(EditCommand.TEXT_START, shift)
            GLFW.GLFW_KEY_DOWN -> if (multiline && !(mac && cmd)) EditKey(EditCommand.DOWN, shift) else EditKey(EditCommand.TEXT_END, shift)
            GLFW.GLFW_KEY_HOME -> EditKey(if (ctrl || !multiline) EditCommand.TEXT_START else EditCommand.LINE_START, shift)
            GLFW.GLFW_KEY_END -> EditKey(if (ctrl || !multiline) EditCommand.TEXT_END else EditCommand.LINE_END, shift)
            GLFW.GLFW_KEY_BACKSPACE -> EditKey(EditCommand.DELETE_BACKWARD, word = word)
            GLFW.GLFW_KEY_DELETE -> EditKey(EditCommand.DELETE_FORWARD, word = word)
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> when {
                // Ctrl+Enter sends (art direction 2.3); a plain Enter starts a new line in a text area.
                shortcut || ctrl || !multiline -> EditKey(EditCommand.SUBMIT)
                else -> EditKey(EditCommand.NEWLINE)
            }
            GLFW.GLFW_KEY_A -> if (shortcut && !shift) EditKey(EditCommand.SELECT_ALL) else null
            GLFW.GLFW_KEY_C -> if (shortcut && !shift) EditKey(EditCommand.COPY) else null
            GLFW.GLFW_KEY_X -> if (shortcut && !shift) EditKey(EditCommand.CUT) else null
            GLFW.GLFW_KEY_V -> if (shortcut && !shift) EditKey(EditCommand.PASTE) else null
            else -> null
        }
    }
}
