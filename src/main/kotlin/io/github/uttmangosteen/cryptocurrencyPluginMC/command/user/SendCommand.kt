package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat.formatCoin
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Signer
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model.MempoolEntry
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.Account
import io.github.uttmangosteen.cryptocurrencyPluginMC.wallet.KeyItemFactory
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SendCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix
    private val publicKeyItemFactory = KeyItemFactory(plugin)

    private val pendingSends = ConcurrentHashMap<UUID, MutableList<PendingSend>>()

    companion object {
        //1つのtxに入れられる送金
        private const val MAX_SENDS_PER_TX = 8
    }

    fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return
        val action = args.getOrNull(1) ?: return

        when (action) {
            "byName" -> {
                val amount = TextFormat.parseCoin(args.getOrNull(2))
                val name = args.getOrNull(3)

                if (amount == null || name == null) {
                    sender.sendMessage("$prefix§c/cc send byName <amount> <name>")
                    return
                }

                byName(sender, amount, name)
            }

            "byPubKey" -> {
                val amount = TextFormat.parseCoin(args.getOrNull(2))

                if (amount == null) {
                    sender.sendMessage("$prefix§c/cc send byPubKey <amount> [pubKey]")
                    return
                }

                byPubKey(sender, amount, args.getOrNull(3))
            }

            "list" -> list(sender)

            "delete" -> {
                val target = args.getOrNull(2) ?: return
                delete(sender, target)
            }

            "create" -> {
                val fee = TextFormat.parseCoin(args.getOrNull(2))

                if (fee == null) {
                    sender.sendMessage("$prefix§c/cc send create <fee> [memo]")
                    return
                }

                val memo = args.drop(3).joinToString(" ")
                create(sender, fee, memo)
            }
        }
    }

    private fun byName(player: Player, amount: Long, name: String) {
        plugin.launchAsync {
            val targetUuid = Bukkit.getOfflinePlayer(name).uniqueId.toString()
            val targetAccount = plugin.repositories.walletRepo.getMainAccount(targetUuid)

            plugin.runSync {
                if (targetAccount == null) {
                    player.sendMessage("$prefix§c$name のメイン口座が見つかりません")
                    return@runSync
                }

                addPendingSend(player, targetAccount.publicKey, amount)
                player.sendMessage("$prefix§a送金リストに追加しました: §a${formatCoin(amount)} §7-> §f$name")
            }
        }
    }

    private fun byPubKey(player: Player, amount: Long, pubKeyArg: String?) {
        val pubKey = if (pubKeyArg != null) {
            Signer.normalizePublicKey(pubKeyArg)
        } else {
            publicKeyItemFactory.readAccount(
                itemStack = player.inventory.itemInMainHand,
                memo = ""
            )?.publicKey
        }

        if (pubKey == null) {
            player.sendMessage("$prefix§c公開鍵が不正、または有効な公開鍵アイテムを持っていません")
            return
        }

        addPendingSend(player, pubKey, amount)
        player.sendMessage("$prefix§a送金リストに追加しました: §a${formatCoin(amount)} §7-> §8${pubKey}")
    }

    private fun list(player: Player) {
        val list = pendingSends[player.uniqueId].orEmpty()

        if (list.isEmpty()) {
            player.sendMessage("$prefix§e作成中の送金はありません")
            return
        }

        player.sendMessage("$prefix§f§l========== §8§lPending sends §f§l==========")
        list.forEachIndexed { index, send ->
            player.sendMessage("$prefix§f[$index] §a${formatCoin(send.amount)} §7->")
            player.sendMessage("$prefix§8${send.receiverPubKey}")
        }
        player.sendMessage("$prefix§f§l===================================")
    }

    private fun delete(player: Player, target: String) {
        val list = pendingSends[player.uniqueId]

        if (list.isNullOrEmpty()) {
            player.sendMessage("$prefix§e作成中の送金はありません")
            return
        }

        if (target == "all") {
            list.clear()
            player.sendMessage("$prefix§a送金リストを空にしました")
            return
        }

        val index = target.toIntOrNull()

        if (index == null || index !in list.indices) {
            player.sendMessage("$prefix§c指定された送金がありません")
            return
        }

        list.removeAt(index)
        player.sendMessage("$prefix§a送金リストから削除しました")
    }

    private fun create(player: Player, fee: Long, memo: String) {
        val sends = pendingSends[player.uniqueId]?.toList().orEmpty()

        if (sends.isEmpty()) {
            player.sendMessage("$prefix§c送金リストが空です")
            return
        }

        if (sends.size > MAX_SENDS_PER_TX) {
            player.sendMessage("$prefix§c1つのTransactionに入れられる送金は最大${MAX_SENDS_PER_TX}件です")
            return
        }

        if (fee < 0L) {
            player.sendMessage("$prefix§c手数料は0以上にしてください")
            return
        }

        plugin.launchAsync {
            val account = plugin.repositories.walletRepo.getMainAccount(player.uniqueId.toString())

            if (account == null || account.privateKey == null) {
                plugin.runSync {
                    player.sendMessage("$prefix§c秘密鍵付きのメイン口座がありません")
                }
                return@launchAsync
            }

            val tx = buildTransaction(
                senderAccount = account,
                sends = sends,
                fee = fee,
                memo = memo
            )

            if (tx == null) {
                plugin.runSync {
                    player.sendMessage("$prefix§c残高不足、またはTransaction作成に失敗しました")
                }
                return@launchAsync
            }

            val locked = plugin.repositories.utxoRepo.lock(tx)

            if (!locked) {
                plugin.runSync {
                    player.sendMessage("$prefix§c使用するUTXOのロックに失敗しました")
                }
                return@launchAsync
            }

            val saved = plugin.repositories.mempoolRepo.save(
                MempoolEntry(
                    transaction = tx,
                    fee = fee
                )
            )

            if (!saved) {
                plugin.repositories.utxoRepo.unlock(tx)

                plugin.runSync {
                    player.sendMessage("$prefix§cTransactionのmempool登録に失敗しました")
                }
                return@launchAsync
            }

            pendingSends[player.uniqueId]?.clear()

            plugin.runSync {
                player.sendMessage("$prefix§aTransactionを作成しました")
                player.sendMessage("$prefix§7txHash: §8${tx.txHash}")
                player.sendMessage("$prefix§7fee: §a${formatCoin(fee)}")
            }
        }
    }

    private suspend fun buildTransaction(
        senderAccount: Account,
        sends: List<PendingSend>,
        fee: Long,
        memo: String
    ): Transaction? {
        val privateKey = senderAccount.privateKey ?: return null

        val outputAmount = sends.fold(0L) { sum, send ->
            if (send.amount <= 0L) return null
            Math.addExact(sum, send.amount)
        }

        val requiredAmount = Math.addExact(outputAmount, fee)
        val selectedUtxos = mutableListOf<Utxo>()
        var selectedAmount = 0L

        val availableUtxos = plugin.repositories.utxoRepo
            .getAvailableUtxos(senderAccount.publicKey)
            .sortedBy { it.amount }

        for (utxo in availableUtxos) {
            selectedUtxos.add(utxo)
            selectedAmount = Math.addExact(selectedAmount, utxo.amount)

            if (selectedAmount >= requiredAmount) break
        }

        if (selectedAmount < requiredAmount) return null

        val unsignedInputs = selectedUtxos.map { utxo ->
            Transaction.TxInput(
                prevTxHash = utxo.outPoint.txHash,
                outputIndex = utxo.outPoint.outputIndex,
                signature = null,
                publicKey = senderAccount.publicKey
            )
        }

        val outputs = sends.map { send ->
            Transaction.TxOutput(
                amount = send.amount,
                receiverPubKey = send.receiverPubKey
            )
        }.toMutableList()

        val change = selectedAmount - requiredAmount
        if (change > 0L) {
            outputs.add(
                Transaction.TxOutput(
                    amount = change,
                    receiverPubKey = senderAccount.publicKey
                )
            )
        }

        val unsignedTx = Transaction(
            isCoinbase = false,
            inputs = unsignedInputs,
            outputs = outputs,
            timestamp = System.currentTimeMillis(),
            memo = memo.take(32)
        )

        val signature = Signer.sign(privateKey, unsignedTx.txHash)

        val signedInputs = unsignedInputs.map { input ->
            input.copy(signature = signature)
        }

        return unsignedTx.copy(
            inputs = signedInputs,
            txHash = unsignedTx.txHash
        )
    }

    private fun addPendingSend(player: Player, receiverPubKey: String, amount: Long) {
        val list = pendingSends.computeIfAbsent(player.uniqueId) { mutableListOf() }

        if (list.size >= MAX_SENDS_PER_TX) {
            player.sendMessage("$prefix§c送金リストに追加できるのは最大${MAX_SENDS_PER_TX}件です")
            return
        }

        list.add(
            PendingSend(
                receiverPubKey = receiverPubKey,
                amount = amount
            )
        )
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("byName", "byPubKey", "list", "delete", "create")
                .filter { it.startsWith(args[1]) }

            3 -> when (args[1]) {
                "byName", "byPubKey" -> listOf("<amount>").filter { it.startsWith(args[2]) }
                "delete" -> listOf("<index>", "all").filter { it.startsWith(args[2]) }
                "create" -> listOf("<fee>").filter { it.startsWith(args[2]) }
                else -> emptyList()
            }

            4 -> when (args[1]) {
                "byName" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[3]) }
                "byPubKey" -> listOf("[pubKey]").filter { it.startsWith(args[3]) }
                "create" -> listOf("[memo]").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }

    private data class PendingSend(
        val receiverPubKey: String,
        val amount: Long
    )
}