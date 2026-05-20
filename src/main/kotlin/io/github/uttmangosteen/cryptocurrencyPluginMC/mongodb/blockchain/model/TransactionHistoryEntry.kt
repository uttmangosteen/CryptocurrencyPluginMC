package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.model

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Utxo
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction

data class TransactionHistoryEntry(
    val txHash: String,
    val transaction: Transaction,
    val inputUtxos: List<Utxo>,
    val fee: Long,
    val height: Int,
    val blockTimestamp: Long,
    val txTimestamp: Long,
    val memo: String,
    val relatedPubKeys: List<String>
)