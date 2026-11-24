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
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
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
 * `<game dir>/captures/feed/`. The language and the GUI scale a scenario starts with are `-Dlnbphone_feed.capture.lang`
 * (default `fr_fr`) and `-Dlnbphone_feed.capture.scale` (default 3): [Options].
 *
 * Two things made early runs fragile, and the harness now guards against both:
 * - a window too small for the GUI scale (Minecraft allows scale n only from 320n x 240n pixels, and lowers it
 *   silently) once gave a blank shot: a scale that isn't the one asked for, a minimised window, or a render target
 *   that isn't the window now stop the run with a message instead of shooting;
 * - a click was once lost when the real mouse moved over the window: the game's own input callbacks are detached when
 *   the run starts, so that only the harness's events reach the game (the keyboard likewise), and a click's move and
 *   press happen in one step.
 *
 * A smaller cousin of the demo's harness (`apps/demo/.../client/capture`), which Feed can't depend on.
 */
object FeedCapture {
    /** What a run starts with, from the capture run's properties (`-PcaptureLang`, `-PcaptureScale`). */
    object Options {
        val language: String = System.getProperty("lnbphone_feed.capture.lang")?.takeIf { it.isNotBlank() } ?: "fr_fr"
        val scale: Int = System.getProperty("lnbphone_feed.capture.scale")?.toIntOrNull() ?: 3

        /**
         * Whether the run asks for something other than the defaults. A scenario's closing shots in another language
         * and scale (to compare with the first ones) are then left out: the run is about the one asked for.
         */
        val customised: Boolean get() = language != "fr_fr" || scale != 3
    }

    /** Stops the run with [message] in the log and a failure exit code: a half-played scenario shows nothing true. */
    private fun fail(message: String): Nothing {
        FeedMod.LOGGER.error("Feed capture stopped: {}", message)
        Runtime.getRuntime().halt(2)
        error(message)
    }

    private var requestedScale = 0

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
        // Scenarios against a real site and server wait for restarts: -PcaptureWatchdog gives them longer.
        val watchdogSeconds = System.getProperty("lnbphone_feed.capture.watchdog")?.toLongOrNull() ?: 360L
        Thread({
            Thread.sleep(watchdogSeconds * 1000)
            FeedMod.LOGGER.error("Feed capture still running after {} seconds: killing the client", watchdogSeconds)
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
            detachRealInput(mc)
            started = true
            FeedMod.LOGGER.info("Feed capture started: window {}x{}, shots in {}", mc.window.width, mc.window.height, outDir)
        }
        if (step >= steps.size) return
        if (steps[step]()) step++
        if (step == steps.size) {
            FeedMod.LOGGER.info("Feed capture finished: shots in {}", outDir)
            mc.stop()
        }
    }

