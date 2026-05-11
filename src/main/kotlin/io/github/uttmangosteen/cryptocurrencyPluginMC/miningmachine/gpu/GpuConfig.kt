package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class GpuConfig(
    private val gpus: Map<String, Gpu>
) {

    fun getGpu(type: String): Gpu? {
        return gpus[type.lowercase()]?.copy()
    }

    fun getTypes(): List<String> {
        return gpus.keys.sorted()
    }

    companion object {
        fun load(plugin: JavaPlugin): GpuConfig {
            val file = File(plugin.dataFolder, "gpu.yml")
            if (!file.exists()) plugin.saveResource("gpu.yml", false)
            val config = YamlConfiguration.loadConfiguration(file)
            val section = config.getConfigurationSection("gpus") ?: return GpuConfig(emptyMap())

            val gpuType = section.getKeys(false).mapNotNull { key ->
                val path = "gpus.$key"

                val gpuName = config.getString("$path.name") ?: return@mapNotNull null
                val materialName = config.getString("$path.material", Material.NETHER_STAR.name)
                    ?.uppercase()
                    ?: Material.NETHER_STAR.name
                val material = Material.matchMaterial(materialName) ?: return@mapNotNull null
                val customModelData: Float = config.getDouble("$path.custom-model-data", 0.0).toFloat()
                val description = config.getString("$path.description", "§c§l名称未設定") ?: return@mapNotNull null
                val life = config.getInt("$path.life", -1)
                val breakChance = config.getDouble("$path.break-chance", 0.0)
                val power = config.getInt("$path.power", 0)

                // 不正な値の GPU は登録しない
                if (key.isBlank()) return@mapNotNull null
                if (life < 0) return@mapNotNull null
                if (breakChance !in 0.0..1.0) return@mapNotNull null
                if (power <= 0) return@mapNotNull null
                if (customModelData < 0) return@mapNotNull null

                key.lowercase() to Gpu(
                    gpuName = gpuName,
                    material = material.name,
                    customModelData = customModelData,
                    description = description,
                    life = life,
                    breakChance = breakChance,
                    power = power,
                )
            }.toMap()

            return GpuConfig(gpuType)
        }
    }
}