package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb.blockchain.transactionhistory

data class TransactionHistoryEntry(
    val txHash: String,
    val senderPubKeyHash: String?,
    val receiverPubKeyHash: String,
    val amount: Long,
    val height: Int,
    val blockTimestamp: Long,
    val txTimestamp: Long,
)