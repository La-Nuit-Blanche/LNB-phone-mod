package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.AnnouncerBadge
import fr.bde_teknantes.lnb.phone.api.client.ui.Avatar
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.CountBadge
import fr.bde_teknantes.lnb.phone.api.client.ui.Divider
import fr.bde_teknantes.lnb.phone.api.client.ui.DotBadge
import fr.bde_teknantes.lnb.phone.api.client.ui.Fab
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconButton
import fr.bde_teknantes.lnb.phone.api.client.ui.IconView
import fr.bde_teknantes.lnb.phone.api.client.ui.Interaction
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.MessageRow
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.SkeletonPost
import fr.bde_teknantes.lnb.phone.api.client.ui.Spinner
import fr.bde_teknantes.lnb.phone.api.client.ui.SpinnerRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Tabs
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.api.client.ui.atRight
import fr.bde_teknantes.lnb.phone.demo.client.smooth.SampleSkins

/**
 * The UI kit's gallery: every component of art direction 8.7 in every state, one page of the real viewport size each.
 * Captions name the states, left to right. States that need the pointer are forced with [Clickable.showAs].
 */
object Gallery {
    /** A gallery page: a header, then what [build] adds. */
    open class Page(title: String, back: Boolean = true, build: Page.() -> Unit = {}) : KitScreen() {
        init {
            add(Header(title, showBack = back).at(0f, 0f))
            build()
        }

        fun caption(text: String, y: Float) = add(Label(text, TextStyle.CAPTION, Colors.TEXT_3).at(12f, y, 232f, 16f))

        fun <T : Clickable> T.look(state: Interaction?, focus: Boolean = false): T = apply {
            showAs = state
            showFocus = focus
        }
    }

    fun headers() = Page("Publication") {
        caption("Close, no title, primary button 88 dp", 52f)
        add(Header(null, leading = IconButton(Icon.CLOSE), trailing = Button(tr("compose.post"), fixedWidth = 88f)).at(0f, 70f))
        caption("Root screen (no back), icon button trailing", 122f)
        add(Header(tr("thread.title"), showBack = false, trailing = IconButton(Icon.MORE)).at(0f, 140f))
        caption("Icon button: normal, hover, pressed, disabled, focus", 192f)
        val looks = listOf(null, Interaction.HOVER, Interaction.PRESSED, Interaction.DISABLED, null)
        looks.forEachIndexed { i, look ->
            add(IconButton(Icon.BACK).look(look, focus = i == 4).at(12f + i * 48f, 212f))
        }
        caption("28 dp in posts (text-2): normal, hover, pressed", 258f)
        listOf(null, Interaction.HOVER, Interaction.PRESSED).forEachIndexed { i, look ->
            add(IconButton(Icon.MORE, 28f, Colors.TEXT_2).look(look).at(14f + i * 48f, 278f))
        }
        caption("Long title truncates before the trailing slot", 310f)
        add(Header("Un titre bien trop long pour tenir ici", showBack = true, trailing = IconButton(Icon.MORE)).at(0f, 326f))
    }

    fun tabsAndFab() = object : Page("Tabs, FAB, badges", build = {}) {
        init {
            caption("Annonces active, Global with unread dot", 50f)
            add(Tabs(listOf(tr("tab.announcements"), tr("tab.global")), 0).also { it.setUnread(1, true) }.at(0f, 68f))
            caption("Inactive hover / pressed, Global active", 118f)
            add(Tabs(listOf(tr("tab.announcements"), tr("tab.global")), 1).also { forceTab(it, 0, Interaction.HOVER) }.at(0f, 136f))
            add(Tabs(listOf(tr("tab.announcements"), tr("tab.global")), 1).also { forceTab(it, 0, Interaction.PRESSED) }.at(0f, 184f))
            caption("FAB: normal, hover, pressed", 236f)
            listOf(null, Interaction.HOVER, Interaction.PRESSED).forEachIndexed { i, look ->
                add(Fab(floating = false).look(look).at(12f + i * 64f, 258f))
            }
            caption("Badges: count, 99+, dot, announcer", 318f)
            add(CountBadge(3).at(12f, 340f))
            add(CountBadge(12).at(36f, 340f))
            add(CountBadge(150).at(66f, 340f))
            add(DotBadge().at(108f, 344f))
            add(AnnouncerBadge().at(126f, 340f))
            add(Avatar(SampleSkins.ALEX, Avatar.Size.SMALL).at(156f, 340f))
            add(Avatar(null, Avatar.Size.SMALL).at(180f, 340f))
        }

        private fun forceTab(tabs: Tabs, index: Int, look: Interaction) {
            (tabs.children[index] as Clickable).showAs = look
        }
    }

