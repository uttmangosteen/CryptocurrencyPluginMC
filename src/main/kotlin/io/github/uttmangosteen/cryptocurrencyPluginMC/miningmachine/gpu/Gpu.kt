package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ThreadLocalRandom

data class Gpu(
    var gpuName: String, // GPU名 gpu.ymlのnameの部分、null = 空スロット
    var material: String,
    var customModelData: Int = 0,
    var description: String,
    var life: Int, // 耐久値 (0=寿命切れ、-1=壊れた)
    var breakChance: Double, // 耐久値 0 のときにほるたび壊れる確率 (0.0〜1.0)
    var power: Int, // このGPUがひとほりで回せるnonce数
) {
    // GPU が壊れているか
    fun isBroken(): Boolean {
        return life < 0
    }

    // GPU が寿命ギリギリか(life0でもほれるが確率で壊れる)
    fun isEndOfLife(): Boolean {
        return life == 0
    }

    // GPUがアクティブか(セットされていて、壊れてなくて、powerが1以上であること)
    fun isActive(): Boolean {
        return life >= 0 && power > 0
    }

    // ほるたびに呼ばれる耐久値の消費処理
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
                meta.displayName(Component.text(gpuName))
                //TODO:MachineGUI作成時要調整
            }
        }
    }

    //アイテム化
    fun createItem(plugin: JavaPlugin): ItemStack {
        val lifeKey = NamespacedKey(plugin, "gpu_life")
        val breakChanceKey = NamespacedKey(plugin, "gpu_break_chance")
        val powerKey = NamespacedKey(plugin, "gpu_power")

        val material = Material.matchMaterial(material) ?: Material.IRON_INGOT
        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(gpuName))

                //TODO: アイテムのロア清書
                meta.lore(listOf(Component.text(description)))

                @Suppress("DEPRECATION")
                meta.setCustomModelData(customModelData)

                meta.persistentDataContainer.set(lifeKey, PersistentDataType.INTEGER, life)
                meta.persistentDataContainer.set(breakChanceKey, PersistentDataType.DOUBLE, breakChance)
                meta.persistentDataContainer.set(powerKey, PersistentDataType.INTEGER, power)
            }
        }
    }

    //Machineに差し込むとき用
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

        @Suppress("DEPRECATION")
        val gpuName = meta.displayName

        @Suppress("DEPRECATION")
        val description = meta.lore?.firstOrNull() ?: ""

        @Suppress("DEPRECATION")
        val customModelData = if (meta.hasCustomModelData()) meta.customModelData else 0

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