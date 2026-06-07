package io.github.uttmangosteen.cryptocurrencyPluginMC.item

import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

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
            if (isPrivate) {
                Component.text("秘密鍵付き公開鍵共有用アイテム", NamedTextColor.RED, TextDecoration.BOLD)
            } else {
                Component.text("公開鍵共有用アイテム", NamedTextColor.WHITE, TextDecoration.BOLD)
            },
            Component.text("publicKey:", NamedTextColor.GRAY),
            Component.text(account.publicKey, NamedTextColor.DARK_GRAY)
        )

        if (isPrivate) {
            lore.add(
                Component.text("落とすな、絶対に。", NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.BOLD)
                    .decorate(TextDecoration.UNDERLINED)
            )
            lore.add(Component.text("privateKey:", NamedTextColor.GRAY))
            lore.add(Component.text(privateKey, NamedTextColor.DARK_GRAY))
        }

        return Items.create(
            material = Material.PAPER,
            name = Component.text(memo),
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