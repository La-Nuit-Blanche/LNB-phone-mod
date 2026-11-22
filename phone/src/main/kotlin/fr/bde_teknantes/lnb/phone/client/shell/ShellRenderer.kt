package fr.bde_teknantes.lnb.phone.client.shell

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.icons.AppIconTextures
import fr.bde_teknantes.lnb.phone.client.notifications.BannerContent
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLayout
import fr.bde_teknantes.lnb.phone.client.notifications.BannerLook
import fr.bde_teknantes.lnb.phone.client.notifications.NotificationBanner
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * Draws the device (art direction 2 to 6): frame, screen content (home screen, app screens and their slides), the
 * notification banner (10.3), status bar, LED, home button and the screen's corner pixels, in that order, so the
 * corners notch whatever the app drew (8.1); and the peek of the closed phone (10.2). The pixel parts are GUI atlas
 * sprites (16.1) and plain fills, on whole GUI px.
 *
 * Render thread only.
 */
internal object ShellRenderer {
    private fun sprite(path: String) = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, path)

    private val FRAME = sprite("phone/frame")
    private val HOME_BUTTON = sprite("phone/home_button")
    private val HOME_BUTTON_HOVER = sprite("phone/home_button_hover")
    private val HOME_BUTTON_PRESSED = sprite("phone/home_button_pressed")
    private val SIGNAL = (0..4).map { sprite("status/signal_$it") }
    private val BATTERY = sprite("status/battery")
    private val WALLPAPER = sprite("wallpaper/dusk")
    private val ICON_HOVER = sprite("home/icon_hover")
    private val BADGE = sprite("home/badge")
    private val BADGE_PULSE = sprite("home/badge_pulse")

    // Depth layers, relative to the phone: item icons draw 150 above where they are put; badges go over them, the app
    // layer over the home screen it slides across, and the shell's last parts over everything.
    private const val Z_BADGE = 200f
    private const val Z_APP = 300f
    private const val Z_BANNER = 350f
    private const val Z_TOP = 400f

    // Where the pointer is said to be when nobody points at the phone (it is sliding, the mouse belongs to the game).
    private const val NOWHERE = -10_000.0

    /** For the screenshot harness: a latency to show in place of the player's own (singleplayer has none). */
    internal var latencyOverride: Int? = null

    /** For the screenshot harness: keeps a blinking LED lit, so a capture catches it on. */
    internal var ledHeldOn = false

    fun render(graphics: GuiGraphics, pose: DevicePose, now: Float, devicePointer: Pointer?) {
        val mc = Minecraft.getInstance()
        // What lies under the notification banner doesn't hear the pointer: no hover ring or app hover through it.
        val pointer = devicePointer?.takeUnless { overBanner(it, pose, now) }
        if (pose.scrim > 0f) graphics.fill(0, 0, PhoneShell.guiWidth(), PhoneShell.guiHeight(), PhoneLayout.scrimColor(pose.scrim))
        val x = pose.x
        val y = pose.y
        graphics.blitSprite(FRAME, x, y, Device.WIDTH, Device.HEIGHT)

        val navigator = LnbPhoneClient.navigator
        val current = navigator.currentScreen
        val slide = PhoneShell.screenSlide?.takeIf { current != null }
        val offset = slide?.offset(PhoneShell.screenSlideProgress(slide, now)) ?: 0
        val outgoing = PhoneShell.slideOutgoing
        val fromHome = slide != null && slide.forward && outgoing == null

        // The home screen, alone or under an app sliding in over it.
        if (current == null || fromHome) drawHome(graphics, mc.font, x, y, now, pointer.takeIf { current == null })

        val mouseX = pointer?.x ?: NOWHERE
        val mouseY = pointer?.y ?: NOWHERE
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, Z_APP)
        if (current != null) {
            when {
                slide == null -> drawApp(graphics, current, x, y, 0, mouseX, mouseY)
                slide.forward -> {
                    outgoing?.let { drawApp(graphics, it, x, y, 0, mouseX, mouseY) }
                    drawApp(graphics, current, x, y, offset, mouseX, mouseY)
                }
                else -> {
                    drawApp(graphics, current, x, y, 0, mouseX, mouseY)
                    outgoing?.let { drawApp(graphics, it, x, y, offset, mouseX, mouseY) }
                }
            }
        }
        graphics.pose().popPose()

        // The notification banner over whatever the viewport shows, under the status bar (10.3).
        PhoneShell.alerts.banner?.let { drawBanner(graphics, it.notification, x, y, now, devicePointer) }

        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, Z_TOP)
        // The status bar is translucent over the wallpaper at home, the app's background inside apps; while an app
        // slides in from home, each part of the bar follows what is under it.
        val homeWidth = when {
            current == null -> Device.SCREEN_WIDTH
            fromHome -> offset
            else -> 0
        }
        drawStatusBar(graphics, mc, x + Device.SCREEN_X, y + Device.SCREEN_Y, homeWidth)
        drawLed(graphics, x, y, lit = PhoneShell.counts.values.any { it > 0 } && (ledHeldOn || Motion.ledLit(PhoneShell.ticks)))
        drawHomeButton(graphics, x, y, now, pointer)
        drawScreenCorners(graphics, x, y)
        graphics.pose().popPose()
    }

    /**
     * The closed phone peeking from the bottom-right corner (10.2): the device with the wallpaper, the home status bar
     * and the banner on its screen, no icons, the LED lit. Drawn on the HUD, under the hotbar.
     */
    fun renderPeek(graphics: GuiGraphics, pose: DevicePose, now: Float, notification: PhoneNotification) {
        val mc = Minecraft.getInstance()
        val x = pose.x
        val y = pose.y
        graphics.blitSprite(FRAME, x, y, Device.WIDTH, Device.HEIGHT)
        val sx = x + Device.SCREEN_X
        val sy = y + Device.SCREEN_Y
        graphics.blitSprite(WALLPAPER, sx, sy, Device.SCREEN_WIDTH, Device.SCREEN_HEIGHT)
        contentOf(notification)?.let {
            // Clipped below the status bar, as over the open phone: at screen-local (4, 14) GUI px.
            val vy = y + Device.VIEWPORT_Y
            banner.render(
                graphics, x + Device.VIEWPORT_X, vy, Device.VIEWPORT_WIDTH, Device.VIEWPORT_HEIGHT,
                BannerLayout.PEEK_SCREEN_X_GUI * 2f, (BannerLayout.PEEK_SCREEN_Y_GUI - Device.STATUS_HEIGHT) * 2f, it, BannerLook.NORMAL,
            )
        }
        drawStatusBar(graphics, mc, sx, sy, Device.SCREEN_WIDTH)
        drawLed(graphics, x, y, lit = true)
        drawHomeButton(graphics, x, y, now, null)
        drawScreenCorners(graphics, x, y)
    }

    // ---- Notification banner (10.1, 10.3) ----

    private val banner = NotificationBanner()

    // The last banner's content, made drawable once: its text is laid out with the fonts on its first frame.
    private var content: BannerContent? = null
    private var contentNotification: PhoneNotification? = null

    /** [notification] as a banner shows it, or null if its app is gone. */
    private fun contentOf(notification: PhoneNotification): BannerContent? {
        if (contentNotification !== notification) {
            val app = LnbPhoneClient.apps[notification.appId] ?: return null
            content = BannerContent.of(app, notification)
            contentNotification = notification
        }
        return content
    }

    private fun overBanner(pointer: Pointer, pose: DevicePose, now: Float): Boolean {
        val alerts = PhoneShell.alerts
        if (alerts.banner == null || !Device.inViewport(pointer.x - pose.x, pointer.y - pose.y)) return false
        val dpX = (pointer.x - pose.x - Device.VIEWPORT_X) * 2
        val dpY = (pointer.y - pose.y - Device.VIEWPORT_Y) * 2
        return alerts.bannerContains(dpX.toFloat(), dpY.toFloat(), now)
    }

    private fun drawBanner(graphics: GuiGraphics, notification: PhoneNotification, x: Int, y: Int, now: Float, pointer: Pointer?) {
        val alerts = PhoneShell.alerts
        val progress = alerts.bannerProgress(now)
        if (progress <= 0f || !PhoneShell.appsSupported()) return
        val content = contentOf(notification) ?: return
        val vx = x + Device.VIEWPORT_X
        val vy = y + Device.VIEWPORT_Y
        val hovered = pointer != null && overBanner(pointer, DevicePose(x, y, 0f), now)
        val look = when {
            alerts.bannerPressed -> BannerLook.PRESSED
            hovered -> BannerLook.HOVER
            else -> BannerLook.NORMAL
        }
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, Z_BANNER)
        banner.render(
            graphics, vx, vy, Device.VIEWPORT_WIDTH, Device.VIEWPORT_HEIGHT,
            BannerLayout.IN_PHONE_X, BannerLayout.inPhoneY(progress), content, look, progress,
        )
        graphics.pose().popPose()
    }

    // ---- Home screen (6) ----

    private fun drawHome(graphics: GuiGraphics, font: Font, x: Int, y: Int, now: Float, pointer: Pointer?) {
        graphics.blitSprite(WALLPAPER, x + Device.SCREEN_X, y + Device.SCREEN_Y, Device.SCREEN_WIDTH, Device.SCREEN_HEIGHT)
        val vx = x + Device.VIEWPORT_X
        val vy = y + Device.VIEWPORT_Y
        val apps = LnbPhoneClient.apps.apps
        val page = PhoneShell.page()
        val onPage = HomeGrid.appsOnPage(page, apps.size)
        val hovered = pointer?.takeIf { Device.inViewport(it.x - x, it.y - y) }
            ?.let { HomeGrid.appAt(it.x - vx, it.y - vy, page, apps.size) }
        val pressed = PhoneShell.pressedApp?.takeIf { PhoneShell.pressedAppShown(now) && it in onPage }
        val ringed = pressed ?: hovered

        for (index in onPage) {
            val slot = index - onPage.first
            val sink = if (index == pressed) 1 else 0
            AppIconTextures.draw(graphics, apps[index].icon, vx + HomeGrid.iconX(slot), vy + HomeGrid.iconY(slot) + sink)
        }
        if (ringed != null) {
            val slot = ringed - onPage.first
            graphics.blitSprite(
                ICON_HOVER,
                vx + HomeGrid.iconX(slot) - HomeGrid.RING_INSET, vy + HomeGrid.iconY(slot) - HomeGrid.RING_INSET,
                HomeGrid.RING_SIZE, HomeGrid.RING_SIZE,
            )
        }

        // Badges over the ring and over item icons.
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, Z_BADGE)
        for (index in onPage) {
            val app = apps[index]
            val text = HomeGrid.badgeText(PhoneShell.counts[app.id] ?: 0) ?: continue
            val slot = index - onPage.first
            val width = HomeGrid.badgeWidth(text)
            val bx = vx + HomeGrid.badgeX(slot, width)
            val by = vy + HomeGrid.badgeY(slot)
            val pulsing = PhoneShell.pulseStart[app.id]?.let { Motion.pulseLit(it, PhoneShell.ticks) } == true
            graphics.blitSprite(if (pulsing) BADGE_PULSE else BADGE, bx, by, width, HomeGrid.BADGE_HEIGHT)
            graphics.drawString(font, text, bx + HomeGrid.BADGE_TEXT_X, by + HomeGrid.BADGE_TEXT_Y, ShellColors.BADGE_TEXT, false)
        }
        graphics.pose().popPose()

        if (ringed != null) {
            val name = labelText(font, apps[ringed].name)
            graphics.drawString(font, name, vx + HomeGrid.centredX(drawnWidth(font, name)), vy + HomeGrid.LABEL_Y, ShellColors.WHITE, true)
        }

        val pages = HomeGrid.pageCount(apps.size)
        if (pages > 1) {
            val first = vx + HomeGrid.firstDotX(pages)
            for (p in 0 until pages) {
                val dx = first + p * HomeGrid.DOT_PITCH
                val color = if (p == page) ShellColors.WHITE else ShellColors.PAGE_DOT_INACTIVE
                graphics.fill(dx, vy + HomeGrid.DOT_Y, dx + HomeGrid.DOT_SIZE, vy + HomeGrid.DOT_Y + HomeGrid.DOT_SIZE, color)
            }
        }
    }

    /** The app's name for the label line: at most 20 characters, and never wider than the line's 120 px. */
    private fun labelText(font: Font, name: Component): String {
        val text = name.string
        val chars = if (text.codePointCount(0, text.length) > HomeGrid.LABEL_MAX_CHARS) {
            text.substring(0, text.offsetByCodePoints(0, HomeGrid.LABEL_MAX_CHARS))
        } else {
            text
        }
        return font.plainSubstrByWidth(chars, HomeGrid.LABEL_MAX_WIDTH + 1)
    }

    // The bitmap font's advance counts a 1 px gap after the last glyph; the art direction measures what is drawn.
    private fun drawnWidth(font: Font, text: String): Int = maxOf(0, font.width(text) - 1)

    // ---- Apps (8.1, 8.2) ----

    private fun drawApp(graphics: GuiGraphics, screen: AppScreen, x: Int, y: Int, offset: Int, mouseX: Double, mouseY: Double) {
        val vx = x + Device.VIEWPORT_X
        val vy = y + Device.VIEWPORT_Y
        if (PhoneShell.appsSupported()) {
            val partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)
            AppHost.draw(graphics, screen, vx, vy, offset, mouseX, mouseY, partialTick)
        } else {
            drawScaleMessage(graphics, vx, vy, offset)
        }
    }

    /** At GUI scale 1 the app isn't drawn: its viewport says that apps need GUI scale 2 (P-15). */
    private fun drawScaleMessage(graphics: GuiGraphics, vx: Int, vy: Int, offset: Int) {
        val font = Minecraft.getInstance().font
        graphics.enableScissor(vx, vy, vx + Device.VIEWPORT_WIDTH, vy + Device.VIEWPORT_HEIGHT)
        val left = vx + offset
        graphics.fill(left, vy, left + Device.VIEWPORT_WIDTH, vy + Device.VIEWPORT_HEIGHT, ShellColors.APP_BG)
        val text = Component.translatable("gui.lnbphone.scale").string
        val top = vy + Math.floorDiv(Device.VIEWPORT_HEIGHT - CAP_HEIGHT, 2)
        graphics.drawString(font, text, left + HomeGrid.centredX(drawnWidth(font, text)), top, ShellColors.TEXT_2, false)
        graphics.disableScissor()
    }

    // ---- Status bar (4) ----

    private fun drawStatusBar(graphics: GuiGraphics, mc: Minecraft, sx: Int, sy: Int, homeWidth: Int) {
        val bottom = sy + Device.STATUS_HEIGHT
        if (homeWidth > 0) graphics.fill(sx, sy, sx + homeWidth, bottom, ShellColors.STATUS_BG_HOME)
        if (homeWidth < Device.SCREEN_WIDTH) graphics.fill(sx + homeWidth, sy, sx + Device.SCREEN_WIDTH, bottom, ShellColors.STATUS_BG_APP)

        val top = sy + StatusBar.ROW
        val player = mc.player
        val latency = latencyOverride ?: player?.let { mc.connection?.getPlayerInfo(it.uuid) }?.latency
        val bars = StatusBar.signalBars(latency, latencyOverride == null && mc.isSingleplayer)
        graphics.blitSprite(SIGNAL[bars], sx + StatusBar.SIGNAL_X, top, StatusBar.SIGNAL_WIDTH, StatusBar.ELEMENT_HEIGHT)

        val level = mc.level
        val clock = if (level == null) StatusBar.NO_TIME else StatusBar.clock(level.dayTime, level.dimensionType().hasFixedTime())
        graphics.drawString(mc.font, clock, sx + StatusBar.clockX(drawnWidth(mc.font, clock)), top, ShellColors.STATUS_INK, true)

        val batteryX = sx + StatusBar.BATTERY_X
        graphics.blitSprite(BATTERY, batteryX, top, StatusBar.BATTERY_WIDTH, StatusBar.ELEMENT_HEIGHT)
        val battery = StatusBar.battery(player?.foodData?.foodLevel ?: 20, player != null && !player.isCreative && !player.isSpectator)
        if (battery.cells > 0) {
            val fx = batteryX + StatusBar.FILL_X
            val fy = top + StatusBar.FILL_Y
            graphics.fill(fx, fy, fx + battery.cells, fy + StatusBar.FILL_HEIGHT, battery.color)
        }
    }

    // ---- Bezel (2.1, 2.3) ----

    /** The LED (2.1): blinking while any app has a badge, lit during a peek. */
    private fun drawLed(graphics: GuiGraphics, x: Int, y: Int, lit: Boolean) {
        val lx = x + Device.LED_X
        val ly = y + Device.LED_Y
        graphics.fill(lx, ly, lx + Device.LED_SIZE, ly + Device.LED_SIZE, if (lit) ShellColors.ACCENT else ShellColors.BODY_LO)
    }

    private fun drawHomeButton(graphics: GuiGraphics, x: Int, y: Int, now: Float, pointer: Pointer?) {
        val hovered = pointer != null && Device.inHomeButton((pointer.x - x).floorInt(), (pointer.y - y).floorInt())
        val sprite = when {
            PhoneShell.homeButtonPressedShown(now) -> HOME_BUTTON_PRESSED
            hovered -> HOME_BUTTON_HOVER
            else -> HOME_BUTTON
        }
        graphics.blitSprite(sprite, x + Device.HOME_BUTTON_X, y + Device.HOME_BUTTON_Y, Device.HOME_BUTTON_SIZE, Device.HOME_BUTTON_SIZE)
    }

    /** The screen's 1-step corners, painted after the app layer (2.2, 8.1). */
    private fun drawScreenCorners(graphics: GuiGraphics, x: Int, y: Int) {
        val left = x + Device.SCREEN_X
        val right = left + Device.SCREEN_WIDTH - 1
        val top = y + Device.SCREEN_Y
        val bottom = top + Device.SCREEN_HEIGHT - 1
        for (cx in intArrayOf(left, right)) for (cy in intArrayOf(top, bottom)) {
            graphics.fill(cx, cy, cx + 1, cy + 1, ShellColors.SCREEN_RIM)
        }
    }

    private fun Double.floorInt() = kotlin.math.floor(this).toInt()

    // Height of the bitmap font's capitals (2.6): what "centred vertically" centres.
    private const val CAP_HEIGHT = 7
}
