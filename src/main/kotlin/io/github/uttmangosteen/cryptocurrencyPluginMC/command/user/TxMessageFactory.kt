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
        val messages = mutableListOf<String>()
        val memo = tx.memo.takeIf { it.isNotBlank() } ?: "no memo"

        messages += "$prefix§7memo: §f$memo"

        if (tx.isCoinbase) {
            tx.outputs
                .filter { it.receiverPubKey in targetPubKeys }
                .forEach { output ->
                    messages += "$prefix§a[Receive] +${TextFormat.formatCoin(output.amount)} §7(coinbase)"
                }
            return messages
        }

        val senderPubKeys = tx.inputs.map { it.publicKey }.toSet()
        val isSender = senderPubKeys.any { it in targetPubKeys }
        val senderLabel = senderPubKeys.firstOrNull() ?: "unknown"

        if (!isSender) {
            tx.outputs
                .filter { it.receiverPubKey in targetPubKeys }
                .forEach { output ->
                    messages += "$prefix§a[Receive] +${TextFormat.formatCoin(output.amount)} §7<- §8$senderLabel"
                }
            return messages
        }

        val inputAmount = inputUtxos.sumOf { it.amount }
        messages += "$prefix§c[UTXO] -${TextFormat.formatCoin(inputAmount)}"

        if (fee > 0L) {
            messages += "$prefix§c[fee] -${TextFormat.formatCoin(fee)} §7-> §6MinerReward"
        }

        val explicitSelfOutputs = tx.outputs
            .filter { it.receiverPubKey in targetPubKeys }

        val changeOutput = inferChangeOutput(
            selfOutputs = explicitSelfOutputs,
            inputAmount = inputAmount,
            fee = fee,
            tx = tx,
            targetPubKeys = targetPubKeys
        )

        tx.outputs.forEach { output ->
            when {
                changeOutput === output -> {
                    messages += "$prefix§a[Change] +${TextFormat.formatCoin(output.amount)}"
                }

                output.receiverPubKey in targetPubKeys -> {
                    messages += "$prefix§a[Receive] +${TextFormat.formatCoin(output.amount)} §7<- §8${output.receiverPubKey}"
                }

                else -> {
                    messages += "$prefix§c[Send] -${TextFormat.formatCoin(output.amount)} §7-> §8${output.receiverPubKey}"
                }
            }
        }

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