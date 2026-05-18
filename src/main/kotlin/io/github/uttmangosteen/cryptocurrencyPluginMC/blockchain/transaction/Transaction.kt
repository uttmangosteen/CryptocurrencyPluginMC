package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.sha256
import java.nio.ByteBuffer

data class Transaction(
    val isCoinbase: Boolean = false,
    val inputs: List<TxInput>,
    val outputs: List<TxOutput>,
    val timestamp: Long,
    val memo: String = "",
    val txHash: String = calculateHash(isCoinbase, inputs, outputs, timestamp, memo)
) {
    companion object {
        private const val MEMO_MAX_LENGTH = 32

        fun calculateHash(
            isCoinbase: Boolean,
            inputs: List<TxInput>,
            outputs: List<TxOutput>,
            timestamp: Long,
            memo: String
        ): String {
            val safeMemo = memo.take(MEMO_MAX_LENGTH)
            val memoBytes = safeMemo.toByteArray(Charsets.UTF_8)

            val totalSize = 1 +
                    inputs.sumOf { 36 + it.publicKey.length / 2 } +
                    outputs.sumOf { 8 + it.receiverPubKey.length / 2 } +
                    8 +
                    memoBytes.size

            val buffer = ByteBuffer.allocate(totalSize)

            buffer.put(if (isCoinbase) 1.toByte() else 0.toByte())

            for (input in inputs) {
                buffer.put(input.prevTxHash.hexToByteArray())
                buffer.putInt(input.outputIndex)
                buffer.put(input.publicKey.hexToByteArray())
            }

            for (output in outputs) {
                buffer.putLong(output.amount)
                buffer.put(output.receiverPubKey.hexToByteArray())
            }

            buffer.putLong(timestamp)
            buffer.put(memoBytes)

            return buffer.array().sha256().toHexString()
        }

        fun createCoinbase(
            minerPubKey: String,
            rewardAmount: Long,
        ): Transaction {
            return Transaction(
                isCoinbase = true,
                inputs = emptyList(),
                outputs = listOf(TxOutput(amount = rewardAmount, receiverPubKey = minerPubKey)),
                timestamp = System.currentTimeMillis(),
                memo = "Coinbase"
            )
        }
    }

    fun isValid(): Boolean {
        if (txHash != calculateHash(isCoinbase, inputs, outputs, timestamp, memo)) return false
        if (outputs.any { it.amount <= 0 }) return false
        if (outputs.any { Signer.normalizePublicKey(it.receiverPubKey) == null }) return false

        if (isCoinbase) return inputs.isEmpty() && outputs.size == 1

        if (inputs.isEmpty()) return false
        if (inputs.any { Signer.normalizePublicKey(it.publicKey) == null }) return false

        return try {
            inputs.all { input ->
                val signature = input.signature ?: return false
                Signer.verify(input.publicKey, txHash, signature)
            }
        } catch (_: Exception) {
            false
        }
    }

    data class TxInput(
        val prevTxHash: String,
        val outputIndex: Int,
        val signature: String?,
        val publicKey: String
    )

    data class TxOutput(
        val amount: Long,
        val receiverPubKey: String
    )
}