    private fun buttonRows(page: Page, kind: Button.Kind, label: String, top: Float, loading: Boolean) {
        page.caption("${kind.name.lowercase()}: normal, hover, pressed", top)
        listOf(null, Interaction.HOVER, Interaction.PRESSED).forEachIndexed { i, look ->
            page.add(page.run { Button(label, kind, 74f).look(look) }.at(12f + i * 79f, top + 18f))
        }
        page.caption(if (loading) "disabled, loading, focus" else "disabled, focus", top + 56f)
        page.add(Button(label, kind, 74f).also { it.enabled = false }.at(12f, top + 74f))
        var x = 91f
        if (loading) {
            page.add(Button(label, kind, 74f).also { it.loading = true }.at(x, top + 74f))
            x += 79f
        }
        page.add(page.run { Button(label, kind, 74f).look(null, focus = true) }.at(x, top + 74f))
    }

    fun buttons() = Page("Buttons") {
        buttonRows(this, Button.Kind.PRIMARY, tr("compose.post"), 50f, loading = true)
        buttonRows(this, Button.Kind.DANGER, "Bannir", 166f, loading = true)
        caption("Auto width (min 64) and fixed 136", 282f)
        add(Button("OK").at(12f, 300f))
        add(Button(tr("empty.global.button"), fixedWidth = 136f).at(92f, 300f))
    }

    fun buttons2() = Page("Buttons 2") {
        buttonRows(this, Button.Kind.SECONDARY, tr("dialog.cancel"), 50f, loading = false)
        buttonRows(this, Button.Kind.TEXT, "Voir", 166f, loading = false)
        caption("Secondary retrying (104 dp, accent spinner)", 282f)
        add(Button(tr("error.retry"), Button.Kind.SECONDARY, 104f).also { it.loading = true }.at(12f, 300f))
        add(Spinner().at(132f, 306f))
        add(Spinner(16f).at(164f, 308f))
    }

    private fun fieldStates(page: Page, states: List<String>) {
        states.forEachIndexed { i, name ->
            val y = 52f + i * 92f
            page.caption(name, y)
            val filled = name.startsWith("Filled") || name.startsWith("Error") || name.startsWith("Disabled")
            val field = TextField(TextState(if (filled) "Mika Laurent" else "", multiline = false), tr("reply.placeholder", "Alexandre Morel"))
            when {
                name.startsWith("Hover") -> field.showHovered = true
                name.startsWith("Focused") -> field.showFocused = true
                name.startsWith("Error") -> field.error = "Pseudo inconnu"
                name.startsWith("Disabled") -> field.enabled = false
            }
            page.add(field.at(12f, y + 20f))
        }
    }

    fun textFields() = Page("Text fields") { fieldStates(this, listOf("Empty", "Hover", "Focused: caret, placeholder after it")) }

    fun textFields2() = Page("Text fields 2") { fieldStates(this, listOf("Filled", "Error: helper 4 dp under", "Disabled")) }

    fun textAreas() = Page("Text areas") {
        caption("Empty, focused: placeholder, caret, 0/280", 50f)
        add(TextArea(TextState(), tr("compose.placeholder"), 280).also { it.showFocused = true }.at(12f, 68f, 232f, 76f))
        caption("Hover, 142/280", 150f)
        add(TextArea(TextState(FeedSample.textOf(142)), "", 280).also { it.showHovered = true }.at(12f, 168f, 232f, 76f))
        caption("Sending: grey text, no caret", 250f)
        add(TextArea(TextState("J'arrive dans 5 min"), "", 280).also { it.sending = true; it.showFocused = true }.at(12f, 268f, 232f, 76f))
    }

