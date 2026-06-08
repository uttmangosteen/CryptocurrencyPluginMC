package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.ItemKeys
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.KeyItems
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class AccountCommand(
    private val plugin: Main,
) {
    private val prefix = plugin.pluginConfig.prefix
    private val keyItemKeys = ItemKeys.keyItem(plugin)

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        if (args.size < 2) return

        when (args[1]) {
            "create" -> create(sender)
            "delete" -> {
                if (args.size < 3) return
                delete(sender, args[2])
            }

            "list" -> list(sender)
            "main" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                main(sender, index)
            }

            "memo" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                val memo = args.getOrNull(3).orEmpty()
                memo(sender, index, memo)
            }

            "getPubKeyItem" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                getPubKeyItem(sender, index)
            }

            "getPrivateKeyItem" -> {
                if (args.size < 3) return
                val index = args[2].toIntOrNull() ?: return
                getPrivateKeyItem(sender, index)
            }

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

    private fun delete(player: Player, publicKey: String) {
        plugin.launchAsync {
            val deleted = plugin.repositories.walletRepo.forgetAccount(
                ownerUUID = player.uniqueId.toString(),
                publicKey = publicKey
            )

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

                player.sendMessage("$prefix§f§l========== §8§lAccount list §f§l==========")
                wallet.accounts.forEachIndexed { index, account ->
                    val mainMark = if (index == 0) "§a§lMAIN " else ""
                    val keyMark = if (account.privateKey == null) "§e§lWATCH " else ""
                    val memo = account.memo.ifBlank { "§7no memo" }
                    player.sendMessage("$prefix§f[$index] $mainMark$keyMark§r$memo")
                    player.sendMessage("$prefix§8${account.publicKey}")
                }
                player.sendMessage("$prefix§f§l==================================")
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
        val normalizedMemo = memo.ifBlank { "" }

        plugin.launchAsync {
            val updated = plugin.repositories.walletRepo.updateMemo(
                ownerUUID = player.uniqueId.toString(),
                index = index,
                memo = normalizedMemo
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

    private fun getPubKeyItem(player: Player, index: Int) {
        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())
                ?.accounts
                ?.getOrNull(index)

            plugin.runSync {
                if (account == null) {
                    player.sendMessage("$prefix§c指定された口座がありません")
                    return@runSync
                }

                if (!consumePaper(player)) {
                    player.sendMessage("$prefix§c公開鍵アイテムの作成には紙が1枚必要です")
                    return@runSync
                }

                val item = KeyItems.create(
                    account = account,
                    ownerName = player.name,
                    keys = keyItemKeys
                )
                val overflow = player.inventory.addItem(item)
                overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }
                playWriteSound(player)
                player.sendMessage("$prefix§a公開鍵アイテムを取得しました")
            }
        }
    }

    private fun getPrivateKeyItem(player: Player, index: Int) {
        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getWallet(player.uniqueId.toString())
                ?.accounts
                ?.getOrNull(index)

            plugin.runSync {
                if (account == null) {
                    player.sendMessage("$prefix§c指定された口座がありません")
                    return@runSync
                }

                if (account.privateKey == null) {
                    player.sendMessage("$prefix§cこの口座には秘密鍵がありません")
                    return@runSync
                }

                if (!consumePaper(player)) {
                    player.sendMessage("$prefix§c秘密鍵付き公開鍵アイテムの作成には紙が1枚必要です")
                    return@runSync
                }

                val item = KeyItems.createWithPrivateKey(
                    account = account,
                    ownerName = player.name,
                    keys = keyItemKeys
                )

                if (item == null) {
                    player.inventory.addItem(ItemStack(Material.PAPER, 1))
                    player.sendMessage("$prefix§c秘密鍵付き公開鍵アイテムの作成に失敗しました")
                    return@runSync
                }

                val overflowItems = player.inventory.addItem(item)

                if (overflowItems.isEmpty()) {
                    playWriteSound(player)
                    player.sendMessage("$prefix§c秘密鍵付き公開鍵アイテムを取得しました　取扱注意")
                } else {
                    player.inventory.addItem(ItemStack(Material.PAPER, 1))
                    player.sendMessage("$prefix§eインベントリに空きがありません")
                }
            }
        }
    }

    private fun consumePaper(player: Player): Boolean {
        val paper = ItemStack(Material.PAPER, 1)
        if (!player.inventory.containsAtLeast(paper, 1)) return false
        player.inventory.removeItem(paper)
        return true
    }

    private fun playWriteSound(player: Player) {
        player.playSound(
            player.location,
            Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
            1.0f,
            0.8f
        )
    }

    private fun register(player: Player, pubKey: String?) {
        val account = if (pubKey != null) {
            val watchAccount = Account.watchOnly(
                publicKey = pubKey,
                memo = "§7watch-only"
            )
            if (watchAccount == null) {
                player.sendMessage("$prefix§c公開鍵のフォーマットが不正です")
                player.sendMessage("$prefix§c64文字の16進数(0-9, a-f)で正確に入力してください")
                return
            }
            watchAccount
        } else {
            val itemAccount = KeyItems.readAccount(
                item = player.inventory.itemInMainHand,
                keys = keyItemKeys,
                memo = "§7registered item"
            )
            if (itemAccount == null) {
                player.sendMessage("$prefix§c有効な公開鍵アイテムを手に持つか、公開鍵を文字列で指定してください")
                return
            }
            itemAccount
        }

        plugin.launchAsync {
            val registered = plugin.repositories.walletRepo.registerAccount(
                ownerUUID = player.uniqueId.toString(),
                account = account
            )

            plugin.runSync {
                when (registered) {
                    true -> {
                        if (account.privateKey == null) {
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
                "delete" -> listOf("<pubKey>").filter { it.startsWith(args[2]) }
                "main", "memo", "getPubKeyItem", "getPrivateKeyItem" -> {
                    listOf("<index>").filter { it.startsWith(args[2]) }
                }

                "register" -> listOf("[pubKey]").filter { it.startsWith(args[2]) }
                else -> emptyList()
            }

            4 -> when (args[1]) {
                "memo" -> {
                    val index = args[2].toIntOrNull()
                    if (index == null) {
                        emptyList()
                    } else {
                        listOf("[memo]").filter { it.startsWith(args[3]) }
                    }
                }

                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}