    /** Removes the game's GLFW callbacks for the mouse and the keyboard: the script calls the handlers itself. */
    private fun detachRealInput(mc: Minecraft) {
        val window = mc.window.window
        GLFW.glfwSetCursorPosCallback(window, null)
        GLFW.glfwSetMouseButtonCallback(window, null)
        GLFW.glfwSetScrollCallback(window, null)
        GLFW.glfwSetKeyCallback(window, null)
        GLFW.glfwSetCharCallback(window, null)
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
            requestedScale = scale
            checkWindow(mc)
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

        /**
         * Joins the dedicated server at [address] (`host:port`) and waits until in its world. A refused or lost
         * connection is tried again every 5 seconds, so a step can wait for a server that is restarting.
         */
        fun joinServer(address: String) {
            var begun = false
            var retryAt = 0
            var ticks = 0
            add {
                val mc = Minecraft.getInstance()
                ticks++
                if (mc.screen is DisconnectedScreen && begun) {
                    begun = false
                    retryAt = ticks + 100
                    mc.setScreen(TitleScreen())
                }
                if (!begun && ticks >= retryAt && mc.level == null) {
                    begun = true
                    // No "social interactions" toast on joining a server for the first time.
                    mc.options.joinedFirstServer = true
                    FeedMod.LOGGER.info("Feed capture joining {}", address)
                    ConnectScreen.startConnecting(TitleScreen(), mc, ServerAddress.parseString(address), ServerData("LNB Feed", address, ServerData.Type.OTHER), false, null)
                }
                // A player who died on the server (its mobs) joins on the death screen: respawn.
                if (mc.screen is DeathScreen) {
                    mc.player?.respawn()
                    mc.setScreen(null)
                }
                mc.level != null && mc.player != null && mc.screen == null
            }
        }

        /** Leaves the server for the title screen, as the pause menu's Disconnect does. */
        fun leaveServer() = add {
            val mc = Minecraft.getInstance()
            mc.level?.let {
                it.disconnect()
                mc.disconnect(TitleScreen())
            }
            mc.level == null
        }

        /** Waits until [condition], or [ticks] at most, then goes on, logging that it gave up on [what]. */
        fun waitUntil(ticks: Int, what: String, condition: () -> Boolean) {
            var left = ticks
            add {
                when {
                    condition() -> true
                    --left <= 0 -> {
                        FeedMod.LOGGER.warn("Feed capture gave up waiting for {}", what)
                        true
                    }
                    else -> false
                }
            }
        }

        /**
         * Asks the run's orchestrator for [name] (a file `req-<name>` in `-Dlnbphone_feed.capture.control`) and waits
         * until it answers (`done-<name>`): restarting the site or the server, which the client can't do itself.
         */
        fun control(name: String) {
            var asked = false
            add {
                val dir = Path.of(System.getProperty("lnbphone_feed.capture.control") ?: error("No -Dlnbphone_feed.capture.control"))
                if (!asked) {
                    asked = true
                    Files.deleteIfExists(dir.resolve("done-$name"))
                    Files.writeString(dir.resolve("req-$name"), "")
                    FeedMod.LOGGER.info("Feed capture asked the orchestrator for {}", name)
                }
                Files.exists(dir.resolve("done-$name"))
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
            movePointer(x, y)
        }

        fun click(x: Int, y: Int) = clickAt { x.toFloat() to y.toFloat() }

        /**
         * Clicks the app-local point [at] gives when the step runs. The pointer is moved in the same step as the press
         * (and again for the release, to the same point), so that nothing can come between a move and its click.
         */
        fun clickAt(at: () -> Pair<Float, Float>) {
            var point = 0f to 0f
            run {
                point = at()
                movePointer(point.first, point.second)
                button(GLFW.GLFW_PRESS)
            }
            run {
                movePointer(point.first, point.second)
                button(GLFW.GLFW_RELEASE)
            }
        }

        private fun movePointer(x: Float, y: Float) {
            val mc = Minecraft.getInstance()
            val device = CaptureHooks.devicePosition() ?: error("The phone is away")
            val viewport = CaptureHooks.viewportOffset
            val gx = device[0] + viewport[0] + x / 2.0
            val gy = device[1] + viewport[1] + y / 2.0
            val scale = mc.window.screenWidth.toDouble() / mc.window.guiScaledWidth
            ON_MOVE.invoke(mc.mouseHandler, mc.window.window, gx * scale, gy * scale)
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
            checkWindow(mc)
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

    /**
     * Stops the run unless the window can show what the scenario set: it is not minimised, and the GUI scale in force is
     * the one a scenario set (Minecraft lowers a scale the window is too small for), drawn into a target the window's size.
     */
    private fun checkWindow(mc: Minecraft) {
        val window = mc.window
        if (GLFW.glfwGetWindowAttrib(window.window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) fail("the game window is minimised")
        if (requestedScale > 0 && window.guiScale.toInt() != requestedScale) {
            fail(
                "GUI scale ${window.guiScale.toInt()} is in force, not $requestedScale: the window is ${window.width}x${window.height} " +
                    "and scale $requestedScale needs ${320 * requestedScale}x${240 * requestedScale} (-PcaptureSize=<w>x<h>)",
            )
        }
        val target = mc.mainRenderTarget
        if (target.width != window.width || target.height != window.height) {
            fail("the render target (${target.width}x${target.height}) isn't the window (${window.width}x${window.height})")
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
