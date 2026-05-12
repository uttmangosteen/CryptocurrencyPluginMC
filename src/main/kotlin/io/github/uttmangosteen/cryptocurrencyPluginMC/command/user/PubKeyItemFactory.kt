package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class PubKeyItemFactory(
    private val plugin: Main
) {
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    fun create(account: Account, memo: String): ItemStack {
        val publicKeyKey = NamespacedKey(plugin, "public_key")
        return ItemStack(Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(legacySerializer.deserialize(memo))
                //TODO: ロア清書
                meta.lore(listOf(
                    legacySerializer.deserialize("§7publicKey:"),
                    legacySerializer.deserialize("§8${account.publicKeyHex}")
                ))
                meta.persistentDataContainer.set(publicKeyKey, PersistentDataType.STRING, account.publicKeyHex)
            }
        }
    }
}