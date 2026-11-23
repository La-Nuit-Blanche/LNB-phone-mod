package fr.bde_teknantes.lnb.phone.feed.capture

import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.client.FeedClient
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.server.FeedServer
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.TitleScreen
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
import org.lwjgl.glfw.GLFW
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * Feed's screenshot harness: an unattended client plays a scenario ([FeedScenarios]) from the title screen, one step
 * per client tick, shoots, and quits; a watchdog kills it after 6 minutes. Development runs only (started by
 * [FeedClient] when `-Dlnbphone_feed.capture` is set). Shots go to `-Dlnbphone_feed.capture.dir`, or
 * `<game dir>/captures/feed/`.
 *
 * A smaller cousin of the demo's harness (`apps/demo/.../client/capture`), which Feed can't depend on.
 */
object FeedCapture {
    private val steps = ArrayList<() -> Boolean>()
    private var step = 0
    private var started = false
    private lateinit var outDir: Path

    @JvmStatic
    fun start() {
        val scenario = System.getProperty("lnbphone_feed.capture") ?: return
        val script = FeedScenarios.get(scenario) ?: run {
            FeedMod.LOGGER.error("Unknown Feed capture scenario '{}'", scenario)
            return
        }
        script(Script())
        outDir = (System.getProperty("lnbphone_feed.capture.dir")?.takeIf { it.isNotBlank() }?.let(Path::of) ?: Path.of("captures", "feed")).absolute()
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) { tick() }
        Thread({
            Thread.sleep(360_000)
            FeedMod.LOGGER.error("Feed capture still running after 6 minutes: killing the client")
            Runtime.getRuntime().halt(3)
        }, "lnbphone_feed-capture-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        if (!started) {
            if (mc.screen is AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false
                mc.setScreen(TitleScreen(true))
                return
            }
            if (mc.overlay != null || mc.screen !is TitleScreen) return
            mc.options.pauseOnLostFocus = false
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0)
            mc.options.tutorialStep = TutorialSteps.NONE
            started = true
        }
        if (step >= steps.size) return
        if (steps[step]()) step++
        if (step == steps.size) {
            FeedMod.LOGGER.info("Feed capture finished: shots in {}", outDir)
            mc.stop()
        }
    }

    /** What a scenario is written with: each call adds steps. */
    class Script {
        private fun add(step: () -> Boolean) {
            steps += step
        }

        fun run(action: () -> Unit) = add {
            action()
            true
        }

        fun waitTicks(ticks: Int) {
            var left = ticks
            add { --left <= 0 }
        }

        fun waitUntil(condition: () -> Boolean) = add(condition)

        fun guiScale(scale: Int) = run {
            val mc = Minecraft.getInstance()
            mc.options.guiScale().set(scale)
            mc.resizeDisplay()
        }

        fun language(code: String) {
            run {
                val mc = Minecraft.getInstance()
                mc.options.languageCode = code
                mc.languageManager.setSelected(code)
            }
            var reload: CompletableFuture<Void>? = null
            add {
                val mc = Minecraft.getInstance()
                val future = reload ?: mc.reloadResourcePacks().also { reload = it }
                future.isDone && mc.overlay == null
            }
        }

        /** A fresh flat world without cheats: the player is no operator, so no moderator for the memory backend. */
        @OptIn(ExperimentalPathApi::class)
        fun enterWorld(name: String) {
            var begun = false
            add {
                val mc = Minecraft.getInstance()
                if (!begun) {
                    begun = true
                    val saves = mc.gameDirectory.toPath().resolve("saves").resolve(name)
                    if (saves.exists()) saves.deleteRecursively()
                    val settings = LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, false, GameRules(), WorldDataConfiguration.DEFAULT)
                    mc.createWorldOpenFlows().createFreshLevel(name, settings, WorldOptions(0L, false, false), { registries ->
                        registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                    }, TitleScreen())
                }
                mc.level != null && mc.player != null && mc.screen == null
            }
        }

        fun key(key: Int) {
            run { press(key, GLFW.GLFW_PRESS) }
            run { press(key, GLFW.GLFW_RELEASE) }
        }

        private fun press(key: Int, action: Int) {
            val mc = Minecraft.getInstance()
            mc.keyboardHandler.keyPress(mc.window.window, key, GLFW.glfwGetKeyScancode(key), action, 0)
        }

        fun openPhone() {
            key(GLFW.GLFW_KEY_UP)
            waitUntil { CaptureHooks.isOpen }
            waitTicks(2)
        }

        /** Opens Feed afresh on its first screen, as from its icon (a target Feed doesn't know leads there). */
        fun openFeed() = run { check(CaptureHooks.openApp(Feed.APP_ID, "capture")) { "The phone refused to open Feed" } }

        /** Moves the pointer to the app-local point ([x], [y]) dp. */
        fun mouseTo(x: Int, y: Int) = mouseToAt { x.toFloat() to y.toFloat() }

        /** Moves the pointer to the app-local point [at] gives when the step runs (a card found where it is then). */
        fun mouseToAt(at: () -> Pair<Float, Float>) = run {
            val (x, y) = at()
            val mc = Minecraft.getInstance()
            val device = CaptureHooks.devicePosition() ?: error("The phone is away")
            val viewport = CaptureHooks.viewportOffset
            val gx = device[0] + viewport[0] + x / 2.0
            val gy = device[1] + viewport[1] + y / 2.0
            val scale = mc.window.screenWidth.toDouble() / mc.window.guiScaledWidth
            ON_MOVE.invoke(mc.mouseHandler, mc.window.window, gx * scale, gy * scale)
        }

        fun click(x: Int, y: Int) {
            mouseTo(x, y)
            run { button(GLFW.GLFW_PRESS) }
            run { button(GLFW.GLFW_RELEASE) }
        }

        /** Clicks the app-local point [at] gives when the step runs. */
        fun clickAt(at: () -> Pair<Float, Float>) {
            mouseToAt(at)
            run { button(GLFW.GLFW_PRESS) }
            run { button(GLFW.GLFW_RELEASE) }
        }

        private fun button(action: Int) {
            val mc = Minecraft.getInstance()
            ON_PRESS.invoke(mc.mouseHandler, mc.window.window, GLFW.GLFW_MOUSE_BUTTON_LEFT, action, 0)
        }

        /** Turns the wheel where the pointer is (negative: down the list). */
        fun scroll(notches: Double) = run {
            val mc = Minecraft.getInstance()
            ON_SCROLL.invoke(mc.mouseHandler, mc.window.window, 0.0, notches)
        }

        /** Runs [action] with the memory backend, on the integrated server's thread. */
        fun backend(action: (MemoryBackend) -> Unit) = run {
            val server = Minecraft.getInstance().singleplayerServer ?: error("No integrated server")
            server.execute {
                val backend = FeedServer.current?.backend as? MemoryBackend ?: error("Feed doesn't run on its memory backend")
                action(backend)
            }
        }

        fun shot(name: String) = run {
            val mc = Minecraft.getInstance()
            Files.createDirectories(outDir)
            val file = outDir.resolve("$name.png")
            Screenshot.takeScreenshot(mc.mainRenderTarget).use { it.writeToFile(file) }
            FeedMod.LOGGER.info("Feed capture shot {} (GUI scale {})", file, mc.window.guiScale)
        }

        /** Logs what the client's session holds, to read beside the shots. */
        fun log(moment: String) = run {
            val s = FeedClient.session
            FeedMod.LOGGER.info(
                "Feed capture {}: availability {}, state {}, annonces {} pinned {} waiting {}, global {} waiting {}",
                moment, s.availability, s.state?.let { "linked=${it.linked} writing=${it.writing} rights=${it.rights}" },
                s.feed(FeedKind.ANNOUNCEMENTS).posts.size,
                s.feed(FeedKind.ANNOUNCEMENTS).pinned.size,
                s.feed(FeedKind.ANNOUNCEMENTS).waiting.size,
                s.feed(FeedKind.GLOBAL).posts.size,
                s.feed(FeedKind.GLOBAL).waiting.size,
            )
        }
    }

    // The game's input callbacks are private; the game runs with Mojang's names in development.
    private val ON_MOVE: Method = MouseHandler::class.java.getDeclaredMethod(
        "onMove", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
    ).apply { isAccessible = true }
    private val ON_PRESS: Method = MouseHandler::class.java.getDeclaredMethod(
        "onPress", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    ).apply { isAccessible = true }
    private val ON_SCROLL: Method = MouseHandler::class.java.getDeclaredMethod(
        "onScroll", Long::class.javaPrimitiveType, Double::class.javaPrimitiveType, Double::class.javaPrimitiveType,
    ).apply { isAccessible = true }
}
