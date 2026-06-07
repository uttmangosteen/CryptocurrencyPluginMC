package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

object Items {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun legacy(text: String): Component {
        return legacySerializer.deserialize(text)
    }

    fun legacy(component: Component): String {
        return legacySerializer.serialize(component)
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

    fun createLegacy(
        material: Material,
        name: String? = null,
        lore: List<String>? = null,
        amount: Int = 1,
        customModelData: Float? = null,
        persistentDataContainer: ((PersistentDataContainer) -> Unit)? = null
    ): ItemStack {
        return create(
            material = material,
            name = name?.let { legacy(it) },
            lore = lore?.map { legacy(it) },
            amount = amount,
            customModelData = customModelData,
            persistentDataContainer = persistentDataContainer
        )
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

data class GpuKeys(
    val life: NamespacedKey,
    val breakChance: NamespacedKey,
    val power: NamespacedKey
)

object GpuItems {
    fun create(gpu: Gpu, keys: GpuKeys): ItemStack {
        val lifeColor = when {
            gpu.life > 0 -> "§a"
            gpu.life == 0 -> "§e"
            else -> "§c"
        }

        return Items.createLegacy(
            material = Material.matchMaterial(gpu.material) ?: Material.IRON_INGOT,
            name = gpu.gpuName,
            lore = listOf(
                gpu.description,
                "§flife: $lifeColor${gpu.life}",
                "§fbreakChance: §e${gpu.breakChance}",
                "§fpower: §e${gpu.power}"
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
            ?.let { Items.legacy(it) }
            ?: "§c§l名称未設定"

        val description = meta.lore()
            ?.firstOrNull()
            ?.let { Items.legacy(it) }
            ?: "§c§l説明未設定"

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

data class KeyItemKeys(
    val public: NamespacedKey,
    val private: NamespacedKey
)

object KeyItems {
    fun create(
        account: Account,
        memo: String,
        keys: KeyItemKeys,
        privateKey: String? = null
    ): ItemStack {
        val isPrivate = privateKey != null

        val lore = mutableListOf(
            if (isPrivate) "§c§l秘密鍵付き公開鍵共有用アイテム" else "§f§l公開鍵共有用アイテム",
            "§7publicKey:",
            "§8${account.publicKey}"
        )

        if (isPrivate) {
            lore.add("§4§k§na§4§n落とすな、絶対に。§4§k§na")
            lore.add("§7privateKey:")
            lore.add("§8$privateKey")
        }

        return Items.createLegacy(
            material = Material.PAPER,
            name = memo,
            lore = lore
        ) { pdc ->
            pdc.set(keys.public, PersistentDataType.STRING, account.publicKey)
            if (isPrivate) {
                pdc.set(keys.private, PersistentDataType.STRING, privateKey)
            }
        }
    }

    fun createWithPrivateKey(
        account: Account,
        memo: String,
        keys: KeyItemKeys
    ): ItemStack? {
        val privateKey = account.privateKey ?: return null
        return create(account, memo, keys, privateKey)
    }

    fun readAccount(
        item: ItemStack?,
        keys: KeyItemKeys,
        memo: String = ""
    ): Account? {
        val pdc = Items.pdc(item) ?: return null

        val publicKey = pdc.get(keys.public, PersistentDataType.STRING) ?: return null
        val privateKey = pdc.get(keys.private, PersistentDataType.STRING)

        return Account(
            publicKey = publicKey,
            privateKey = privateKey,
            memo = memo
        )
    }
}