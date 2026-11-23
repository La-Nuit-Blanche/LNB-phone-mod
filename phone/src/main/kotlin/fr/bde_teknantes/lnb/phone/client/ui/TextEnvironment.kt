package fr.bde_teknantes.lnb.phone.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * What text fields read from the game besides their input events: the clipboard, whether Shift is held (a click with
 * Shift extends the selection) and whether keys follow the Mac's conventions. [current] is the game; tests put a fake
 * one there to drive fields without a window.
 */
internal interface TextEnvironment {
    var clipboard: String
    val shiftDown: Boolean
    val mac: Boolean

    object Game : TextEnvironment {
        override var clipboard: String
            get() = Minecraft.getInstance().keyboardHandler.clipboard
            set(value) {
                Minecraft.getInstance().keyboardHandler.clipboard = value
            }

        override val shiftDown: Boolean get() = Screen.hasShiftDown()
        override val mac: Boolean get() = Minecraft.ON_OSX
    }

    companion object {
        @Volatile
        var current: TextEnvironment = Game
    }
}
