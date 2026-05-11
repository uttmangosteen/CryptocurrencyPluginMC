package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

import io.github.uttmangosteen.cryptocurrencyPluginMC.util.sha256
import java.nio.ByteBuffer

data class Transaction(
    val isCoinbase: Boolean = false,
    val inputs: List<TxInput>,
    val outputs: List<TxOutput>,
    val timestamp: Long,
    val memo: String = "",
    val txHash: ByteArray = calculateHash(isCoinbase, inputs, outputs, timestamp, memo)
) {
    companion object {
        // memoのバイト長上限
        private const val MEMO_SIZE = 48
        private const val SPACE_BYTE = ' '.code.toByte()

        fun calculateHash(
            isCoinbase: Boolean,
            inputs: List<TxInput>,
            outputs: List<TxOutput>,
            timestamp: Long,
            memo: String
        ): ByteArray {
            val memoBytes = ByteArray(MEMO_SIZE) { SPACE_BYTE }
            memo.toByteArray(Charsets.UTF_8).let { rawMemo ->
                System.arraycopy(rawMemo, 0, memoBytes, 0, minOf(rawMemo.size, MEMO_SIZE))
            }

            val totalSize = 1 +
                inputs.sumOf { 36 + it.publicKey.size } +
                outputs.sumOf { 8 + it.receiverPubKey.size } +
                8 +
                MEMO_SIZE

            val buffer = ByteBuffer.allocate(totalSize)

            buffer.put(if (isCoinbase) 1.toByte() else 0.toByte())

            for (input in inputs) {
                buffer.put(input.prevTxHash)
                buffer.putInt(input.outputIndex)
                buffer.put(input.publicKey)
            }

            for (output in outputs) {
                buffer.putLong(output.amount)
                buffer.put(output.receiverPubKey)
            }

            buffer.putLong(timestamp)
            buffer.put(memoBytes)

            return buffer.array().sha256()
        }

        fun createCoinbase(
            minerPubKey: ByteArray,
            rewardAmount: Long,
            memo: String = "Coinbase"
        ): Transaction {
            return Transaction(
                isCoinbase = true,
                inputs = emptyList(),
                outputs = listOf(TxOutput(amount = rewardAmount, receiverPubKey = minerPubKey)),
                timestamp = System.currentTimeMillis(),
                memo = memo
            )
        }
    }

    fun isValid(): Boolean {
        if (!txHash.contentEquals(calculateHash(isCoinbase, inputs, outputs, timestamp, memo))) return false
        if (outputs.any { it.amount <= 0 }) return false
        if (isCoinbase) return inputs.isEmpty() && outputs.size == 1
        if (inputs.isEmpty()) return false
        return try {
            inputs.all { input ->
                val signature = input.signature ?: return false
                Signer.verify(input.publicKey, txHash, signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Transaction

        if (isCoinbase != other.isCoinbase) return false
        if (timestamp != other.timestamp) return false
        if (inputs != other.inputs) return false
        if (outputs != other.outputs) return false
        if (memo != other.memo) return false
        if (!txHash.contentEquals(other.txHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isCoinbase.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + inputs.hashCode()
        result = 31 * result + outputs.hashCode()
        result = 31 * result + memo.hashCode()
        result = 31 * result + txHash.contentHashCode()
        return result
    }
}
