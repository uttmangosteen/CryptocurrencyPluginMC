package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player

class WalletAccountGui(
    private val plugin: Main,
    private val wallet: Wallet,
    private val accountIndex: Int
) : Gui(
    3,
    Component.text("口座アクション")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {

    init {
        update()
    }

    private fun update() {
        val inv = inventory
        inv.clear()

        val account = wallet.accounts.getOrNull(accountIndex) ?: return
        val isMain = accountIndex == 0

        val titleText = if (account.memo.isNotEmpty()) account.memo else "口座 #${accountIndex + 1}"
        val infoLore = mutableListOf<Component>()
        if (isMain) infoLore.add(Component.text("⭐ [メイン口座]", NamedTextColor.GREEN))
        infoLore.add(Component.text("PublicKey: ${account.publicKey.take(16)}...", NamedTextColor.GRAY))
        infoLore.add(
            Component.text(
                "PrivateKey: ${if (account.privateKey != null) "登録済" else "なし (Watch Only)"}",
                NamedTextColor.GRAY
            )
        )

        val infoItem = Items.create(
            material = Material.PAPER,
            name = Component.text(titleText, NamedTextColor.WHITE, TextDecoration.BOLD),
            lore = infoLore
        )
        setItem(4, infoItem)

        val setMainItem = Items.create(
            material = if (isMain) Material.LIME_DYE else Material.GRAY_DYE,
            name = Component.text("メイン口座に設定")
                .color(if (isMain) NamedTextColor.GREEN else NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD),
            lore = listOf(
                if (isMain) {
                    Component.text("現在この口座はメイン口座です", NamedTextColor.GRAY)
                } else {
                    Component.text("▶ クリックしてメイン口座に切り替え", NamedTextColor.YELLOW)
                }
            )
        )
        setItem(10, setMainItem) { event ->
            val player = event.whoClicked as Player
            if (!isMain) {
                player.performCommand("cc account main $accountIndex")
                player.closeInventory()
            }
        }

        val getPubKeyItem = Items.create(
            material = Material.NAME_TAG,
            name = Component.text("公開鍵アイテムを取得", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(Component.text("▶ クリックして取得", NamedTextColor.YELLOW))
        )
        setItem(12, getPubKeyItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account getPubKeyItem $accountIndex")
        }

        val getPrivKeyItem = Items.create(
            material = Material.TRIPWIRE_HOOK,
            name = Component.text("秘密鍵アイテムを取得", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして取得", NamedTextColor.YELLOW),
                Component.text("※ 取扱注意 ※", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            )
        )
        setItem(14, getPrivKeyItem) { event ->
            val player = event.whoClicked as Player
            if (account.privateKey != null) {
                player.performCommand("cc account getPrivateKeyItem $accountIndex")
            } else {
                player.sendMessage("${plugin.pluginConfig.prefix}§cこの口座はWatch Onlyのため、秘密鍵は取得できません")
            }
        }

        val deleteItem = Items.create(
            material = Material.BARRIER,
            name = Component.text("口座を削除", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして削除", NamedTextColor.YELLOW),
                Component.text("注意: 秘密鍵を保存していない場合、", NamedTextColor.RED),
                Component.text("資金に一生触れなくなります", NamedTextColor.RED)
            )
        )
        setItem(16, deleteItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account delete $accountIndex")
            player.closeInventory()
        }

        val backItem = Items.create(
            material = Material.ARROW,
            name = Component.text("戻る", NamedTextColor.WHITE, TextDecoration.BOLD)
        )
        setItem(26, backItem) { event ->
            event.whoClicked.closeInventory()
        }
    }
}