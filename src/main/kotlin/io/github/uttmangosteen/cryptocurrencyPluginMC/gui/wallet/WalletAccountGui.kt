package io.github.uttmangosteen.cryptocurrencyPluginMC.gui.wallet

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.CommandSuggestHelper
import io.github.uttmangosteen.cryptocurrencyPluginMC.gui.Gui
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.Items
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model.WalletState
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Wallet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent

class WalletAccountGui(
    private val plugin: Main,
    private val wallet: Wallet,
    private val accountIndex: Int
) : Gui(
    3,
    Component.text("口座の詳細")
        .color(NamedTextColor.DARK_GRAY)
        .decorate(TextDecoration.BOLD)
) {
    private var preventParentOpen = false
    private var walletState: WalletState? = null

    init {
        update()
        loadWalletState()
    }

    companion object {
        private val borderItem = Items.create(
            material = Material.GRAY_STAINED_GLASS_PANE,
            name = Component.empty()
        )
        private val getPubKeyItem = Items.create(
            material = Material.TRIAL_KEY,
            name = Component.text("公開鍵を書き出す", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(Component.text("▶ クリックして書き出す", NamedTextColor.YELLOW))
        )
        private val getPrivateKeyItem = Items.create(
            material = Material.OMINOUS_TRIAL_KEY,
            name = Component.text("秘密鍵付き公開鍵を書き出す", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして書き出す", NamedTextColor.YELLOW),
                Component.text("取扱注意", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            )
        )
        private val deleteItem = Items.create(
            material = Material.TNT,
            name = Component.text("口座を忘れる", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして削除", NamedTextColor.YELLOW),
                Component.text("注意: 秘密鍵を保存していない場合、", NamedTextColor.RED),
                Component.text("口座のお金に一生触れなくなります", NamedTextColor.RED),
                Component.text(
                    "運営にも復旧できません",
                    NamedTextColor.DARK_RED,
                    TextDecoration.BOLD,
                    TextDecoration.UNDERLINED
                ),
            )
        )
        private val createSendItem = Items.create(
            material = Material.EMERALD,
            name = Component.text("送金を作成する", NamedTextColor.GREEN, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして送金作成の案内を表示", NamedTextColor.YELLOW),
                Component.text("送金はメイン口座から作成されます", NamedTextColor.GRAY)
            )
        )
        private val needMainForSendItem = Items.create(
            material = Material.BARRIER,
            name = Component.text("送金はメイン口座から行われます", NamedTextColor.YELLOW, TextDecoration.BOLD),
            lore = listOf(
                Component.text("この口座は現在メインではありません", NamedTextColor.GRAY),
                Component.text("送金を作成するには、先にメインに設定してください", NamedTextColor.GRAY),
                Component.text("上の「メイン口座に設定」から切り替えできます", NamedTextColor.YELLOW)
            )
        )
        private val needPrivateKeyItem = Items.create(
            material = Material.BARRIER,
            name = Component.text("秘密鍵がありません", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("秘密鍵を共有してもらう必要があります", NamedTextColor.YELLOW)
            )
        )
        private val editMemoItem = Items.create(
            material = Material.WRITABLE_BOOK,
            name = Component.text("メモを編集する", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてメモを編集", NamedTextColor.YELLOW),
                Component.text("/cc account memo <index> [memo]", NamedTextColor.GRAY)
            )
        )
    }

    private fun loadWalletState() {
        val account = wallet.accounts.getOrNull(accountIndex) ?: return

        plugin.launchAsync {
            val state = plugin.repositories.utxoRepo.getWalletState(account.publicKey)

            plugin.runSync {
                walletState = state
                update()
            }
        }
    }

    private fun update() {
        val inv = inventory
        inv.clear()
        for (i in 0 until size) setItem(i, borderItem)

        val account = wallet.accounts.getOrNull(accountIndex) ?: return
        val isMain = accountIndex == 0
        val hasPrivateKey = account.privateKey != null

        val titleText = "口座 #${accountIndex}"
        val memoText = account.memo.ifBlank { "no memo" }

        val infoLore = mutableListOf<Component>()
        infoLore.add(Component.text(memoText, NamedTextColor.GRAY))
        if (isMain) infoLore.add(Component.text("[Main]", NamedTextColor.GREEN))
        if (!hasPrivateKey) infoLore.add(Component.text("[Watch]", NamedTextColor.YELLOW))
        infoLore.add(Component.text("PublicKey:", NamedTextColor.GRAY))
        infoLore.add(Component.text(account.publicKey, NamedTextColor.DARK_GRAY))
        infoLore.add(Component.text("PrivateKey:", NamedTextColor.GRAY))
        infoLore.add(
            if (hasPrivateKey) Component.text(
                TextFormat.formatKey(account.privateKey),
                NamedTextColor.DARK_GRAY
            )
            else Component.text("[Watch Only]", NamedTextColor.YELLOW)
        )

        val state = walletState
        infoLore.add(Component.empty())
        infoLore.add(Component.text("Balance:", NamedTextColor.GRAY))
        if (state == null) {
            infoLore.add(Component.text("loading...", NamedTextColor.DARK_GRAY))
        } else {
            infoLore.add(
                Component.text("Total: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.totalBalance), NamedTextColor.GREEN))
            )
            infoLore.add(
                Component.text("Available: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.balance), NamedTextColor.GREEN))
            )
            infoLore.add(
                Component.text("Pending: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.pendingBalance), NamedTextColor.YELLOW))
            )
        }

        val infoItem = Items.create(
            material = Material.PAPER,
            name = Component.text(titleText, NamedTextColor.WHITE, TextDecoration.BOLD),
            lore = infoLore
        )
        setItem(4, infoItem)

        val setMainItem = Items.create(
            material = if (isMain) Material.LIME_DYE else Material.GRAY_DYE,
            name = Component.text("口座をメイン(#0)に設定")
                .color(if (isMain) NamedTextColor.GREEN else NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD),
            lore = listOf(
                if (isMain) {
                    Component.text("この口座はメインです", NamedTextColor.GRAY)
                } else {
                    Component.text("▶ クリックしてメインに切り替え", NamedTextColor.YELLOW)
                }
            )
        )
        setItem(0, setMainItem) { event ->
            val player = event.whoClicked as Player
            if (!isMain) {
                preventParentOpen = true
                player.performCommand("cc account main $accountIndex")
                player.closeInventory()
            }
        }

        setItem(23, getPubKeyItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account getPubKeyItem $accountIndex")
        }

        if (!hasPrivateKey) {
            setItem(25, needPrivateKeyItem)
        } else {
            setItem(25, getPrivateKeyItem) { event ->
                val player = event.whoClicked as Player
                player.performCommand("cc account getPrivateKeyItem $accountIndex")
            }
        }

        setItem(8, deleteItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.performCommand("cc account delete ${account.publicKey}")
            player.closeInventory()
        }

        setItem(21, editMemoItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "メモの書き換え",
                command = "/cc account memo $accountIndex ",
                usage = "/cc account memo <index> [memo]"
            )
            player.sendMessage("========================================")
        }

        if (!hasPrivateKey) {
            setItem(19, needPrivateKeyItem)
        } else if (!isMain) {
            setItem(19, needMainForSendItem)
        } else {
            setItem(19, createSendItem) { event ->
                val player = event.whoClicked as Player
                preventParentOpen = true
                player.closeInventory()

                player.sendMessage("========================================")
                CommandSuggestHelper.send(
                    player = player,
                    title = "名前から送金を追加",
                    command = "/cc send byName ",
                    usage = "/cc send byName <amount> <name>"
                )
                CommandSuggestHelper.send(
                    player = player,
                    title = "手に持った鍵アイテムor公開鍵から送金を追加",
                    command = "/cc send byPubKey ",
                    usage = "/cc send byPubKey <amount> [pubKey]"
                )
                CommandSuggestHelper.send(
                    player = player,
                    title = "送金リストを確認",
                    command = "/cc send list",
                    usage = "/cc send list"
                )
                CommandSuggestHelper.send(
                    player = player,
                    title = "間違えた送金を削除",
                    command = "/cc send delete ",
                    usage = "/cc send delete <index|all>"
                )
                CommandSuggestHelper.send(
                    player = player,
                    title = "送金をまとめた取引を送信",
                    command = "/cc send create ",
                    usage = "/cc send create <fee> [memo]"
                )
                player.sendMessage("========================================")
            }
        }
    }

    override fun onClose(e: InventoryCloseEvent): Boolean {
        return preventParentOpen
    }
}