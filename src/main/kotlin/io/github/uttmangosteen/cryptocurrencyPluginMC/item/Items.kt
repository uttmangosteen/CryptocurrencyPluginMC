package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.plugin.java.JavaPlugin

object Items {
    private val miniMessage = MiniMessage.miniMessage()

    fun miniMessage(text: String): Component {
        return runCatching {
            miniMessage.deserialize("<!i>$text")
        }.getOrElse {
            Component.text(text)
        }
    }

    @Suppress("UnstableApiUsage")
    fun create(
        material: Material,
        name: Component? = null,
        lore: List<Component>? = null,
        amount: Int = 1,
        customModelData: Float? = null,
        persistentDataContainer: ((PersistentDataContainer) -> Unit)? = null
    ): ItemStack {
        return ItemStack(material, amount).apply {
            editMeta { meta ->
                if (name != null) meta.displayName(name.removeDefaultItalic())
                if (lore != null) meta.lore(lore.map { it.removeDefaultItalic() })

                if (customModelData != null) {
                    val cmd = meta.customModelDataComponent
                    cmd.floats = listOf(customModelData)
                    meta.setCustomModelDataComponent(cmd)
                }

                if (persistentDataContainer != null) {
                    persistentDataContainer(meta.persistentDataContainer)
                }
            }
        }
    }

    fun pdc(item: ItemStack?): PersistentDataContainer? {
        if (item == null || item.type.isAir) return null
        return item.itemMeta?.persistentDataContainer
    }

    private fun Component.removeDefaultItalic(): Component {
        return if (this.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) this.decoration(
            TextDecoration.ITALIC,
            false
        ) else this
    }
}

object ItemKeys {
    fun gpu(plugin: JavaPlugin): GpuKeys {
        return GpuKeys(
            name = NamespacedKey(plugin, "gpu_name"),
            description = NamespacedKey(plugin, "gpu_description"),
            material = NamespacedKey(plugin, "gpu_material"),
            customModelData = NamespacedKey(plugin, "gpu_custom_model_data"),
            life = NamespacedKey(plugin, "gpu_life"),
            breakChance = NamespacedKey(plugin, "gpu_break_chance"),
            power = NamespacedKey(plugin, "gpu_power")
        )
    }

    fun keyItem(plugin: JavaPlugin): KeyItemKeys {
        return KeyItemKeys(
            public = NamespacedKey(plugin, "public_key"),
            private = NamespacedKey(plugin, "private_key")
        )
    }
}