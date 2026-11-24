package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.ui.Widget

/**
 * An invisible widget that runs [onFrame] before each frame, after the widgets added before it were updated: a
 * screen's way to bring its controls in line with the session every frame (`KitScreen` has a tick hook, not a frame
 * one). It takes no room and never catches the pointer.
 */
class FrameHook(private val onFrame: () -> Unit) : Widget() {
    override fun update() = onFrame()
}

/** Empty space at the end of a list whose height follows [size], read each frame (the thread's spacer). */
class FollowingSpacer(private val size: () -> Float) : Widget() {
    override val preferredWidth get() = 256f
    override val preferredHeight get() = size()
}
