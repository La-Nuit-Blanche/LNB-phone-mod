package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * Plays a [CaptureScript] in an unattended client and quits, for screenshots taken the same way every time.
 *
 * Off unless the JVM has `-Dlnbphone.capture=<scenario>` (the demo's `runCapture` task sets it). Shots go to
 * `-Dlnbphone.capture.dir`, or `<game dir>/captures/<scenario>/`. A watchdog kills the client after
 * `-Dlnbphone.capture.timeout` seconds (default 420) so a stuck run never stays open on the desktop.
 */
class CaptureRunner private constructor(private val scenario: String, private val script: CaptureScript, private val outDir: Path) {
    private var step = 0
    private var started = false
    private var shots = 0

    private fun onTick() {
        val mc = Minecraft.getInstance()
        if (!started) {
            // First launch of a fresh game directory: skip the accessibility screen instead of waiting on it.
            if (mc.screen is AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false
                mc.options.save()
                mc.setScreen(TitleScreen(true))
                return
            }
            if (mc.overlay != null || mc.screen !is TitleScreen) return
            prepareOptions(mc)
            started = true
            LnbPhone.LOGGER.info("Capture '{}' started, {} steps, shots in {}", scenario, script.steps.size, outDir)
        }
        if (step >= script.steps.size) return
        if (script.steps[step].tick(this)) step++
        if (step == script.steps.size) {
            LnbPhone.LOGGER.info("Capture '{}' finished: {} shots in {}", scenario, shots, outDir)
            mc.stop()
        }
    }

    /** Settings that would get in the way of an unattended run; in memory only, never saved. */
    private fun prepareOptions(mc: Minecraft) {
        mc.options.pauseOnLostFocus = false
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0)
        // The movement tutorial's toast would sit over the top-right corner of every in-world shot.
        mc.options.tutorialStep = TutorialSteps.NONE
    }

    internal fun shoot(name: String) {
        val mc = Minecraft.getInstance()
        Files.createDirectories(outDir)
        val file = outDir.resolve("$name.png")
        Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(file) }
        shots++
        LnbPhone.LOGGER.info("Capture shot {} ({}x{}, GUI scale {})", file, mc.window.width, mc.window.height, mc.window.guiScale)
    }

    @OptIn(ExperimentalPathApi::class)
    internal fun createWorld(name: String, gameType: GameType) {
        val mc = Minecraft.getInstance()
        val saves = mc.gameDirectory.toPath().resolve("saves").resolve(name)
        if (saves.exists()) saves.deleteRecursively()
        val settings = LevelSettings(name, gameType, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT)
        mc.createWorldOpenFlows().createFreshLevel(name, settings, WorldOptions(0L, false, false), { registries ->
            registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
        }, TitleScreen())
    }

    companion object {
        fun startFromSystemProperties() {
            val scenario = System.getProperty("lnbphone.capture")?.takeIf { it.isNotBlank() } ?: return
            val script = CaptureScenarios.get(scenario)
            if (script == null) {
                LnbPhone.LOGGER.error("Unknown capture scenario '{}'; known: {}", scenario, CaptureScenarios.names())
                return
            }
            val outDir = System.getProperty("lnbphone.capture.dir")?.takeIf { it.isNotBlank() }?.let(Path::of)
                ?: Path.of("captures", scenario)
            val runner = CaptureRunner(scenario, script, outDir.absolute())
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) { runner.onTick() }
            startWatchdog(System.getProperty("lnbphone.capture.timeout")?.toLongOrNull() ?: 420L)
        }

        private fun startWatchdog(seconds: Long) {
            Thread({
                Thread.sleep(seconds * 1000)
                LnbPhone.LOGGER.error("Capture still running after {} s: killing the client", seconds)
                Runtime.getRuntime().halt(3)
            }, "lnbphone-capture-watchdog").apply {
                isDaemon = true
                start()
            }
        }
    }
}
