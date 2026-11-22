package fr.bde_teknantes.lnb.phone.demo.client.capture

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.level.GameType
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
}
