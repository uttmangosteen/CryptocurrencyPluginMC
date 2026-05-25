package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class GpuItemFactory(
    plugin: JavaPlugin
) {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    private val lifeKey = NamespacedKey(plugin, "gpu_life")
    private val breakChanceKey = NamespacedKey(plugin, "gpu_break_chance")
    private val powerKey = NamespacedKey(plugin, "gpu_power")

    // アイテム化
    @Suppress("UnstableApiUsage")
    fun createItem(gpu: Gpu): ItemStack {
        val material = Material.matchMaterial(gpu.material) ?: Material.IRON_INGOT

        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(gpu.gpuName))

                val lifeColor = when {
                    gpu.life > 0 -> "§a"
                    gpu.life == 0 -> "§e"
                    else -> "§c"
                }

                meta.lore(
                    listOf(
                        legacySerializer.deserialize(gpu.description),
                        legacySerializer.deserialize("§flife: ${lifeColor}${gpu.life}"),
                        legacySerializer.deserialize("§fbreakChance: §e${gpu.breakChance}"),
                        legacySerializer.deserialize("§fpower: §e${gpu.power}"),
                    )
                )

                if (gpu.customModelData != 0f) {
                    val customModelDataComponent = meta.customModelDataComponent
                    customModelDataComponent.floats = listOf(gpu.customModelData)
                    meta.setCustomModelDataComponent(customModelDataComponent)
                }

                meta.persistentDataContainer.set(lifeKey, PersistentDataType.INTEGER, gpu.life)
                meta.persistentDataContainer.set(breakChanceKey, PersistentDataType.DOUBLE, gpu.breakChance)
                meta.persistentDataContainer.set(powerKey, PersistentDataType.INTEGER, gpu.power)
            }
        }
    }

    // Machineに差し込むとき用
    @Suppress("UnstableApiUsage")
    fun readGpu(item: ItemStack?): Gpu? {
        if (item == null || item.type.isAir) return null

        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer

        val life = container.get(lifeKey, PersistentDataType.INTEGER) ?: return null
        val breakChance = container.get(breakChanceKey, PersistentDataType.DOUBLE) ?: return null
        val power = container.get(powerKey, PersistentDataType.INTEGER) ?: return null

        val gpuName = meta.displayName()?.let {
            legacySerializer.serialize(it)
        } ?: "§c§l名称未設定"

        val description = meta.lore()?.firstOrNull()?.let {
            legacySerializer.serialize(it)
        } ?: "§c§l説明未設定"

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
}