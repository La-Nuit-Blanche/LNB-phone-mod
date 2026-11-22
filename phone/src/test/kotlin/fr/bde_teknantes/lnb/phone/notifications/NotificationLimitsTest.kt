package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.api.AppTarget
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

// Arguments of other types (a StringBuilder, a Character) can't be tested here: outside production, Minecraft's
// TranslatableContents refuses them in its constructor, before NotificationLimits ever sees them.

/** What an app (and through it, its players) can pass to [fr.bde_teknantes.lnb.phone.api.PhoneNotifications.send]. */
class NotificationLimitsTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val ops = RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE)

    /** The plain text the budget counts, in the order it spends it: literal, then arguments, then fallback, then siblings. */
    private fun budgetText(component: Component): String = buildString {
        fun walk(c: Component) {
            when (val contents = c.contents) {
                is PlainTextContents -> append(contents.text())
                is TranslatableContents -> {
                    for (arg in contents.args) {
                        when (arg) {
                            is Component -> walk(arg)
                            is Number -> Unit
                            else -> append(arg.toString())
                        }
                    }
                    contents.fallback?.let(::append)
                }
            }
            c.siblings.forEach(::walk)
        }
        walk(component)
    }

    /** Components in the tree, siblings and component arguments together. */
    private fun parts(component: Component): Int =
        1 + component.siblings.sumOf(::parts) +
            ((component.contents as? TranslatableContents)?.args?.filterIsInstance<Component>()?.sumOf(::parts) ?: 0)

    private fun nested(depth: Int, text: String): MutableComponent {
        var c: MutableComponent = Component.literal(text)
        repeat(depth) { c = Component.literal(text).append(c) }
        return c
    }

    // ---- Lengths ----

    @Test
    fun `a title or text within the limit is kept whole`() {
        val title = "t".repeat(NotificationLimits.MAX_TITLE_LENGTH)
        assertEquals(title, budgetText(NotificationLimits.title(Component.literal(title))))
        val text = "x".repeat(NotificationLimits.MAX_TEXT_LENGTH)
        assertEquals(text, budgetText(NotificationLimits.text(Component.literal(text))))
    }

    @Test
    fun `one character over the limit is cut`() {
        val title = NotificationLimits.title(Component.literal("t".repeat(NotificationLimits.MAX_TITLE_LENGTH + 1)))
        assertEquals(NotificationLimits.MAX_TITLE_LENGTH, budgetText(title).length)
        val text = NotificationLimits.text(Component.literal("x".repeat(NotificationLimits.MAX_TEXT_LENGTH + 1)))
        assertEquals(NotificationLimits.MAX_TEXT_LENGTH, budgetText(text).length)
    }

    @Test
    fun `the length is counted across siblings, in reading order`() {
        val component = Component.literal("a".repeat(100)).append("b".repeat(100)).append("c".repeat(100))
        assertEquals("a".repeat(100) + "b".repeat(28), budgetText(NotificationLimits.title(component)))
    }

    @Test
    fun `the length is counted across nested components`() {
        // Ten levels of 20 characters: 200 in all.
        val component = nested(9, "n".repeat(20))
        val clamped = NotificationLimits.title(component)
        assertEquals(budgetText(component).take(NotificationLimits.MAX_TITLE_LENGTH), budgetText(clamped))
    }

    @Test
    fun `string translation arguments count, numbers do not`() {
        val component = Component.translatable("notif.test", "Mika", 12, 2.5, "x".repeat(400))
        val clamped = NotificationLimits.text(component).contents as TranslatableContents
        assertEquals("notif.test", clamped.key)
        assertEquals(4, clamped.args.size)
        assertEquals("Mika", clamped.args[0])
        assertEquals(12, clamped.args[1])
        assertEquals(2.5, clamped.args[2])
        assertEquals(NotificationLimits.MAX_TEXT_LENGTH - 4, (clamped.args[3] as String).length)
    }

    @Test
    fun `a boolean argument reads the same once saved or sent`() {
        // Minecraft writes a boolean argument as a byte and reads back 1: the player would see "1" for "true".
        val clamped = NotificationLimits.text(Component.translatable("notif.test", true, false))
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        ComponentSerialization.STREAM_CODEC.encode(buf, clamped)
        val sent = (ComponentSerialization.STREAM_CODEC.decode(buf).contents as TranslatableContents).args
        assertEquals(listOf("true", "false"), sent.map { it.toString() })
        val saved = ComponentSerialization.CODEC.parse(ops, ComponentSerialization.CODEC.encodeStart(ops, clamped).getOrThrow()).getOrThrow()
        assertEquals(listOf("true", "false"), (saved.contents as TranslatableContents).args.map { it.toString() })
    }

    @Test
    fun `component arguments count, and arguments come before the fallback`() {
        val component = Component.translatableWithFallback(
            "notif.test",
            "f".repeat(100),
            Component.literal("a".repeat(60)),
            Component.literal("b".repeat(60)),
        )
        val clamped = NotificationLimits.title(component)
        val contents = clamped.contents as TranslatableContents
        assertEquals("a".repeat(60), budgetText(contents.args[0] as Component))
        assertEquals("b".repeat(60), budgetText(contents.args[1] as Component))
        assertEquals("f".repeat(8), contents.fallback)
    }

    @Test
    fun `whatever comes after the budget is spent is emptied, not removed`() {
        val component = Component.literal("x".repeat(200)).append("after").append(Component.translatable("k", "arg"))
        val clamped = NotificationLimits.title(component)
        assertEquals(2, clamped.siblings.size)
        assertEquals("", budgetText(clamped.siblings[0]))
        // The arguments of a translation stay in place, so its %1$s still finds one.
        val translation = clamped.siblings[1].contents as TranslatableContents
        assertEquals(listOf(""), translation.args.toList())
    }

    @Test
    fun `a cut never splits a surrogate pair`() {
        val emoji = "😀" // 😀, two chars
        val component = Component.literal("a".repeat(NotificationLimits.MAX_TITLE_LENGTH - 1) + emoji + "tail")
        val text = budgetText(NotificationLimits.title(component))
        assertEquals("a".repeat(NotificationLimits.MAX_TITLE_LENGTH - 1), text)
        // Exactly at the limit, the pair is kept.
        val fits = Component.literal("a".repeat(NotificationLimits.MAX_TITLE_LENGTH - 2) + emoji)
        assertEquals("a".repeat(NotificationLimits.MAX_TITLE_LENGTH - 2) + emoji, budgetText(NotificationLimits.title(fits)))
    }

    @Test
    fun `French accents and other scripts count one char each`() {
        val component = Component.literal("é".repeat(200))
        assertEquals("é".repeat(NotificationLimits.MAX_TITLE_LENGTH), budgetText(NotificationLimits.title(component)))
    }

    @Test
    fun `a zero budget gives empty text`() {
        assertEquals("", budgetText(NotificationLimits.clamp(Component.literal("abc"), 0)))
    }

    @Test
    fun `an empty component stays empty`() {
        assertEquals("", budgetText(NotificationLimits.text(Component.empty())))
    }

    @Test
    fun `cutting twice changes nothing`() {
        val component = Component.literal("a".repeat(90)).append(Component.translatable("k", "b".repeat(90))).append("c")
        val once = NotificationLimits.title(component)
        assertEquals(once, NotificationLimits.title(once))
    }

    // ---- Parts ----

    @Test
    fun `at most 32 parts are kept, the first ones`() {
        val component = Component.literal("root")
        repeat(100) { component.append(Component.literal("$it")) }
        val clamped = NotificationLimits.text(component)
        assertEquals(NotificationLimits.MAX_PARTS, parts(clamped))
        assertEquals((0 until NotificationLimits.MAX_PARTS - 1).map { "$it" }, clamped.siblings.map(::budgetText))
    }

    @Test
    fun `exactly 32 parts are all kept`() {
        val component = Component.literal("root")
        repeat(NotificationLimits.MAX_PARTS - 1) { component.append(Component.literal("$it")) }
        assertEquals(NotificationLimits.MAX_PARTS, parts(NotificationLimits.text(component)))
    }

    @Test
    fun `the part cap reaches into nesting`() {
        val clamped = NotificationLimits.text(nested(100, "n"))
        assertEquals(NotificationLimits.MAX_PARTS, parts(clamped))
    }

    @Test
    fun `a translation keeps its first 16 arguments`() {
        val args = Array<Any>(40) { Component.literal("$it") }
        val clamped = NotificationLimits.text(Component.translatable("k", *args))
        val kept = (clamped.contents as TranslatableContents).args
        assertEquals(NotificationLimits.MAX_ARGUMENTS, kept.size)
        assertEquals((0 until NotificationLimits.MAX_ARGUMENTS).map { "$it" }, kept.map { budgetText(it as Component) })
    }

    @Test
    fun `every argument is a part, and past the cap arguments keep their places, empty`() {
        // 31 siblings spend the parts after the root; a translation then gets its place but empty arguments.
        val component = Component.literal("root")
        repeat(NotificationLimits.MAX_PARTS - 2) { component.append(Component.literal("$it")) }
        component.append(Component.translatable("k", "s", 3, Component.literal("c")))
        val clamped = NotificationLimits.text(component)
        val translation = clamped.siblings.last().contents as TranslatableContents
        assertEquals(listOf<Any>("", "", ""), translation.args.toList())
        // Strings and numbers spend parts too: a list of them can't grow a notification without bound.
        val many = NotificationLimits.text(Component.translatable("k", *Array<Any>(NotificationLimits.MAX_ARGUMENTS) { "a$it" }))
        val strings = Component.literal("r")
        repeat(3) { strings.append(Component.translatable("k", *Array<Any>(NotificationLimits.MAX_ARGUMENTS) { "a$it" })) }
        val spent = NotificationLimits.text(strings)
        assertEquals(NotificationLimits.MAX_ARGUMENTS, (many.contents as TranslatableContents).args.count { it != "" })
        val nonEmpty = spent.siblings.sumOf { (it.contents as TranslatableContents).args.count { a -> a != "" } }
        assertEquals(NotificationLimits.MAX_PARTS - 1 - 2, nonEmpty) // the root and two of the translations are parts
    }

    @Test
    fun `translation keys share one budget`() {
        val key = "k".repeat(NotificationLimits.MAX_KEY_LENGTH)
        val component = Component.translatable(key).append(Component.translatable(key)).append(Component.translatable(key))
        val clamped = NotificationLimits.text(component)
        assertInstanceOf(TranslatableContents::class.java, clamped.contents)
        assertInstanceOf(TranslatableContents::class.java, clamped.siblings[0].contents)
        // 512 key characters in all: the third key is over.
        assertInstanceOf(PlainTextContents::class.java, clamped.siblings[1].contents)
    }

    @Test
    fun `the largest possible result stays small on the wire`() {
        val key = "k".repeat(NotificationLimits.MAX_KEY_LENGTH)
        val x = "x".repeat(1000)
        val y = Component.literal(x)
        val huge = Component.translatable(key, *Array<Any>(1000) { x })
        val sibling = Component.translatable(key, *Array<Any>(1000) { y })
        repeat(1000) { huge.append(sibling) }
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        ComponentSerialization.STREAM_CODEC.encode(buf, NotificationLimits.text(huge))
        assertTrue(buf.readableBytes() < 4096, "${buf.readableBytes()} bytes")
    }

    @Test
    fun `a deep tree of arguments is bounded`() {
        var c: Component = Component.literal("leaf")
        repeat(500) { c = Component.translatable("k", c) }
        // Must neither overflow the stack nor keep more than the cap.
        val clamped = NotificationLimits.text(c)
        assertTrue(parts(clamped) <= NotificationLimits.MAX_PARTS + 1) // the placeholder after the cap
    }

    // ---- What is stripped ----

    @Test
    fun `no style is kept, not even colours, bold, obfuscation or font`() {
        val style = Style.EMPTY
            .withColor(TextColor.fromRgb(0x3A8DF0))
            .withBold(true)
            .withObfuscated(true)
            .withFont(ResourceLocation.withDefaultNamespace("alt"))
            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/op @s"))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("hover")))
            .withInsertion("insert")
        val arg = Component.literal("c").withStyle(style)
        val component = Component.literal("a").withStyle(style).append(Component.literal("b").withStyle(style))
            .append(Component.translatable("k", arg).withStyle(style))
        val clamped = NotificationLimits.title(component)
        val translatedArg = (clamped.siblings[1].contents as TranslatableContents).args[0] as Component
        for (part in listOf(clamped, clamped.siblings[0], clamped.siblings[1], translatedArg)) assertEquals(Style.EMPTY, part.style)
    }

    @Test
    fun `click events inside translation arguments are dropped too`() {
        val arg = Component.literal("x").withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com")) }
        val clamped = NotificationLimits.title(Component.translatable("k", arg))
        val kept = (clamped.contents as TranslatableContents).args[0] as Component
        assertNull(kept.style.clickEvent)
    }

    @Test
    fun `scores, selectors and NBT lookups become empty, their siblings stay`() {
        val score = Component.score("@a", "kills").append("s")
        val selector = Component.selector("@e", Optional.empty()).append("e")
        val nbt = Component.nbt("Inventory", false, Optional.empty(), net.minecraft.network.chat.contents.EntityDataSource("@p")).append("n")
        for ((component, tail) in listOf(score to "s", selector to "e", nbt to "n")) {
            val clamped = NotificationLimits.title(component)
            assertInstanceOf(PlainTextContents::class.java, clamped.contents)
            assertEquals("", (clamped.contents as PlainTextContents).text())
            assertEquals(tail, budgetText(clamped))
        }
        // Also as an argument.
        val inArg = NotificationLimits.title(Component.translatable("k", Component.selector("@a", Optional.empty())))
        val arg = (inArg.contents as TranslatableContents).args[0] as Component
        assertInstanceOf(PlainTextContents::class.java, arg.contents)
    }

    @Test
    fun `keybinds are kept, overlong keys and keybind names are dropped`() {
        val keybind = NotificationLimits.title(Component.keybind("key.jump"))
        assertEquals("key.jump", (keybind.contents as KeybindContents).name)
        val longKey = "k".repeat(NotificationLimits.MAX_KEY_LENGTH + 1)
        assertInstanceOf(PlainTextContents::class.java, NotificationLimits.title(Component.keybind(longKey)).contents)
        assertInstanceOf(PlainTextContents::class.java, NotificationLimits.title(Component.translatable(longKey, "a")).contents)
        val okKey = "k".repeat(NotificationLimits.MAX_KEY_LENGTH)
        assertInstanceOf(TranslatableContents::class.java, NotificationLimits.title(Component.translatable(okKey)).contents)
    }

    // ---- Copies ----

    @Test
    fun `the result shares nothing an app could change afterwards`() {
        val sibling = Component.literal("sibling")
        val arg = Component.literal("arg")
        val component = Component.literal("root").append(sibling).append(Component.translatable("k", arg))
        val clamped = NotificationLimits.title(component)
        val before = budgetText(clamped)
        component.append("later")
        sibling.append("later")
        arg.append("later")
        assertEquals(before, budgetText(clamped))
    }

    // ---- Encoding ----

    @Test
    fun `what comes out can always be saved and sent`() {
        val samples = listOf<Component>(
            Component.literal("plain"),
            Component.translatable("k", "s", 1, 2L, 3.5, 4.5f, 6.toShort(), 7.toByte(), true, Component.literal("c")),
            Component.translatable("k", BigDecimal("1.5"), BigInteger.TEN, AtomicInteger(3)),
            Component.translatableWithFallback("k", "fallback", "a"),
            Component.keybind("key.jump").append(Component.literal("x").withStyle { it.withBold(true) }),
        )
        for (sample in samples) {
            val clamped = NotificationLimits.text(sample)
            val saved = ComponentSerialization.CODEC.encodeStart(ops, clamped)
            assertTrue(saved.result().isPresent, "can't save $clamped: ${saved.error()}")
            val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            ComponentSerialization.STREAM_CODEC.encode(buf, clamped)
            assertEquals(clamped.string, ComponentSerialization.STREAM_CODEC.decode(buf).string)
        }
    }

    // ---- Addresses ----

    @Test
    fun `targets are refused past their limit, never cut`() {
        assertEquals("t".repeat(AppTarget.MAX_LENGTH), NotificationLimits.checkAddress(feed, "t".repeat(AppTarget.MAX_LENGTH)))
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkAddress(feed, "t".repeat(AppTarget.MAX_LENGTH + 1)) }
        assertNull(NotificationLimits.checkAddress(feed, null))
        assertNull(NotificationLimits.checkAddress(feed, ""))
        // A blank target is a target: only the empty string means none.
        assertEquals(" ", NotificationLimits.checkAddress(feed, " "))
    }

    @Test
    fun `app ids are refused past 128 characters`() {
        // "a:" + path: namespace, colon and path together.
        val ok = ResourceLocation.fromNamespaceAndPath("a", "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH - 2))
        assertNull(NotificationLimits.checkAddress(ok, null))
        val long = ResourceLocation.fromNamespaceAndPath("a", "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH - 1))
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkAddress(long, null) }
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkAddress(long, "ok") }
    }

    @Test
    fun `the target bound send accepts is the one the wire and the saved file take`() {
        val longest = NotificationLimits.checkAddress(feed, "é".repeat(AppTarget.MAX_LENGTH))!!
        val notification = PhoneNotification(1, feed, Component.empty(), Component.empty(), longest, 0, false)
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        NotificationCodecs.STREAM_CODEC.encode(buf, notification)
        assertEquals(longest, NotificationCodecs.STREAM_CODEC.decode(buf).target)
        val saved = NotificationCodecs.CODEC.encodeStart(ops, notification).getOrThrow()
        assertEquals(longest, NotificationCodecs.CODEC.parse(ops, saved).getOrThrow().target)
        // One more is refused by all three.
        val over = "é".repeat(AppTarget.MAX_LENGTH + 1)
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkAddress(feed, over) }
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkTarget(over) }
        assertThrows(Exception::class.java) {
            NotificationCodecs.STREAM_CODEC.encode(RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY), notification.copy(target = over))
        }
        assertTrue(NotificationCodecs.CODEC.encodeStart(ops, notification.copy(target = over)).error().isPresent)
    }

    @Test
    fun `a target to clear can't be empty`() {
        assertThrows(IllegalArgumentException::class.java) { NotificationLimits.checkTarget("") }
        assertEquals("thread:1", NotificationLimits.checkTarget("thread:1"))
    }

    @Test
    fun `only an app id in a loaded mod's namespace may send`() {
        val loaded = setOf("lnbfeed", "minecraft", "neoforge")
        NotificationLimits.checkSender(ResourceLocation.fromNamespaceAndPath("lnbfeed", "feed")) { it in loaded }
        for (namespace in listOf("other", "minecraft", "neoforge")) {
            assertThrows(IllegalArgumentException::class.java) {
                NotificationLimits.checkSender(ResourceLocation.fromNamespaceAndPath(namespace, "feed")) { it in loaded }
            }
        }
    }
}
