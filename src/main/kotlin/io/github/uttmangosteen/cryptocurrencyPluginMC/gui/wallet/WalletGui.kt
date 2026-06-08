package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.ItemKeys
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.KeyItems
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player

class WalletGui(
    private val plugin: Main,
    private val wallet: Wallet
) : Gui(
    1,
    Component.text("自分の財布")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {
    private val keyItemKeys = ItemKeys.keyItem(plugin)

    init {
        update()
    }

    companion object {
        private val accountPosIndex = listOf(2, 4, 5, 6, 7, 8)

        private val borderItem = Items.create(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val spaceItem = Items.create(
            material = Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val addAccountItem = Items.create(
            material = Material.FEATHER,
            name = Component.text("口座を追加する", NamedTextColor.GREEN, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして新しい口座を作成", NamedTextColor.YELLOW),
                Component.text("▶ 鍵アイテムをMainHandに持っている場合はそれを登録", NamedTextColor.AQUA)
            )
        )
        private val infoBlockchainItem = Items.create(
            material = Material.BOOKSHELF,
            name = Component.text("ブロックチェーン情報を確認", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてチェーンの状況を表示", NamedTextColor.YELLOW),
            )
        )
    }

    private fun update() {
        val inv = inventory
        inv.clear()
        setItem(0, infoBlockchainItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc info blockchain")
            player.closeInventory()
        }
        setItem(1, borderItem)
        setItem(3, borderItem)
        for (i in accountPosIndex) setItem(i, spaceItem)

        accountPosIndex.forEachIndexed { index, slot ->
            val account = wallet.accounts.getOrNull(index)
            if (account == null) {
                if (index == wallet.accounts.size) {
                    setItem(slot, addAccountItem) { event ->
                        val player = event.whoClicked as Player
                        val itemAccount = KeyItems.readAccount(
                            item = player.inventory.itemInMainHand,
                            keys = keyItemKeys
                        )

                        if (itemAccount != null) {
                            player.performCommand("cc account register")
                        } else {
                            player.performCommand("cc account create")
                        }

                        player.closeInventory()
                    }
                } else setItem(slot, spaceItem)
                return@forEachIndexed
            }

            val isMain = index == 0
            val isWatchOnly = account.privateKey == null
            val titleText = "口座(#${index})"
            val memoText = account.memo.ifBlank { "no memo" }

            val lore = mutableListOf<Component>()
            lore.add(Component.text(memoText, NamedTextColor.GRAY))

            var tagsComponent = Component.empty()
            if (isMain) tagsComponent = tagsComponent.append(Component.text("[Main] ", NamedTextColor.GREEN))
            if (isWatchOnly) tagsComponent = tagsComponent.append(Component.text("[Watch] ", NamedTextColor.YELLOW))
            if (isMain || isWatchOnly) lore.add(tagsComponent)

            lore.add(Component.text("PublicKey:", NamedTextColor.GRAY))
            lore.add(Component.text(TextFormat.formatKey(account.publicKey), NamedTextColor.DARK_GRAY))
            lore.add(Component.text("▶ クリックして詳細を開く", NamedTextColor.YELLOW))

            val item = Items.create(
                material = Material.PAPER,
                name = Component.text(titleText, NamedTextColor.WHITE, TextDecoration.BOLD),
                lore = lore
            )

            setItem(slot, item) { event ->
                val player = event.whoClicked as Player
                val accountGui = WalletAccountGui(plugin, wallet, index)
                accountGui.parentGuiOpener = {
                    player.performCommand("cc wallet")
                }
                accountGui.open(player)
            }
        }
    }
}