package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class GpuKeys(
    val name: NamespacedKey,
    val description: NamespacedKey,
    val material: NamespacedKey,
    val customModelData: NamespacedKey,
    val life: NamespacedKey,
    val breakChance: NamespacedKey,
    val power: NamespacedKey
)

object GpuItems {
    fun create(gpu: Gpu, keys: GpuKeys): ItemStack {
        val lifeColor = when {
            gpu.life > 0 -> "<green>"
            gpu.life == 0 -> "<yellow>"
            else -> "<red>"
        }

        return Items.create(
            material = Material.matchMaterial(gpu.material) ?: Material.IRON_INGOT,
            name = Items.miniMessage(gpu.gpuName),
            lore = listOf(
                Items.miniMessage(gpu.description),
                Items.miniMessage("<gray>life: ${lifeColor}${gpu.life}"),
                Items.miniMessage("<gray>breakChance: <yellow>${gpu.breakChance}"),
                Items.miniMessage("<gray>power: <yellow>${gpu.power}")
            ),
            customModelData = if (gpu.customModelData != 0f) gpu.customModelData else null
        ) { pdc ->
            pdc.set(keys.name, PersistentDataType.STRING, gpu.gpuName)
            pdc.set(keys.description, PersistentDataType.STRING, gpu.description)
            pdc.set(keys.material, PersistentDataType.STRING, gpu.material)
            pdc.set(keys.customModelData, PersistentDataType.FLOAT, gpu.customModelData)
            pdc.set(keys.life, PersistentDataType.INTEGER, gpu.life)
            pdc.set(keys.breakChance, PersistentDataType.DOUBLE, gpu.breakChance)
            pdc.set(keys.power, PersistentDataType.INTEGER, gpu.power)
        }
    }

    fun read(item: ItemStack?, keys: GpuKeys): Gpu? {
        if (item == null || item.type.isAir) return null

        val meta = item.itemMeta ?: return null
        val pdc = meta.persistentDataContainer

        val gpuName = pdc.get(keys.name, PersistentDataType.STRING) ?: return null
        val description = pdc.get(keys.description, PersistentDataType.STRING) ?: return null
        val material = pdc.get(keys.material, PersistentDataType.STRING) ?: item.type.name
        val customModelData = pdc.get(keys.customModelData, PersistentDataType.FLOAT) ?: 0f
        val life = pdc.get(keys.life, PersistentDataType.INTEGER) ?: return null
        val breakChance = pdc.get(keys.breakChance, PersistentDataType.DOUBLE) ?: return null
        val power = pdc.get(keys.power, PersistentDataType.INTEGER) ?: return null

        return Gpu(
            gpuName = gpuName,
            material = material,
            customModelData = customModelData,
            description = description,
            life = life,
            breakChance = breakChance,
            power = power
        )
    }
}