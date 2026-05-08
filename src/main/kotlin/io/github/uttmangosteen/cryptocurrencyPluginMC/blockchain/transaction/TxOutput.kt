package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

data class TxOutput(
    val amount: Long,
    val receiverPubKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxOutput

        if (amount != other.amount) return false
        if (!receiverPubKey.contentEquals(other.receiverPubKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + receiverPubKey.contentHashCode()
        return result
    }
}
