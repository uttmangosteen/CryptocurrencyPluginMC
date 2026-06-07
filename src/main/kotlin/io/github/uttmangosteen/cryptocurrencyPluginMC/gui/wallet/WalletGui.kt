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

class WalletGui(
    private val plugin: Main,
    private val wallet: Wallet
) : Gui(
    3,
    Component.text("自分の財布")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {

    init {
        update()
    }

    private fun update() {
        val inv = inventory
        inv.clear()

        val borderItem = Items.create(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = Component.text(" ")
        )
        for (i in 0 until size) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                setItem(i, borderItem)
            }
        }

        wallet.accounts.forEachIndexed { index, account ->
            val isMain = index == 0
            val titleText = account.memo.ifEmpty { "口座 #${index + 1}" }

            val lore = mutableListOf<Component>()
            if (isMain) lore.add(Component.text("⭐ [メイン口座]", NamedTextColor.GREEN))
            lore.add(Component.text("PublicKey:", NamedTextColor.GRAY))
            lore.add(Component.text("${account.publicKey.take(16)}...", NamedTextColor.DARK_GRAY))
            lore.add(Component.empty())
            lore.add(Component.text("▶ クリックして詳細を開く", NamedTextColor.YELLOW))

            val item = Items.create(
                material = Material.PAPER,
                name = Component.text(titleText)
                    .color(if (isMain) NamedTextColor.AQUA else NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD),
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