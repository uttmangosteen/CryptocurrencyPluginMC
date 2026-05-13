package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.mempool

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex

// Mempool内で扱うトランザクション情報
data class TransactionEntry(
    val transaction: Transaction,

    // 以下検索高速化用
    val fee: Long,

    val txHashHex: String = transaction.txHash.toHex(),

    val timestamp: Long = transaction.timestamp,
    val pubkeyList: List<String> = extractPubKeys(transaction),
    val outpoints: List<OutPoint> = extractConsumedOutpoints(transaction),
) {

    companion object {
        private fun extractPubKeys(tx: Transaction): List<String> {
            val inputKeys = tx.inputs.map { it.publicKey.toHex() }
            val outputKeys = tx.outputs.map { it.receiverPubKey.toHex() }
            return (inputKeys + outputKeys).distinct()
        }

        private fun extractConsumedOutpoints(tx: Transaction): List<OutPoint> {
            return tx.inputs.map { input ->
                OutPoint(
                    txHashHex = input.prevTxHash.toHex(),
                    outputIndex = input.outputIndex
                )
            }
        }
    }
}