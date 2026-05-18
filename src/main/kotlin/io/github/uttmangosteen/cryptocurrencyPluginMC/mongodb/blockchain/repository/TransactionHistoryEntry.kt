package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.repository

data class TransactionHistoryEntry(
    val txHash: String,
    val senderPubKey: String?,
    val receiverPubKey: String,
    val amount: Long,
    val height: Int,
    val blockTimestamp: Long,
    val txTimestamp: Long,
)