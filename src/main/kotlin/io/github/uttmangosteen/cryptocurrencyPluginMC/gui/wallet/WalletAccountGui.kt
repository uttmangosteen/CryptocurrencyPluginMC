package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import org.bukkit.Material
import org.bukkit.entity.Player

class WalletAccountGui(
    private val plugin: Main,
    private val wallet: Wallet,
    private val accountIndex: Int
) : Gui(3, Items.legacy("§8§l口座アクション")) {

    init {
        update()
    }

    private fun update() {
        val inv = inventory
        inv.clear()

        val account = wallet.accounts.getOrNull(accountIndex) ?: return
        val isMain = accountIndex == 0

        val titleText = if (account.memo.isNotEmpty()) account.memo else "口座 #${accountIndex + 1}"
        val infoLore = mutableListOf<String>()
        if (isMain) infoLore.add("§a⭐ [メイン口座]")
        infoLore.add("§7PublicKey: ${account.publicKey.take(16)}...")
        infoLore.add("§7PrivateKey: ${if (account.privateKey != null) "登録済" else "なし (Watch Only)"}")

        val infoItem = Items.createLegacy(
            material = Material.PAPER,
            name = "§f§l$titleText",
            lore = infoLore
        )
        setItem(4, infoItem)

        val setMainItem = Items.createLegacy(
            material = if (isMain) Material.LIME_DYE else Material.GRAY_DYE,
            name = if (isMain) "§a§lメイン口座に設定" else "§f§lメイン口座に設定",
            lore = listOf(
                if (isMain) "§7現在この口座はメイン口座です" else "§e▶ クリックしてメイン口座に切り替え"
            )
        )
        setItem(10, setMainItem) { event ->
            val player = event.whoClicked as Player
            if (!isMain) {
                player.performCommand("cc account main $accountIndex")
                player.closeInventory()
            }
        }

        val getPubKeyItem = Items.createLegacy(
            material = Material.NAME_TAG,
            name = "§b§l公開鍵アイテムを取得",
            lore = listOf("§e▶ クリックして取得")
        )
        setItem(12, getPubKeyItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account getPubKeyItem $accountIndex")
        }

        val getPrivKeyItem = Items.createLegacy(
            material = Material.TRIPWIRE_HOOK,
            name = "§c§l秘密鍵アイテムを取得",
            lore = listOf(
                "§e▶ クリックして取得",
                "§4§l※ 取扱注意 ※"
            )
        )
        setItem(14, getPrivKeyItem) { event ->
            val player = event.whoClicked as Player
            if (account.privateKey != null) {
                player.performCommand("cc account getPrivateKeyItem $accountIndex")
            } else {
                player.sendMessage(Items.legacy("${plugin.pluginConfig.prefix}§cこの口座はWatch Onlyのため、秘密鍵は取得できません"))
            }
        }

        val deleteItem = Items.createLegacy(
            material = Material.BARRIER,
            name = "§c§l口座を削除",
            lore = listOf(
                "§e▶ クリックして削除",
                "§c注意: 秘密鍵を保存していない場合、",
                "§c資金に一生触れなくなります"
            )
        )
        setItem(16, deleteItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account delete $accountIndex")
            player.closeInventory()
        }

        val backItem = Items.createLegacy(
            material = Material.ARROW,
            name = "§f§l戻る"
        )
        setItem(26, backItem) { event ->
            event.whoClicked.closeInventory()
        }
    }
}