package io.github.uttmangosteen.cryptocurrencyPluginMC.command.user

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction

class TxMessageFactory {
    fun buildMessages(
        prefix: String,
        tx: Transaction,
        targetPubKeys: Set<String>,
        inputUtxos: List<Utxo>,
        fee: Long
    ): List<String> {
        val separate = "$prefix§8§l--------------------------------------------------------------"
        val messages = mutableListOf<String>()

        val memo = tx.memo.takeIf { it.isNotBlank() } ?: "no memo"
        messages += "$prefix§7memo: §f$memo"

        if (tx.isCoinbase) {
            val receivedAmount = tx.outputs
                .filter { it.receiverPubKey in targetPubKeys }
                .sumOf { it.amount }

            messages += "$prefix§a[MINE] +${TextFormat.formatCoin(receivedAmount)} §7(Coinbase)"
            messages += separate
            return messages
        }

        val senderPubKeys = tx.inputs.map { it.publicKey }.toSet()
        val isSender = senderPubKeys.any { it in targetPubKeys }

        if (!isSender) {
            val receivedAmount = tx.outputs
                .filter { it.receiverPubKey in targetPubKeys }
                .sumOf { it.amount }
            val senderPubKey = senderPubKeys.firstOrNull() ?: "unknown"

            messages += "$prefix§a[RECEIVE] +${TextFormat.formatCoin(receivedAmount)}"
            messages += "$prefix §7└§8$senderPubKey"
            messages += separate
            return messages
        }

        // 3. 自分が送信者の場合（UTXOの収支計算）
        val inputAmount = inputUtxos.sumOf { it.amount }

        val explicitSelfOutputs = tx.outputs.filter { it.receiverPubKey in targetPubKeys }
        val changeOutput = inferChangeOutput(
            selfOutputs = explicitSelfOutputs,
            inputAmount = inputAmount,
            fee = fee,
            tx = tx,
            targetPubKeys = targetPubKeys
        )

        val sentOutputs = tx.outputs.filter { it.receiverPubKey !in targetPubKeys }
        val selfReceiveAmount = explicitSelfOutputs.filter { it !== changeOutput }.sumOf { it.amount }
        val changeAmount = changeOutput?.amount ?: 0L

        val returnAmount = selfReceiveAmount + changeAmount

        messages += "$prefix§f[CONSUME] ${TextFormat.formatCoin(inputAmount)} §8(UTXO)"

        sentOutputs.forEach { output ->
            messages += "$prefix §7├§c[SEND] -${TextFormat.formatCoin(output.amount)}"
            messages += "$prefix §7│ └§8${output.receiverPubKey}"
        }

        messages += "$prefix §7├§c[FEE] -${TextFormat.formatCoin(fee)}"
        messages += "$prefix §7│ └§8MinerReward"

        val changeDetail = if (selfReceiveAmount > 0) "Change + Self" else "Change"
        messages += "$prefix §7└§f[RETURN] ${TextFormat.formatCoin(returnAmount)} §8($changeDetail)"
        messages += separate
        return messages
    }

    private fun inferChangeOutput(
        selfOutputs: List<Transaction.TxOutput>,
        inputAmount: Long,
        fee: Long,
        tx: Transaction,
        targetPubKeys: Set<String>
    ): Transaction.TxOutput? {
        if (selfOutputs.isEmpty()) return null

        val externalOutputAmount = tx.outputs
            .filter { it.receiverPubKey !in targetPubKeys }
            .sumOf { it.amount }

        val expectedChange = inputAmount - externalOutputAmount - fee
        if (expectedChange <= 0L) return null

        return selfOutputs.lastOrNull { it.amount == expectedChange }
            ?: selfOutputs.lastOrNull()
    }
}