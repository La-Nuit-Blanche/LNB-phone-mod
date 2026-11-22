package fr.bde_teknantes.lnb.phone.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_C
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_END
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP
import org.lwjgl.glfw.GLFW.GLFW_KEY_V
import org.lwjgl.glfw.GLFW.GLFW_KEY_X
import org.lwjgl.glfw.GLFW.GLFW_KEY_Z
import org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
import org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

class TextKeysTest {
    private fun pc(key: Int, mods: Int = 0, multiline: Boolean = true) = TextKeys.read(key, mods, multiline, mac = false)
    private fun mac(key: Int, mods: Int = 0, multiline: Boolean = true) = TextKeys.read(key, mods, multiline, mac = true)

    @Test
    fun `arrows, with shift selecting and ctrl moving by words`() {
        assertEquals(EditKey(EditCommand.LEFT), pc(GLFW_KEY_LEFT))
        assertEquals(EditKey(EditCommand.RIGHT, select = true), pc(GLFW_KEY_RIGHT, GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.LEFT, select = true, word = true), pc(GLFW_KEY_LEFT, GLFW_MOD_SHIFT or GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.RIGHT, word = true), pc(GLFW_KEY_RIGHT, GLFW_MOD_CONTROL))
    }

    @Test
    fun `up and down move between lines in a text area, to the ends in a field`() {
        assertEquals(EditKey(EditCommand.UP), pc(GLFW_KEY_UP))
        assertEquals(EditKey(EditCommand.DOWN, select = true), pc(GLFW_KEY_DOWN, GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.TEXT_START), pc(GLFW_KEY_UP, multiline = false))
        assertEquals(EditKey(EditCommand.TEXT_END, select = true), pc(GLFW_KEY_DOWN, GLFW_MOD_SHIFT, multiline = false))
    }

    @Test
    fun `home and end go to the line's ends, with ctrl (or in a field) to the text's`() {
        assertEquals(EditKey(EditCommand.LINE_START), pc(GLFW_KEY_HOME))
        assertEquals(EditKey(EditCommand.LINE_END, select = true), pc(GLFW_KEY_END, GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.TEXT_START), pc(GLFW_KEY_HOME, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.TEXT_END, select = true), pc(GLFW_KEY_END, GLFW_MOD_CONTROL or GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.TEXT_START), pc(GLFW_KEY_HOME, multiline = false))
        assertEquals(EditKey(EditCommand.TEXT_END), pc(GLFW_KEY_END, multiline = false))
    }

    @Test
    fun `backspace and delete, by words with ctrl, shift ignored`() {
        assertEquals(EditKey(EditCommand.DELETE_BACKWARD), pc(GLFW_KEY_BACKSPACE))
        assertEquals(EditKey(EditCommand.DELETE_BACKWARD), pc(GLFW_KEY_BACKSPACE, GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.DELETE_BACKWARD, word = true), pc(GLFW_KEY_BACKSPACE, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.DELETE_FORWARD, word = true), pc(GLFW_KEY_DELETE, GLFW_MOD_CONTROL))
    }

    @Test
    fun `enter starts a line in a text area and submits a field, ctrl+enter always submits`() {
        assertEquals(EditKey(EditCommand.NEWLINE), pc(GLFW_KEY_ENTER))
        assertEquals(EditKey(EditCommand.NEWLINE), pc(GLFW_KEY_ENTER, GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.NEWLINE), pc(GLFW_KEY_KP_ENTER))
        assertEquals(EditKey(EditCommand.SUBMIT), pc(GLFW_KEY_ENTER, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.SUBMIT), pc(GLFW_KEY_KP_ENTER, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.SUBMIT), pc(GLFW_KEY_ENTER, multiline = false))
    }

    @Test
    fun `clipboard and select all with ctrl, not with ctrl+shift nor without ctrl`() {
        assertEquals(EditKey(EditCommand.SELECT_ALL), pc(GLFW_KEY_A, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.COPY), pc(GLFW_KEY_C, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.CUT), pc(GLFW_KEY_X, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.PASTE), pc(GLFW_KEY_V, GLFW_MOD_CONTROL))
        assertNull(pc(GLFW_KEY_V, GLFW_MOD_CONTROL or GLFW_MOD_SHIFT))
        assertNull(pc(GLFW_KEY_A))
        assertNull(pc(GLFW_KEY_V, GLFW_MOD_SUPER))
    }

    @Test
    fun `AltGr (ctrl and alt on Windows) types, it is no shortcut`() {
        // AltGr+C or AltGr+V can produce characters on some layouts: they must not copy or paste.
        assertNull(pc(GLFW_KEY_C, GLFW_MOD_CONTROL or GLFW_MOD_ALT))
        assertNull(pc(GLFW_KEY_V, GLFW_MOD_CONTROL or GLFW_MOD_ALT))
    }

    @Test
    fun `keys that edit nothing are left to others`() {
        assertNull(pc(GLFW_KEY_ESCAPE))
        assertNull(pc(GLFW_KEY_TAB))
        // No undo exists: Ctrl+Z does nothing in a field.
        assertNull(pc(GLFW_KEY_Z, GLFW_MOD_CONTROL))
    }

    @Test
    fun `on a Mac, cmd is the shortcut key and alt moves by words`() {
        assertEquals(EditKey(EditCommand.PASTE), mac(GLFW_KEY_V, GLFW_MOD_SUPER))
        assertNull(mac(GLFW_KEY_V, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.LEFT, word = true), mac(GLFW_KEY_LEFT, GLFW_MOD_ALT))
        assertEquals(EditKey(EditCommand.LEFT), mac(GLFW_KEY_LEFT, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.DELETE_BACKWARD, word = true), mac(GLFW_KEY_BACKSPACE, GLFW_MOD_ALT))
    }

    @Test
    fun `on a Mac, cmd with the arrows goes to the ends of the line or text`() {
        assertEquals(EditKey(EditCommand.LINE_START), mac(GLFW_KEY_LEFT, GLFW_MOD_SUPER))
        assertEquals(EditKey(EditCommand.LINE_END, select = true), mac(GLFW_KEY_RIGHT, GLFW_MOD_SUPER or GLFW_MOD_SHIFT))
        assertEquals(EditKey(EditCommand.TEXT_START), mac(GLFW_KEY_UP, GLFW_MOD_SUPER))
        assertEquals(EditKey(EditCommand.TEXT_END), mac(GLFW_KEY_DOWN, GLFW_MOD_SUPER))
        assertEquals(EditKey(EditCommand.UP), mac(GLFW_KEY_UP))
    }

    @Test
    fun `on a Mac, cmd+enter submits a text area and so does ctrl+enter`() {
        assertEquals(EditKey(EditCommand.SUBMIT), mac(GLFW_KEY_ENTER, GLFW_MOD_SUPER))
        assertEquals(EditKey(EditCommand.SUBMIT), mac(GLFW_KEY_ENTER, GLFW_MOD_CONTROL))
        assertEquals(EditKey(EditCommand.NEWLINE), mac(GLFW_KEY_ENTER))
    }
}
