package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.plugin.java.JavaPlugin

object Items {
    fun text(
        content: String,
        color: TextColor? = null,
        vararg decorations: TextDecoration
    ): Component {
        var component = Component.text(content)
        if (color != null) component = component.color(color)
        decorations.forEach { decoration ->
            component = component.decorate(decoration)
        }
        return component
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
                if (name != null) meta.displayName(name)
                if (lore != null) meta.lore(lore)

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
}

object ItemKeys {
    fun gpu(plugin: JavaPlugin): GpuKeys {
        return GpuKeys(
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