package fr.bde_teknantes.lnb.phone.demo.client.capture

import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.level.GameType
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture

/**
 * A list of steps an unattended client plays to take screenshots: set the GUI scale, open a screen, wait, shoot,
 * enter a throwaway world. Build one in [CaptureScenarios]; [CaptureRunner] plays it from the title screen, one step
 * per client tick, and quits at the end.
 */
class CaptureScript {
    internal fun interface Step {
        /** Advances the step by one tick; true once it is done. */
        fun tick(runner: CaptureRunner): Boolean
    }

    internal val steps = ArrayList<Step>()

    /** Sets the GUI scale (the window must be large enough for it, see the `capture` run). */
    fun guiScale(scale: Int) = apply {
        steps += Step {
            val mc = Minecraft.getInstance()
            mc.options.guiScale().set(scale)
            mc.resizeDisplay()
            true
        }
    }

    /** Opens the screen [factory] makes (null closes the current one). */
    fun screen(factory: () -> Screen?) = apply {
        steps += Step {
            Minecraft.getInstance().setScreen(factory())
            true
        }
    }

    /** Waits [ticks] client ticks (20 per second), letting several frames render. */
    fun waitTicks(ticks: Int) = apply {
        var left = ticks
        steps += Step { --left <= 0 }
    }

    /** Waits until [condition] holds. The runner's time limit still applies. */
    fun waitUntil(condition: () -> Boolean) = apply {
        steps += Step { condition() }
    }

    /** Saves the last rendered frame as `<name>.png` in the output directory. */
    fun shot(name: String) = apply {
        steps += Step { runner ->
            runner.shoot(name)
            true
        }
    }

    /** Reloads resource packs (as F3+T does) and waits until the loading overlay is gone. */
    fun reloadResources() = apply {
        var reload: CompletableFuture<Void>? = null
        steps += Step {
            val mc = Minecraft.getInstance()
            val future = reload ?: mc.reloadResourcePacks().also { reload = it }
            future.isDone && mc.overlay == null
        }
    }

    /**
     * Creates a fresh flat, peaceful world named [name] (deleting any previous one of that name) and waits until the
     * player stands in it with the loading screen gone. Survival by default, so the HUD shows health and hunger.
     */
    fun enterWorld(name: String = "lnbphone-capture", gameType: GameType = GameType.SURVIVAL) = apply {
        var started = false
        steps += Step { runner ->
            if (!started) {
                started = true
                runner.createWorld(name, gameType)
            }
            val mc = Minecraft.getInstance()
            mc.level != null && mc.player != null && mc.screen == null
        }
    }

    /** Runs [action] once, on the render thread. */
    fun run(action: () -> Unit) = apply {
        steps += Step {
            action()
            true
        }
    }

    /** Switches the game's language (`en_us`, `fr_fr`...) and reloads resources, as the language screen does. */
    fun language(code: String) = apply {
        run {
            val mc = Minecraft.getInstance()
            mc.options.languageCode = code
            mc.languageManager.setSelected(code)
        }
        reloadResources()
    }

    // ---- Input, through the same handlers GLFW's callbacks call ----

    /** Presses [key] (a GLFW key code) on this tick and releases it on the next one, as a player would. */
    fun key(key: Int, modifiers: Int = 0) = apply {
        steps += Step {
            val mc = Minecraft.getInstance()
            mc.keyboardHandler.keyPress(mc.window.window, key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_PRESS, modifiers)
            true
        }
        steps += Step {
            val mc = Minecraft.getInstance()
            mc.keyboardHandler.keyPress(mc.window.window, key, GLFW.glfwGetKeyScancode(key), GLFW.GLFW_RELEASE, modifiers)
            true
        }
    }

    /**
     * Moves the pointer to the middle of the GUI pixel [position] gives, window GUI px, computed when the step runs
     * (the phone may have moved since the script was built).
     */
    fun mouseTo(position: () -> Pair<Int, Int>) = apply {
        steps += Step {
            val mc = Minecraft.getInstance()
            val (x, y) = position()
            val scale = mc.window.screenWidth.toDouble() / mc.window.guiScaledWidth
            ON_MOVE.invoke(mc.mouseHandler, mc.window.window, (x + 0.5) * scale, (y + 0.5) * scale)
            true
        }
    }

    fun mouseDown(button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) = mouseButton(button, GLFW.GLFW_PRESS)

    fun mouseUp(button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) = mouseButton(button, GLFW.GLFW_RELEASE)

    /** Turns the mouse wheel by [notches] where the pointer is (positive away from the player). */
    fun scroll(notches: Double) = apply {
        steps += Step {
            val mc = Minecraft.getInstance()
            ON_SCROLL.invoke(mc.mouseHandler, mc.window.window, 0.0, notches)
            true
        }
    }

    private fun mouseButton(button: Int, action: Int) = apply {
        steps += Step {
            val mc = Minecraft.getInstance()
            ON_PRESS.invoke(mc.mouseHandler, mc.window.window, button, action, 0)
            true
        }
    }

    private companion object {
        // MouseHandler's GLFW callbacks are private; the game runs with Mojang's names, in development and in production.
        val ON_MOVE: Method = MouseHandler::class.java.getDeclaredMethod(
            "onMove", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val ON_PRESS: Method = MouseHandler::class.java.getDeclaredMethod(
            "onPress", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val ON_SCROLL: Method = MouseHandler::class.java.getDeclaredMethod(
            "onScroll", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }
}
