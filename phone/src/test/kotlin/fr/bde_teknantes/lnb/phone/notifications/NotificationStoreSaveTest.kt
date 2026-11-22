package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.random.Random

/** When the tests' notifications are loaded: their own time, so none has expired. */
private const val NOW = 1000L

/** The store as written with the world and read back, including files it didn't write cleanly. */
class NotificationStoreSaveTest {
    private val ops = RegistryAccess.EMPTY.createSerializationContext(NbtOps.INSTANCE)
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val mail = ResourceLocation.fromNamespaceAndPath("test", "mail")
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)
    private val carol = UUID(0, 3)

    private fun NotificationStore.add(player: UUID, app: ResourceLocation = feed, target: String? = null, delivered: Boolean = false) =
        add(player, app, Component.literal("t"), Component.literal("x"), target, 1000L, delivered)

    private fun load(tag: CompoundTag, errors: MutableList<String> = mutableListOf()) =
        NotificationStore.load(tag, ops, now = NOW) { errors += it }

    /** The saved player entries, by UUID, for a test to damage. */
    private fun CompoundTag.entry(player: UUID): CompoundTag {
        val players = getList("players", Tag.TAG_COMPOUND.toInt())
        return (0 until players.size).map(players::getCompound).first { it.getUUID("player") == player }
    }

    private fun CompoundTag.notifications(player: UUID): ListTag = entry(player).getList("notifications", Tag.TAG_COMPOUND.toInt())

    private fun threePlayers(): NotificationStore = NotificationStore().apply {
        add(alice, feed, "thread:1")
        add(alice, mail)
        add(bob, feed)
        add(carol, mail, delivered = true)
        add(carol, feed)
    }

    // ---- Round trip ----

    @Test
    fun `every field survives a save and a load`() {
        val store = NotificationStore()
        // As the API stores them: through the limits, so without styles. A component argument that is more than plain
        // text (a plain one is saved as a string, and comes back as one: same text).
        val title = NotificationLimits.title(Component.translatable("notif.test.title"))
        val post = Component.translatable("notif.test.post", "Je l'ai vu près du moulin, côté est !")
        val text = NotificationLimits.text(
            Component.translatable("notif.test.text", "Mika Laurent", post, 3, 2.5).append(Component.keybind("key.jump")),
        )
        val n = store.add(alice, mail, title, text, "thread:8/reply:4", 1_763_800_000_000L, delivered = true)
        val empty = store.add(alice, feed, Component.empty(), Component.empty(), null, 0L, delivered = false)
        val loaded = load(store.save(ops))
        assertEquals(listOf(n, empty), loaded.of(alice))
    }

    @Test
    fun `the round trip also holds through the bytes of a file`() {
        val store = threePlayers()
        val out = ByteArrayOutputStream()
        NbtIo.write(store.save(ops), DataOutputStream(out))
        val tag = NbtIo.read(DataInputStream(ByteArrayInputStream(out.toByteArray())))
        val loaded = load(tag)
        for (player in listOf(alice, bob, carol)) assertEquals(store.of(player), loaded.of(player))
    }

    @Test
    fun `ids carry on after a load, never repeating`() {
        val store = threePlayers()
        store.clearApp(carol, feed) // the highest id is gone, the counter still knows it
        val loaded = load(store.save(ops))
        assertEquals(store.nextId, loaded.nextId)
        val next = loaded.add(alice)
        assertEquals(6L, next.id)
    }

    @Test
    fun `an empty store saves and loads as empty`() {
        val loaded = load(NotificationStore().save(ops))
        assertEquals(emptySet<UUID>(), loaded.playersWithNotifications)
        assertEquals(1L, loaded.nextId)
    }

    @Test
    fun `a full player saves and loads at capacity, in order`() {
        val store = NotificationStore()
        repeat(60) { store.add(alice) }
        val loaded = load(store.save(ops))
        assertEquals((11L..60L).toList(), loaded.of(alice).map { it.id })
    }

    @Test
    fun `saving twice gives the same tag`() {
        val store = threePlayers()
        assertEquals(store.save(ops), load(store.save(ops)).save(ops))
    }

    // ---- Files from elsewhere ----

    @Test
    fun `a world without the file starts empty`() {
        val errors = mutableListOf<String>()
        val loaded = load(CompoundTag(), errors)
        assertEquals(emptySet<UUID>(), loaded.playersWithNotifications)
        assertEquals(1L, loaded.nextId)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a lost counter restarts above the highest stored id`() {
        val tag = threePlayers().save(ops)
        tag.remove("next_id")
        assertEquals(6L, load(tag).nextId)
    }

    @Test
    fun `a counter behind the stored ids is moved past them`() {
        val tag = threePlayers().save(ops)
        tag.putLong("next_id", 2)
        assertEquals(6L, load(tag).nextId)
        tag.putLong("next_id", -50)
        assertEquals(6L, load(tag).nextId)
    }

    @Test
    fun `a counter of the wrong type is ignored`() {
        val tag = NotificationStore().save(ops)
        tag.putString("next_id", "seven")
        assertEquals(1L, load(tag).nextId)
    }

    @Test
    fun `numbers saved as ints and a missing delivered flag still load`() {
        val tag = CompoundTag()
        val entry = CompoundTag()
        entry.putUUID("player", alice)
        val n = CompoundTag()
        n.put("id", IntTag.valueOf(7))
        n.putString("app", "test:feed")
        n.putString("title", "plain title")
        n.putString("text", "plain text")
        n.put("time", IntTag.valueOf(1000))
        entry.put("notifications", ListTag().apply { add(n) })
        tag.put("players", ListTag().apply { add(entry) })
        val errors = mutableListOf<String>()
        val loaded = load(tag, errors)
        val only = loaded.of(alice).single()
        assertEquals(7L, only.id)
        assertEquals("plain title", only.title.string)
        assertEquals(null, only.target)
        assertFalse(only.delivered)
        assertEquals(8L, loaded.nextId)
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `a player entry without a UUID is skipped and reported, the others load`() {
        val store = threePlayers()
        val tag = store.save(ops)
        tag.entry(bob).remove("player")
        val errors = mutableListOf<String>()
        val loaded = load(tag, errors)
        assertEquals(store.of(alice), loaded.of(alice))
        assertEquals(store.of(carol), loaded.of(carol))
        assertEquals(emptyList<PhoneNotification>(), loaded.of(bob))
        assertEquals(1, errors.size)
        // Its ids still count: none is given again.
        assertEquals(6L, loaded.nextId)
    }

    @Test
    fun `a UUID written as a string is skipped and reported`() {
        val tag = threePlayers().save(ops)
        tag.entry(bob).putString("player", bob.toString())
        val errors = mutableListOf<String>()
        val loaded = load(tag, errors)
        assertEquals(emptyList<PhoneNotification>(), loaded.of(bob))
        assertEquals(2, loaded.of(alice).size)
        assertEquals(1, errors.size)
    }

    @Test
    fun `one bad notification is dropped, its neighbours and the other players load`() {
        val store = threePlayers()
        val damages: List<Pair<String, (CompoundTag) -> Unit>> = listOf(
            "no title" to { it.remove("title") },
            "no text" to { it.remove("text") },
            "no id" to { it.remove("id") },
            "no app" to { it.remove("app") },
            "no time" to { it.remove("time") },
            "malformed app id" to { it.putString("app", "Not An Id!") },
            "app id of the wrong type" to { it.putInt("app", 3) },
            "title of the wrong type" to { it.putInt("title", 3) },
            "target too long" to { it.putString("target", "t".repeat(257)) },
            "target of the wrong type" to { it.putInt("target", 3) },
            "id of the wrong type" to { it.putString("id", "one") },
            "delivered of the wrong type" to { it.putString("delivered", "yes") },
        )
        for ((what, damage) in damages) {
            val tag = store.save(ops)
            damage(tag.notifications(alice).getCompound(0))
            val errors = mutableListOf<String>()
            val loaded = load(tag, errors)
            assertEquals(store.of(alice).drop(1), loaded.of(alice), what)
            assertEquals(store.of(bob), loaded.of(bob), what)
            assertEquals(store.of(carol), loaded.of(carol), what)
            assertEquals(1, errors.size, what)
        }
    }

    @Test
    fun `a notifications list of the wrong type loses that player only`() {
        val store = threePlayers()
        val tag = store.save(ops)
        tag.entry(bob).put("notifications", ListTag().apply { add(StringTag.valueOf("junk")) })
        val loaded = load(tag)
        assertEquals(emptyList<PhoneNotification>(), loaded.of(bob))
        assertEquals(store.of(alice), loaded.of(alice))
        assertEquals(store.of(carol), loaded.of(carol))
    }

    @Test
    fun `a players list of the wrong type loads as empty without failing`() {
        val tag = threePlayers().save(ops)
        tag.putString("players", "junk")
        val loaded = load(tag)
        assertEquals(emptySet<UUID>(), loaded.playersWithNotifications)
        // The counter still keeps ids from repeating.
        assertEquals(6L, loaded.nextId)
    }

    @Test
    fun `stored notifications out of order or repeated load sorted and once`() {
        val store = NotificationStore()
        repeat(3) { store.add(alice) }
        val tag = store.save(ops)
        val list = tag.notifications(alice)
        val reversed = ListTag().apply { for (i in list.indices.reversed()) add(list[i].copy()) }
        reversed.add(list[0].copy())
        tag.entry(alice).put("notifications", reversed)
        assertEquals(store.of(alice), load(tag).of(alice))
    }

    @Test
    fun `more stored notifications than the capacity keep the newest`() {
        val big = NotificationStore(capacity = 80)
        repeat(80) { big.add(alice) }
        val loaded = load(big.save(ops))
        assertEquals((31L..80L).toList(), loaded.of(alice).map { it.id })
        assertEquals(81L, loaded.nextId)
    }

    @Test
    fun `no damage to one notification's fields makes the load fail or touch other players`() {
        // Each field of one of Alice's notifications replaced by each kind of tag.
        val store = threePlayers()
        val replacements: List<Tag> = listOf(
            StringTag.valueOf(""), StringTag.valueOf("{\"text\":"), IntTag.valueOf(-1), LongTag.valueOf(Long.MIN_VALUE),
            ByteTag.valueOf(2), ListTag(), CompoundTag(), ListTag().apply { add(IntTag.valueOf(1)) },
        )
        val fields = store.save(ops).notifications(alice).getCompound(0).allKeys.toList()
        for (field in fields) {
            for (replacement in replacements) {
                val tag = store.save(ops)
                tag.notifications(alice).getCompound(0).put(field, replacement.copy())
                val loaded = load(tag)
                assertEquals(store.of(bob), loaded.of(bob), "$field = $replacement")
                assertEquals(store.of(carol), loaded.of(carol), "$field = $replacement")
                assertEquals(store.of(alice)[1], loaded.of(alice).last(), "$field = $replacement")
                assertTrue(loaded.nextId >= store.nextId, "$field = $replacement")
            }
        }
    }

    @Test
    fun `random damage anywhere in the file never makes the load fail`() {
        val random = Random(20261122)
        val store = threePlayers()
        val junk: List<() -> Tag> = listOf(
            { StringTag.valueOf("junk") }, { IntTag.valueOf(random.nextInt()) }, { ListTag() }, { CompoundTag() },
            { LongTag.valueOf(random.nextLong()) },
        )
        repeat(500) {
            val tag = store.save(ops)
            // Walk down to a random compound and damage one of its keys.
            var target: CompoundTag = tag
            while (true) {
                val children = target.allKeys.mapNotNull { key ->
                    when (val child = target.get(key)) {
                        is CompoundTag -> child
                        is ListTag -> child.filterIsInstance<CompoundTag>().randomOrNull(random)
                        else -> null
                    }
                }
                if (children.isEmpty() || random.nextInt(3) == 0) break
                target = children.random(random)
            }
            val key = target.allKeys.randomOrNull(random) ?: return@repeat
            if (random.nextBoolean()) target.remove(key) else target.put(key, junk.random(random)())
            load(tag) // must not throw
        }
    }

    // ---- Checked again on load ----

    @Test
    fun `what a hand edit or an older build saved is made valid again or dropped`() {
        val store = threePlayers()
        val tag = store.save(ops)
        val notifications = tag.notifications(alice)
        // An app id too long for the network: dropped, reported.
        notifications.getCompound(0).putString("app", "test:" + "p".repeat(NotificationLimits.MAX_APP_ID_LENGTH))
        // A text far over its limit, with a style, and an empty target: cut, unstyled, no target.
        val long = Component.literal("x".repeat(5000)).withStyle { it.withBold(true) }
        notifications.getCompound(1).put("text", ComponentSerialization.CODEC.encodeStart(ops, long).getOrThrow())
        notifications.getCompound(1).putString("target", "")
        val errors = mutableListOf<String>()
        val loaded = load(tag, errors)
        assertEquals(1, errors.size)
        val kept = loaded.of(alice).single()
        assertEquals(store.of(alice)[1].id, kept.id)
        assertEquals(NotificationLimits.MAX_TEXT_LENGTH, kept.text.string.length)
        assertEquals(Style.EMPTY, kept.text.style)
        assertEquals(null, kept.target)
        // The dropped one's id still counts.
        assertEquals(store.nextId, loaded.nextId)
    }

    @Test
    fun `two entries for one player are merged`() {
        val tag = threePlayers().save(ops)
        val players = tag.getList("players", Tag.TAG_COMPOUND.toInt())
        val again = NotificationStore().apply { repeat(7) { add(bob) } }.save(ops).entry(bob)
        // Ids 1 to 7 again: one of them (3) is Bob's own notification, and loads once.
        players.add(again)
        val loaded = load(tag)
        assertEquals((1L..7L).toList(), loaded.of(bob).map { it.id })
    }

    @Test
    fun `a notification that can't be written is left out and reported`() {
        val store = NotificationStore()
        store.add(alice, target = "ok")
        store.add(alice, target = "t".repeat(300))
        val errors = mutableListOf<String>()
        val tag = store.save(ops) { errors += it }
        assertEquals(1, errors.size)
        assertEquals(listOf("ok"), load(tag).of(alice).map { it.target })
    }

    @Test
    fun `the saved tag is a copy, and a change is saved next time`() {
        val store = threePlayers()
        val first = store.save(ops)
        first.notifications(alice).clear()
        assertEquals(store.of(alice), load(store.save(ops)).of(alice))
        store.add(alice)
        assertEquals(3, load(store.save(ops)).of(alice).size)
        store.clearApp(alice, feed)
        assertEquals(listOf(mail), load(store.save(ops)).of(alice).map { it.appId })
        store.markDelivered(bob)
        assertTrue(load(store.save(ops)).of(bob).all { it.delivered })
    }

    // ---- Expiry ----

    @Test
    fun `notifications older than 30 days are dropped, on load and on demand`() {
        val day = 24 * 60 * 60 * 1000L
        val store = NotificationStore()
        store.add(alice, feed, Component.literal("t"), Component.literal("old"), null, 0L, false)
        store.add(alice, feed, Component.literal("t"), Component.literal("new"), null, 10 * day, false)
        store.add(bob, feed, Component.literal("t"), Component.literal("old"), null, 0L, false)
        val loaded = NotificationStore.load(store.save(ops), ops, now = 31 * day)
        assertEquals(listOf("new"), loaded.of(alice).map { it.text.string })
        assertEquals(setOf(alice), loaded.playersWithNotifications)

        // On demand, one player or all; exactly 30 days old is kept; a clock set back keeps everything.
        assertEquals(0, store.expire(-5 * day))
        assertEquals(1, store.expire(30 * day + 1, alice))
        assertEquals(listOf("new"), store.of(alice).map { it.text.string })
        assertEquals(1, store.of(bob).size)
        assertEquals(0, store.expire(30 * day))
        assertEquals(2, store.expire(40 * day + 1))
        assertEquals(emptySet<UUID>(), store.playersWithNotifications)
    }
}
