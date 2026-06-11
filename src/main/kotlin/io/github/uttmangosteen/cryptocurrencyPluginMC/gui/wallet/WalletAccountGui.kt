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
    2,
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
            lore = listOf(
                Component.text("▶ クリックして書き出す", NamedTextColor.YELLOW),
                Component.text("紙が1枚必要です", NamedTextColor.GRAY)
            )
        )
        private val getPrivateKeyItem = Items.create(
            material = Material.OMINOUS_TRIAL_KEY,
            name = Component.text("秘密鍵付き公開鍵を書き出す", NamedTextColor.RED, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして書き出す", NamedTextColor.YELLOW),
                Component.text("取扱注意", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("紙が1枚必要です", NamedTextColor.GRAY)
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
                )
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
                Component.text("上の「口座をメイン(#0)に設定」から切り替えできます", NamedTextColor.YELLOW)
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
            name = Component.text("メモを編集する", NamedTextColor.GOLD, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてメモを編集", NamedTextColor.YELLOW),
            )
        )
        private val historyItem = Items.create(
            material = Material.BOOK,
            name = Component.text("取引履歴を確認", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックして取引履歴の案内を表示", NamedTextColor.YELLOW),
                Component.text("証明された取引の確認はこちらから", NamedTextColor.GRAY)
            )
        )
        private val mempoolInfoItem = Items.create(
            material = Material.PAINTING,
            name = Component.text("Mempoolの状況を確認", NamedTextColor.AQUA, TextDecoration.BOLD),
            lore = listOf(
                Component.text("▶ クリックしてMempoolを確認", NamedTextColor.YELLOW),
                Component.text("送信した取引の確認はこちらから", NamedTextColor.GRAY)
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

        val infoLore = mutableListOf<Component>()
        infoLore.add(
            if (account.memo.isNotBlank()) Component.text(
                account.memo,
                NamedTextColor.YELLOW,
                TextDecoration.BOLD,
                TextDecoration.ITALIC
            )
            else Component.text("no memo", NamedTextColor.GRAY)
        )
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
            else Component.text("null", NamedTextColor.DARK_GRAY)
        )

        val state = walletState
        if (state == null) {
            infoLore.add(Component.text("残高を取得できませんでした", NamedTextColor.DARK_GRAY))
        } else {
            infoLore.add(
                Component.text("総額　: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.totalBalance), NamedTextColor.GREEN))
            )
            infoLore.add(
                Component.text("利用可: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.balance), NamedTextColor.GREEN))
            )
            infoLore.add(
                Component.text("計算中: ", NamedTextColor.GRAY)
                    .append(Component.text(TextFormat.formatCoin(state.pendingBalance), NamedTextColor.YELLOW))
            )
        }

        val infoItem = Items.create(
            material = Material.PAPER,
            name = Component.text("口座 #${accountIndex}", NamedTextColor.WHITE, TextDecoration.BOLD),
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
        setItem(2, setMainItem) { event ->
            val player = event.whoClicked as Player
            if (!isMain) {
                preventParentOpen = true
                player.performCommand("cc account main $accountIndex")
                player.closeInventory()
            }
        }

        setItem(15, getPubKeyItem) { event ->
            val player = event.whoClicked as Player
            player.performCommand("cc account getPubKeyItem $accountIndex")
        }

        if (!hasPrivateKey) {
            setItem(6, needPrivateKeyItem)
        } else {
            setItem(6, getPrivateKeyItem) { event ->
                val player = event.whoClicked as Player
                player.performCommand("cc account getPrivateKeyItem $accountIndex")
            }
        }

        setItem(8, deleteItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "口座を忘れる",
                command = "/cc account delete ${account.publicKey} ",
                usage = "/cc account delete <pubKey>"
            )
            player.sendMessage("========================================")
        }

        setItem(0, mempoolInfoItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.performCommand("cc info mempool $accountIndex")
            player.closeInventory()
        }

        setItem(9, historyItem) { event ->
            val player = event.whoClicked as Player
            preventParentOpen = true
            player.closeInventory()

            player.sendMessage("========================================")
            CommandSuggestHelper.send(
                player = player,
                title = "取引履歴を確認",
                command = "/cc history $accountIndex ",
                usage = "/cc history <index> [page]"
            )
            player.sendMessage("========================================")
        }

        setItem(13, editMemoItem) { event ->
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
            setItem(11, needPrivateKeyItem)
        } else if (!isMain) {
            setItem(11, needMainForSendItem)
        } else {
            setItem(11, createSendItem) { event ->
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