package io.github.uttmangosteen.cryptocurrencyPluginMC.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class KeyItemFactory(
    plugin: Main
) {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    private val publicKeyKey = NamespacedKey(plugin, "public_key")
    private val privateKeyKey = NamespacedKey(plugin, "private_key")

    fun create(account: Account, memo: String): ItemStack {
        return ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(memo))
                meta.lore(listOf(
                    legacySerializer.deserialize("§f§l公開鍵共有用アイテム"),
                    legacySerializer.deserialize("§7publicKey:"),
                    legacySerializer.deserialize("§8${account.publicKey}")
                ))
                meta.persistentDataContainer.set(publicKeyKey, PersistentDataType.STRING, account.publicKey)
            }
        }
    }

    fun createWithPrivateKey(account: Account, memo: String): ItemStack? {
        val privateKeyHex = account.privateKey ?: return null

        return ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(memo))
                meta.lore(listOf(
                    legacySerializer.deserialize("§c§l秘密鍵付き公開鍵共有用アイテム"),
                    legacySerializer.deserialize("§4§k§na§4§n落とすな、絶対に。§4§k§na"),
                    legacySerializer.deserialize("§7publicKey:"),
                    legacySerializer.deserialize("§8${account.publicKey}"),
                    legacySerializer.deserialize("§7privateKey:"),
                    legacySerializer.deserialize("§8$privateKeyHex")
                ))
                meta.persistentDataContainer.set(publicKeyKey, PersistentDataType.STRING, account.publicKey)
                meta.persistentDataContainer.set(privateKeyKey, PersistentDataType.STRING, privateKeyHex)
            }
        }
    }

    fun readAccount(itemStack: ItemStack, memo: String = ""): Account? {
        val meta = itemStack.itemMeta ?: return null
        val container = meta.persistentDataContainer

        val publicKey = container.get(publicKeyKey, PersistentDataType.STRING) ?: return null
        val privateKey = container.get(privateKeyKey, PersistentDataType.STRING)

        return Account(
            publicKey = publicKey,
            privateKey = privateKey,
            memo = memo
        )
    }
}