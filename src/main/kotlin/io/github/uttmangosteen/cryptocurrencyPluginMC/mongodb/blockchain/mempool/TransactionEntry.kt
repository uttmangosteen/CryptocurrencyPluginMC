package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.mempool

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction

// Mempool内で扱うトランザクション情報
data class TransactionEntry(
    val transaction: Transaction,

    // 以下検索高速化用
    val txHash: String = transaction.txHash,

    val fee: Long,

    val timestamp: Long = transaction.timestamp,
    val pubkeyList: List<String> = extractPubKeys(transaction),
    val outpoints: List<OutPoint> = extractConsumedOutpoints(transaction),
) {

    companion object {
        private fun extractPubKeys(tx: Transaction): List<String> {
            val inputKeys = tx.inputs.map { it.publicKey }
            val outputKeys = tx.outputs.map { it.receiverPubKey }
            return (inputKeys + outputKeys).distinct()
        }

        private fun extractConsumedOutpoints(tx: Transaction): List<OutPoint> {
            return tx.inputs.map { input ->
                OutPoint(
                    txHash = input.prevTxHash,
                    outputIndex = input.outputIndex
                )
            }
        }
    }
}