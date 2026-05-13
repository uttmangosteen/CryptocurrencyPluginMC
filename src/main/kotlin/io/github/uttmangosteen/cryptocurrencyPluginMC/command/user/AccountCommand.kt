package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
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
            "create" -> create(sender)
            "delete" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                delete(sender, index)
            }

            "list" -> list(sender)
            "main" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                main(sender, index)
            }

            "memo" -> {
                if (args.size < 4) return
                val index = args[2].toIntOrNull() ?: return
                memo(sender, index, args[3])
            }

            "getPubKeyItem" -> getPubKeyItem(sender, args.getOrElse(2) { "" })
            "getPrivateKeyItem" -> getPrivateKeyItem(sender, args.getOrElse(2) { "" })
            "register" -> register(sender, args.getOrNull(2))


            else -> return
        }
    }

    private fun create(player: Player) {
        plugin.launchAsync {
            val created = plugin.repositories.walletRepo.createAccount(player.uniqueId.toString())

                plugin.runSync {
                    when (created) {
                        true -> player.sendMessage("$prefix§a口座を作成しました")
                        false -> player.sendMessage("$prefix§c口座数が上限に達しています")
                        null -> player.sendMessage("$prefix§c口座の作成に失敗しました")
                    }
                }
            }
        }

        private fun delete(player: Player, index: Int) {
            plugin.launchAsync {
                val deleted = plugin.repositories.walletRepo.forgetAccount(player.uniqueId.toString(), index)

                plugin.runSync {
                    if (deleted) {
                        player.sendMessage("$prefix§a口座を削除しました")
                    } else {
                        player.sendMessage("$prefix§c口座の削除に失敗しました")
                    }
                }
            }
        }

        private fun list(player: Player) {
            plugin.launchAsync {
                val wallet = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())

                plugin.runSync {
                    if (wallet == null || wallet.accounts.isEmpty()) {
                        player.sendMessage("$prefix§e登録されている口座はありません")
                        return@runSync
                    }

                    player.sendMessage("$prefix§f§l=============== §8§lAccount list §f§l===============")
                    wallet.accounts.forEachIndexed { index, account ->
                        val mainMark = if (index == 0) "§a§lMAIN " else ""
                        val keyMark = if (account.privateKeyHex == null) "§e§lWATCH " else ""
                        val memo = account.memo.ifBlank { "§7no memo" }
                        player.sendMessage("$prefix§f[$index] $mainMark$keyMark§r$memo")
                        player.sendMessage("$prefix§8${account.publicKeyHex}")
                    }
                    player.sendMessage("$prefix§f§l===========================================")
                }
            }
        }

        private fun main(player: Player, index: Int) {
            plugin.launchAsync {
                val switched = plugin.repositories.walletRepo.switchMainAccount(player.uniqueId.toString(), index)

                plugin.runSync {
                    if (switched) {
                        player.sendMessage("$prefix§aメイン口座を切り替えました")
                    } else {
                        player.sendMessage("$prefix§cメイン口座の切り替えに失敗しました")
                    }
                }
            }
        }

        private fun memo(player: Player, index: Int, memo: String) {
            plugin.launchAsync {
                val updated = plugin.repositories.walletRepo.updateMemo(
                    ownerUUID = player.uniqueId.toString(),
                    index = index,
                    memo = memo
                )

                plugin.runSync {
                    if (updated) {
                        player.sendMessage("$prefix§a口座メモを変更しました")
                    } else {
                        player.sendMessage("$prefix§c口座メモの変更に失敗しました")
                    }
                }
            }
        }

        private fun getPubKeyItem(player: Player, memo: String = "no memo") {
            plugin.launchAsync {
                val account = plugin.repositories.walletRepo.getMainAccount(player.uniqueId.toString())

                plugin.runSync {
                    if (account == null) {
                        player.sendMessage("$prefix§cメイン口座がありません")
                        return@runSync
                    }

                    val item = publicKeyItemFactory.create(account, memo)
                    val overflowItems = player.inventory.addItem(item)

                    if (overflowItems.isEmpty()) {
                        player.sendMessage("$prefix§a公開鍵アイテムを取得しました")
                    } else {
                        player.sendMessage("$prefix§eインベントリに空きがありません")
                    }
                }
            }
        }

        private fun getPrivateKeyItem(player: Player, memo: String = "no memo") {
            plugin.launchAsync {
                val account = plugin.repositories.walletRepo.getMainAccount(player.uniqueId.toString())

                plugin.runSync {
                    if (account == null) {
                        player.sendMessage("$prefix§cメイン口座がありません")
                        return@runSync
                    }

                    if (account.privateKeyHex == null) {
                        player.sendMessage("$prefix§cこの口座には秘密鍵がありません")
                        return@runSync
                    }

                    val item = publicKeyItemFactory.createWithPrivateKey(account, memo)

                    if (item == null) {
                        player.sendMessage("$prefix§c秘密鍵付き公開鍵アイテムの作成に失敗しました")
                        return@runSync
                    }

                    val overflowItems = player.inventory.addItem(item)

                    if (overflowItems.isEmpty()) {
                        player.sendMessage("$prefix§c秘密鍵付き公開鍵アイテムを取得しました　取扱注意")
                    } else {
                        player.sendMessage("$prefix§eインベントリに空きがありません")
                    }
                }
            }
        }

        private fun register(player: Player, pubKey: String?) {
            val account = if (pubKey != null) {
                Account.watchOnly(
                    publicKeyHex = pubKey,
                    memo = "§7watch-only"
                )
            } else {
                publicKeyItemFactory.readAccount(
                    itemStack = player.inventory.itemInMainHand,
                    memo = "§7registered item"
                )
            }

            if (account == null) {
                player.sendMessage("$prefix§c公開鍵アイテムを手に持つか、公開鍵を指定してください")
                return
            }

            plugin.launchAsync {
                val registered = plugin.repositories.walletRepo.registerAccount(
                    ownerUUID = player.uniqueId.toString(),
                    account = account
                )

                plugin.runSync {
                    when (registered) {
                        true -> {
                            if (account.privateKeyHex == null) {
                                player.sendMessage("$prefix§a監視用口座を登録しました")
                            } else {
                                player.sendMessage("$prefix§a秘密鍵付き口座を登録しました")
                            }
                        }

                        false -> player.sendMessage("$prefix§c口座数が上限に達しているか、同じ公開鍵が既に登録されています")
                        null -> player.sendMessage("$prefix§c口座の登録に失敗しました")
                    }
                }
            }
        }

        fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf(
                "create",
                "delete",
                "list",
                "main",
                "memo",
                "getPubKeyItem",
                "getPrivateKeyItem",
                "register"
            ).filter { it.startsWith(args[1]) }
            3 -> when (args[1]) {
                "delete", "main", "memo" -> listOf("<index>").filter { it.startsWith(args[2]) }
                "getPubKeyItem", "getPrivateKeyItem" -> listOf("[itemMemo]").filter { it.startsWith(args[2]) }
                "register" -> listOf("[pubKey]").filter { it.startsWith(args[2]) }
                else -> emptyList()
            }
            4 -> when (args[1]) {
                "memo" -> listOf("<memo>").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}