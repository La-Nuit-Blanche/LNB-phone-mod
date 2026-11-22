package fr.bde_teknantes.lnb.phone.client.shell

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import org.lwjgl.glfw.GLFW

/** The phone's key: Up arrow by default, in its own category of the controls screen. */
internal object PhoneKeys {
    val OPEN = KeyMapping("key.lnbphone.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, "key.categories.lnbphone")

    fun register(event: RegisterKeyMappingsEvent) {
        event.register(OPEN)
    }
}
