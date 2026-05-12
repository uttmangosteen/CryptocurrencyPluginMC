package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AccountCommand(
    private val plugin: Main,
) {
    private val prefix = plugin.pluginConfig.prefix
    private val publicKeyItemFactory = PubKeyItemFactory(plugin)

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) return

        when (args[1]) {
            "create" -> {
                create(sender)
            }

            "delete" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                delete(sender, index)
            }

            "list" -> {
                list(sender)
            }

            "main" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                main(sender, index)
            }

            "getPubKeyItem" -> {
                val memo = args.drop(2).joinToString(" ")
                getPubKeyItem(sender, memo)
            }

            else -> return
        }
    }

    private fun create(player: Player) {
        plugin.launchAsync {
            val created = plugin.repositories.walletRepo.createAccount(player.uniqueId.toString())

            when (created) {
                true -> player.sendMessage("$prefix§a口座を作成しました")
                false -> player.sendMessage("$prefix§c口座数が上限に達しています")
                null -> player.sendMessage("$prefix§c口座の作成に失敗しました")
            }
        }
    }

    private fun delete(player: Player, index: Int) {
        plugin.launchAsync {
            val deletedAccount = plugin.repositories.walletRepo.forgetAccount(player.uniqueId.toString(), index)

            if (deletedAccount == null) {
                player.sendMessage("§c口座の削除に失敗しました")
                return@launchAsync
            }

            val memo = deletedAccount.memo.ifBlank {
                "§f忘れた口座 #$index"
            }

            val item = publicKeyItemFactory.create(deletedAccount, memo)
            val overflowItems = player.inventory.addItem(item)

            if (overflowItems.isEmpty()) {
                player.sendMessage("$prefix§a口座を削除し、公開鍵アイテムを返却しました")
            } else {
                overflowItems.values.forEach { overflowItem ->
                    player.world.dropItemNaturally(player.location, overflowItem)
                }
                player.sendMessage("$prefix§eインベントリに空きがないため、公開鍵アイテムを足元に落としました")
            }
        }
    }

    private fun list(player: Player) {
        plugin.launchAsync {
            val wallet = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())

            if (wallet == null || wallet.accounts.isEmpty()) {
                player.sendMessage("$prefix§e登録されている口座はありません")
                return@launchAsync
            }

            player.sendMessage("§f§l=============== §8§lAccount list §f§l===============")
            wallet.accounts.forEachIndexed { index, account ->
                val mainMark = if (index == 0) "§a§lMAIN " else ""
                val memo = if (account.memo.isBlank()) "§7no memo" else account.memo
                player.sendMessage("§f[$index] $mainMark§r$memo")
                player.sendMessage("§8${account.publicKeyHex}")
            }
            player.sendMessage("§f§l==========================================")
        }
    }

    private fun main(player: Player, index: Int) {
        plugin.launchAsync {
            val switched = plugin.repositories.walletRepo.switchMainAccount(player.uniqueId.toString(), index)

            if (switched) {
                player.sendMessage("$prefix§aメイン口座を切り替えました")
            } else {
                player.sendMessage("$prefix§cメイン口座の切り替えに失敗しました")
            }
        }
    }

    private fun getPubKeyItem(player: Player, memo: String) {
        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getMainAccount(player.uniqueId.toString())

            if (account == null) {
                player.sendMessage("§cメイン口座がありません")
                return@launchAsync
            }

            val itemMemo = memo.ifBlank {
                "§f${player.name}の公開鍵"
            }

            val item = publicKeyItemFactory.create(account, itemMemo)
            val overflowItems = player.inventory.addItem(item)

            if (overflowItems.isEmpty()) {
                player.sendMessage("$prefix§a公開鍵アイテムを取得しました")
            } else {
                player.sendMessage("$prefix§eインベントリに空きがありません")
            }
        }
    }
}