    fun toggles() = Page("Toggles, segments") {
        caption("Toggle: off, off hover, on, on hover, disabled", 50f)
        listOf(false to null, false to Interaction.HOVER, true to null, true to Interaction.HOVER, false to Interaction.DISABLED).forEachIndexed { i, (on, look) ->
            add(Toggle(on).look(look).at(12f + i * 48f, 72f))
        }
        add(Toggle(true).also { it.enabled = false }.at(252f - 36f - 12f, 104f))
        caption("on, disabled", 104f)
        caption("Segmented: Annonces selected, Global hovered", 134f)
        add(SegmentedControl(listOf(tr("tab.announcements"), tr("tab.global")), 0).also { (it.children[1] as Clickable).showAs = Interaction.HOVER }.at(12f, 152f))
        caption("Segmented: Global selected, disabled", 194f)
        add(SegmentedControl(listOf(tr("tab.announcements"), tr("tab.global")), 1).also { it.enabled = false }.at(12f, 212f))
        caption("Settings (12): Coin / Centre", 254f)
        add(SegmentedControl(listOf(tr("settings.position.corner"), tr("settings.position.centre")), 0).at(12f, 272f))
        caption("Focus ring", 314f)
        add(Toggle(true).look(null, focus = true).at(12f, 336f))
        add(Button("OK", fixedWidth = 64f).look(null, focus = true).at(68f, 330f))
    }

    fun rows() = Page("Rows, dividers") {
        val list = add(ScrollList().at(0f, 44f, 256f, 326f))
        list.add(Label(tr("settings.section.phone"), TextStyle.CAPTION_STRONG, Colors.TEXT_2).let { Padded(it, 12f, 12f, 4f) })
        list.add(ListRow(tr("settings.sounds"), trailing = Toggle(true)))
        list.add(ListRow(tr("settings.preview"), trailing = Toggle(false)).look(Interaction.HOVER))
        list.add(ListRow("Ligne simple, chevron", trailing = IconView(Icon.CHEVRON, Colors.TEXT_2)).look(Interaction.PRESSED))
        list.add(ListRow("Alexandre Morel", "@Alex_Craft", leading = Avatar(SampleSkins.ALEX), trailing = Label("2 min", TextStyle.CAPTION, Colors.TEXT_2)))
        list.add(ListRow("Global", "Tous les posts", leading = IconView(Icon.GLOBE)))
        list.add(ListRow("Désactivé", trailing = Toggle(true)).also { it.enabled = false })
        list.add(Padded(Label("Divider, full width then inset", TextStyle.CAPTION, Colors.TEXT_3), 12f, 8f, 4f))
        list.add(Divider())
        list.add(Spacer16())
        list.add(Divider(inset = true))
    }

    private class Spacer16 : Widget() {
        override val preferredHeight get() = 16f
    }

    /** A label with padding, as a list row. */
    private class Padded(private val label: Label, private val left: Float, private val top: Float, private val bottom: Float) : Widget() {
        init {
            add(label)
        }

        override val preferredHeight get() = top + label.preferredHeight + bottom

        override fun layout() {
            label.at(left, top, width - 2 * left, label.preferredHeight)
        }
    }

    fun loading() = Page("Loading") {
        val list = add(ScrollList().at(0f, 44f, 256f, 326f))
        repeat(2) { list.add(SkeletonPost()) }
        list.add(SpinnerRow())
        list.add(MessageRow(tr("feed.end")))
        list.add(MessageRow(tr("thread.no_replies"), Colors.TEXT_2))
    }

    fun iconsAndType() = object : Page("Icons, type") {
        override val background get() = Colors.BG

        init {
            add(object : Widget() {
                override fun render(canvas: AppCanvas) {
                    var x = 12f
                    var y = 4f
                    for (icon in Icon.ALL) {
                        for (size in icon.sizes) {
                            if (x + size > 244f) {
                                x = 12f
                                y += 44f
                            }
                            canvas.drawIcon(icon, x, y + (40f - size) / 2f, Colors.TEXT_1, size.toFloat())
                            x += size + 8f
                        }
                    }
                    y += 52f
                    for (style in TextStyle.entries) {
                        canvas.drawText("${style.name.lowercase()} · Où ça, Noël ?", 12f, y, style, Colors.TEXT_1)
                        y += style.lineHeight + 2f
                    }
                }
            }.at(0f, 44f, 256f, 326f))
        }
    }
}
