package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

data class TxInput(
    val prevTxHash: ByteArray,
    val outputIndex: Int,
    val signature: ByteArray?,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TxInput

        if (outputIndex != other.outputIndex) return false
        if (!prevTxHash.contentEquals(other.prevTxHash)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outputIndex
        result = 31 * result + prevTxHash.contentHashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
