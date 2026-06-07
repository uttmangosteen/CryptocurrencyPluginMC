package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class GpuKeys(
    val life: NamespacedKey,
    val breakChance: NamespacedKey,
    val power: NamespacedKey
)

object GpuItems {
    fun create(gpu: Gpu, keys: GpuKeys): ItemStack {
        val lifeColor = when {
            gpu.life > 0 -> NamedTextColor.GREEN
            gpu.life == 0 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }

        return Items.create(
            material = Material.matchMaterial(gpu.material) ?: Material.IRON_INGOT,
            name = Component.text(gpu.gpuName),
            lore = listOf(
                Component.text(gpu.description),
                Component.text("life: ", NamedTextColor.WHITE)
                    .append(Component.text(gpu.life.toString(), lifeColor)),
                Component.text("breakChance: ", NamedTextColor.WHITE)
                    .append(Component.text(gpu.breakChance.toString(), NamedTextColor.YELLOW)),
                Component.text("power: ", NamedTextColor.WHITE)
                    .append(Component.text(gpu.power.toString(), NamedTextColor.YELLOW))
            ),
            customModelData = if (gpu.customModelData != 0f) gpu.customModelData else null
        ) { pdc ->
            pdc.set(keys.life, PersistentDataType.INTEGER, gpu.life)
            pdc.set(keys.breakChance, PersistentDataType.DOUBLE, gpu.breakChance)
            pdc.set(keys.power, PersistentDataType.INTEGER, gpu.power)
        }
    }

    @Suppress("UnstableApiUsage")
    fun read(item: ItemStack?, keys: GpuKeys): Gpu? {
        if (item == null || item.type.isAir) return null

        val meta = item.itemMeta ?: return null
        val pdc = meta.persistentDataContainer

        val life = pdc.get(keys.life, PersistentDataType.INTEGER) ?: return null
        val breakChance = pdc.get(keys.breakChance, PersistentDataType.DOUBLE) ?: return null
        val power = pdc.get(keys.power, PersistentDataType.INTEGER) ?: return null

        val gpuName = meta.displayName()
            ?.let { plainText(it) }
            ?: "名称未設定"

        val description = meta.lore()
            ?.firstOrNull()
            ?.let { plainText(it) }
            ?: "説明未設定"

        val customModelData = meta.customModelDataComponent.floats.firstOrNull() ?: 0f

        return Gpu(
            gpuName = gpuName,
            material = item.type.name,
            customModelData = customModelData,
            description = description,
            life = life,
            breakChance = breakChance,
            power = power
        )
    }

    private fun plainText(component: Component): String {
        return component.children().fold(component.toString()) { text, child ->
            text + child.toString()
        }
    }
}