package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

data class Utxo(
    val outPoint: OutPoint,
    val amount: Long,
    val receiverPubKey: String,
    val lockedByTxId: String? = null
)
