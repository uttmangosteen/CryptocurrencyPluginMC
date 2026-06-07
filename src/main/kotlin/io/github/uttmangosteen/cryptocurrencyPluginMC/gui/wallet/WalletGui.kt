package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import org.bukkit.Material
import org.bukkit.entity.Player

class WalletGui(
    private val plugin: Main,
    private val wallet: Wallet
) : Gui(3, Items.legacy("§8§l自分の財布")) {

    init {
        update()
    }

    private fun update() {
        val inv = inventory
        inv.clear()

        val borderItem = Items.createLegacy(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = " "
        )
        for (i in 0 until size) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                setItem(i, borderItem)
            }
        }

        wallet.accounts.forEachIndexed { index, account ->
            val isMain = index == 0
            val titleText = account.memo.ifEmpty { "口座 #${index + 1}" }

            val lore = mutableListOf<String>()
            if (isMain) lore.add("§a⭐ [メイン口座]")
            lore.add("§7PublicKey:")
            lore.add("§8${account.publicKey.take(16)}...")
            lore.add("")
            lore.add("§e▶ クリックして詳細を開く")

            val item = Items.createLegacy(
                material = Material.PAPER,
                name = if (isMain) "§b§l$titleText" else "§f§l$titleText",
                lore = lore
            )

            val slot = 10 + index
            setItem(slot, item) { event ->
                val player = event.whoClicked as Player
                val accountGui = WalletAccountGui(plugin, wallet, index)

                accountGui.parentGuiOpener = {
                    WalletGui(plugin, wallet).open(player)
                }
                accountGui.open(player)
            }
        }
    }
}