package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

data class Utxo(
    val outPoint: OutPoint,
    val txHash: ByteArray,
    val amount: Long,
    val receiverPubKey: ByteArray,
    val receiverPubKeyHash: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Utxo

        if (amount != other.amount) return false
        if (outPoint != other.outPoint) return false
        if (!txHash.contentEquals(other.txHash)) return false
        if (!receiverPubKey.contentEquals(other.receiverPubKey)) return false
        if (receiverPubKeyHash != other.receiverPubKeyHash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + outPoint.hashCode()
        result = 31 * result + txHash.contentHashCode()
        result = 31 * result + receiverPubKey.contentHashCode()
        result = 31 * result + receiverPubKeyHash.hashCode()
        return result
    }
}
