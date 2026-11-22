package fr.bde_teknantes.lnb.phone.demo.client.kit

import net.minecraft.client.resources.language.I18n
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconView
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.at

/** Every page of the gallery and the composed Feed screens, in the order they are shown and captured. */
object KitPages {
    val gallery: List<HostedPage> = listOf(
        HostedPage("Headers, icon buttons") { Gallery.headers() },
        HostedPage("Tabs, FAB, badges") { Gallery.tabsAndFab() },
        HostedPage("Buttons: primary, danger") { Gallery.buttons() },
        HostedPage("Buttons: secondary, text") { Gallery.buttons2() },
        HostedPage("Text fields") { Gallery.textFields() },
        HostedPage("Text fields: filled, error, disabled") { Gallery.textFields2() },
        HostedPage("Text areas") { Gallery.textAreas() },
        HostedPage("Toggles, segmented") { Gallery.toggles() },
        HostedPage("List rows, dividers") { Gallery.rows() },
        HostedPage("Loading") { Gallery.loading() },
        HostedPage("Icons, type ramp") { Gallery.iconsAndType() },
        HostedPage("Menu, item hovered", pointer = { s ->
            val more = (s as FeedScreen).cards[2].more!!
            val top = more.toViewportY(0f)
            // Art direction 8.7: 4 dp under the button, or above it when the 48 dp menu would pass y 362.
            val below = more.toViewportY(more.height) + 4f
            val menuTop = if (below + 48f > 362f) top - 4f - 48f else below
            (more.toViewportX(more.width) - 60f) to (menuTop + 24f)
        }) { FeedScreen(FeedSession().apply { tab = 1 }).also { it.openMenu(2) } },
        HostedPage("Dialog, confirm hovered", pointer = { 176f to 224f }) { FeedScreen(FeedSession().apply { tab = 1 }).also { it.showDeleteDialog(2) } },
        HostedPage("Dialog, confirm loading") { FeedScreen(FeedSession().apply { tab = 0 }, viewerCanPin = true).also { it.showPinLimitDialog(loading = true) } },
        HostedPage("Snackbar, FAB lifted") { FeedScreen(FeedSession().apply { tab = 1 }).also { it.snackbar(error = false) } },
        HostedPage("Error snackbar, Retry hover", pointer = { 203f to 342f }) { FeedScreen(FeedSession().apply { tab = 1 }).also { it.snackbar(error = true) } },
        HostedPage("Tooltip, 2 lines", pointer = { s ->
            val area = (s as FeedScreen).cards[1].privateArea!!
            area.toViewportX(60f) to area.toViewportY(8f)
        }) { FeedScreen(FeedSession().apply { tab = 1 }, viewerIsOperator = true) },
        HostedPage("Empty: Global") { FeedScreen(FeedSession().apply { tab = 1 }, content = FeedContent.EMPTY) },
        HostedPage("Empty: Annonces") { FeedScreen(FeedSession().apply { tab = 0 }, content = FeedContent.EMPTY) },
        HostedPage("Error state") { FeedScreen(FeedSession().apply { tab = 0 }, content = FeedContent.ERROR) },
        HostedPage("Error state, retrying") { FeedScreen(FeedSession().apply { tab = 0 }, content = FeedContent.ERROR_RETRYING) },
    )

    val composed: List<HostedPage> = listOf(
        HostedPage("Annonces (9.4)") { FeedScreen(FeedSession().apply { tab = 0 }) },
        HostedPage("Pin hovered, tooltip", pointer = { s ->
            val pin = (s as FeedScreen).cards[2].pin!!
            pin.toViewportX(14f) to pin.toViewportY(14f)
        }) { FeedScreen(FeedSession().apply { tab = 0 }, viewerCanPin = true).also { it.scrollToCard(2) } },
        HostedPage("Global (9.5)") { FeedScreen(FeedSession().apply { tab = 1 }) },
        HostedPage("Global, like hovered", pointer = { s ->
            val like = (s as FeedScreen).cards[0].like
            like.toViewportX(14f) to like.toViewportY(14f)
        }) { FeedScreen(FeedSession().apply { tab = 1 }) },
        HostedPage("Global, card hovered", pointer = { s ->
            val card = (s as FeedScreen).cards[1]
            card.toViewportX(150f) to card.toViewportY(70f)
        }) { FeedScreen(FeedSession().apply { tab = 1 }) },
        HostedPage("Global, end of list") { FeedScreen(FeedSession().apply { tab = 1 }, endOfList = true).also { it.scrollTo(10000f) } },
        HostedPage("Loading (F-1)") { FeedScreen(FeedSession().apply { tab = 0 }, content = FeedContent.LOADING) },
        HostedPage("Announcer badge tooltip", pointer = { s ->
            val badge = (s as FeedScreen).cards[2].badge!!
            badge.toViewportX(8f) to badge.toViewportY(8f)
        }) { FeedScreen(FeedSession().apply { tab = 0 }) },
        HostedPage("Compose, empty (9.8)") { ComposeScreen() },
        HostedPage("Compose, 142/280") { ComposeScreen().also { it.setText(FeedSample.textOf(142)) } },
        HostedPage("Compose, 265/280") { ComposeScreen().also { it.setText(FeedSample.textOf(265)) } },
        HostedPage("Compose, -5 over the limit") { ComposeScreen().also { it.setText(FeedSample.textOf(285)) } },
        HostedPage("Compose, anonymous on", pointer = { 226f to 326f }) {
            ComposeScreen().also { it.setText(FeedSample.textOf(270)); it.setAnonymous(true) }
        },
        HostedPage("Compose, sending") { ComposeScreen().also { it.setText(FeedSample.textOf(142)); it.sendForever() } },
        HostedPage("Compose, announcer") { ComposeScreen(ComposeDraft(), announcer = true, destination = 0) },
        HostedPage("Compose, announcer, Global") {
            ComposeScreen(ComposeDraft(), announcer = true, destination = 1).also { it.setText("Événement PvP dimanche à 15:00 à l'arène. Inscriptions au panneau du spawn.") }
        },
    )

    val all get() = gallery + composed
}

/**
 * The demo's "UI kit" app: a list of the gallery pages and composed screens, each opening on the phone when it is
 * there. Its screens are the same ones the capture harness shows side by side.
 */
class KitIndexScreen : KitScreen() {
    init {
        add(Header(I18n.get("app.lnbphone_demo.kit")).at(0f, 0f))
        val list = add(ScrollList().at(0f, 44f, 256f, 326f))
        for (page in KitPages.all) {
            list.add(ListRow(page.caption) { phone.push(page.create()) }.apply {
                trailing = IconView(Icon.CHEVRON).apply { color = Colors.TEXT_2 }
            })
        }
    }
}
