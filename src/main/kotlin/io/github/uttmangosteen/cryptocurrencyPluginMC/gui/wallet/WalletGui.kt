package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.banner.Pattern
import org.bukkit.block.banner.PatternType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.BannerMeta

class WalletGui(
    private val wallet: Wallet
) : Gui(
    5,
    Component.text("自分の財布")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {
    init {
        update()
    }

    companion object {
        private val accountPosIndex = listOf(10, 12, 14, 16, 28, 30, 32, 34)

        private val borderItem = Items.create(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val spaceItem = Items.create(
            material = Material.WHITE_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val addAccountItem = Items.create(
            material = Material.CRAFTING_TABLE,
            name = Component.text("口座を追加する", NamedTextColor.GREEN, TextDecoration.BOLD),
            lore = listOf(Component.text("▶ クリックして新しい口座を作成", NamedTextColor.YELLOW))
        )
    }

    private fun update() {
        val inv = inventory
        inv.clear()
        for (i in 0 until size) setItem(i, borderItem)

        for (i in accountPosIndex) setItem(i, spaceItem)

        accountPosIndex.forEachIndexed { index, slot ->
            val account = wallet.accounts.getOrNull(index)

            if (account == null) {
                if (index == wallet.accounts.size) {
                    setItem(slot, addAccountItem) { event ->
                        val player = event.whoClicked as Player
                        player.performCommand("cc account create")
                        player.closeInventory()
                    }
                } else setItem(slot, spaceItem)
                return@forEachIndexed
            }

            val isMain = index == 0
            val titleText = account.memo.ifEmpty { "口座 #${index}" }

            val lore = mutableListOf<Component>()
            if (isMain) lore.add(Component.text("[メイン(受け取り用)口座]", NamedTextColor.GREEN))
            lore.add(Component.text("PublicKey:", NamedTextColor.GRAY))
            lore.add(Component.text(TextFormat.formatKey(account.publicKey), NamedTextColor.DARK_GRAY))
            lore.add(Component.text("▶ クリックして詳細を開く", NamedTextColor.YELLOW))

            val item = Items.create(
                material = Material.PAPER,
                name = Component.text(titleText)
                    .color(if (isMain) NamedTextColor.AQUA else NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD),
                lore = lore
            )

            setItem(slot, item) { event ->
                val player = event.whoClicked as Player
                val accountGui = WalletAccountGui(wallet, index)
                accountGui.parentGuiOpener = {
                    player.performCommand("cc wallet")
                }
                accountGui.open(player)
            }
        }
    }
}