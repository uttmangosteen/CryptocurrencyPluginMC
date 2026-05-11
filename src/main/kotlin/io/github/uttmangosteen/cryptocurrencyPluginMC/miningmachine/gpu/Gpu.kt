package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ThreadLocalRandom

data class Gpu(
    var gpuName: String, // GPU名 gpu.ymlのnameの部分
    var material: String,
    var customModelData: Float = 0f,
    var description: String,
    var life: Int, // 耐久値 (0=寿命切れ、-1=壊れた)
    var breakChance: Double, // 耐久値が0のときに掘って壊れる確率 (0.0〜1.0)
    var power: Int, // このGPUが掘る度回せるnonce数
) {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    // GPU が採掘可能か
    fun isActive(): Boolean {
        return life >= 0
    }

    // 掘る度呼ばれる耐久値の消費処理
    fun consumeLife() {
        if (!isActive()) return
        if (life != 0) {
            life--
            return
        }
        if (ThreadLocalRandom.current().nextDouble() < breakChance) life = -1
    }

    //machine表示用アイテム
    fun getItemInMachine(): ItemStack {
        val material = Material.matchMaterial(material) ?: Material.IRON_INGOT
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(gpuName))
                //TODO:MachineGUI作成時要調整
            }
        }
    }

    //アイテム化
    @Suppress("UnstableApiUsage")
    fun createItem(plugin: JavaPlugin): ItemStack {
        val lifeKey = NamespacedKey(plugin, "gpu_life")
        val breakChanceKey = NamespacedKey(plugin, "gpu_break_chance")
        val powerKey = NamespacedKey(plugin, "gpu_power")

        val material = Material.matchMaterial(material) ?: Material.IRON_INGOT

        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(gpuName))

                //TODO: ロア清書
                meta.lore(listOf(legacySerializer.deserialize(description)))

                if (customModelData != 0f) {
                    val customModelDataComponent = meta.customModelDataComponent
                    customModelDataComponent.floats = listOf(customModelData)
                    meta.setCustomModelDataComponent(customModelDataComponent)
                }

                meta.persistentDataContainer.set(lifeKey, PersistentDataType.INTEGER, life)
                meta.persistentDataContainer.set(breakChanceKey, PersistentDataType.DOUBLE, breakChance)
                meta.persistentDataContainer.set(powerKey, PersistentDataType.INTEGER, power)
            }
        }
    }

    //Machineに差し込むとき用
    @Suppress("UnstableApiUsage")
    fun createGpu(item: ItemStack?, plugin: JavaPlugin): Gpu? {
        if (item == null || item.type.isAir) return null
        val meta = item.itemMeta ?: return null

        val lifeKey = NamespacedKey(plugin, "gpu_life")
        val breakChanceKey = NamespacedKey(plugin, "gpu_break_chance")
        val powerKey = NamespacedKey(plugin, "gpu_power")

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