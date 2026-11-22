package fr.bde_teknantes.lnb.phone.client.shell

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import org.lwjgl.glfw.GLFW

/**
 * The phone's key: Up arrow by default, in its own category of the controls screen. In game only: keys used only
 * inside screens don't conflict with it.
 */
internal object PhoneKeys {
    val OPEN = KeyMapping("key.lnbphone.open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, "key.categories.lnbphone")

    fun register(event: RegisterKeyMappingsEvent) {
        event.register(OPEN)
    }

    /**
     * The phone key pressed while the game has the mouse. Read from the input event rather than the mapping's click
     * count: the mapping counts a click for every key repeat, so holding the key would open and close the phone over
     * and over. Only the press itself counts.
     */
    fun onKey(event: InputEvent.Key) {
        if (event.action != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return
        if (OPEN.isActiveAndMatches(InputConstants.getKey(event.key, event.scanCode))) PhoneShell.phoneKeyPressedInGame()
    }

    // Before the screens see the click, so a click that closes the phone's screen isn't taken for a press in game.
    fun onMouseButton(event: InputEvent.MouseButton.Pre) {
        if (event.action != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return
        if (OPEN.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.button))) PhoneShell.phoneKeyPressedInGame()
    }

    /** The mapping's own clicks are never used (see [onKey]); emptied each tick so they don't pile up. */
    fun discardClicks() {
        while (OPEN.consumeClick()) Unit
    }

    /** Whether the key's modifier (Ctrl, Shift, Alt, or none) is held, as for a press of the phone key. */
    fun modifierHeld(): Boolean = OPEN.keyModifier.isActive(OPEN.keyConflictContext)

    /** Whether the key bound to the phone is held down right now, whatever has the mouse. */
    fun isPhysicallyDown(): Boolean {
        val key = OPEN.key
        val window = Minecraft.getInstance().window.window
        return when (key.type) {
            InputConstants.Type.KEYSYM -> key.value != InputConstants.UNKNOWN.value && InputConstants.isKeyDown(window, key.value)
            InputConstants.Type.MOUSE -> GLFW.glfwGetMouseButton(window, key.value) == GLFW.GLFW_PRESS
            else -> false
        }
    }
}
