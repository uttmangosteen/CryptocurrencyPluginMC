package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.transaction.Transaction
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.sha256Digest
import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import java.nio.ByteBuffer
import java.security.MessageDigest

data class Block(
    val height: Int,
    val previousHash: ByteArray,
    val transactions: List<Transaction>,
    val transactionsRoot: ByteArray = calculateTransactionsRoot(transactions),
    val timestamp: Long,
    val memo: String = "",
    val difficulty: Int,
    var nonce: Long = 0,
    var hash: ByteArray? = null
) {

    companion object {
        // memoのバイト長上限
        private const val MEMO_SIZE = 48
        private const val SPACE_BYTE = ' '.code.toByte()

        fun calculateTransactionsRoot(txs: List<Transaction>): ByteArray {
            return sha256Digest().run {
                txs.forEach { update(it.txHash) }
                digest()
            }
        }

        fun isMined(hash: ByteArray, diff: Int): Boolean {
            if (diff < 0) return false
            if (diff > hash.size * 2) return false
            val full = diff / 2
            if (!(0 until full).all { hash[it] == 0.toByte() }) return false
            return diff % 2 == 0 || (hash[full].toInt() and 0xF0) == 0
        }

        fun mine(base: MessageDigest, nonce: Long, diff: Int): ByteArray? {
            val hash = (base.clone() as MessageDigest).digest(
                ByteBuffer.allocate(8).putLong(nonce).array()
            )
            return hash.takeIf { isMined(it, diff) }
        }
    }

    fun prepareMining(): MessageDigest = sha256Digest().apply {
        val memoBytes = ByteArray(MEMO_SIZE) { SPACE_BYTE }
        memo.toByteArray(Charsets.UTF_8).let { rawMemo ->
            System.arraycopy(rawMemo, 0, memoBytes, 0, minOf(rawMemo.size, MEMO_SIZE))
        }
        val fixedData = ByteBuffer.allocate(80 + MEMO_SIZE)
            .putInt(height)
            .put(previousHash)
            .put(transactionsRoot)
            .putLong(timestamp)
            .put(memoBytes)
            .putInt(difficulty)
            .array()
        update(fixedData)
    }

    fun isValid(expectedDifficulty: Int): Boolean {
        if (this.difficulty != expectedDifficulty) return false
        val blockHash = this.hash ?: run { return false }
        val verifiedHash = mine(prepareMining(), nonce, difficulty) ?: return false
        if (!verifiedHash.contentEquals(blockHash)) return false
        if (!this.transactionsRoot.contentEquals(calculateTransactionsRoot(this.transactions))) return false
        val spentOutpoints = mutableSetOf<OutPoint>()
        for (tx in transactions) {
            if (!tx.isValid()) return false
            for (input in tx.inputs) {
                val outpoint = OutPoint(
                    txHashHex = input.prevTxHash.toHex(),
                    outputIndex = input.outputIndex
                )
                if (!spentOutpoints.add(outpoint)) return false
            }
        }
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Block

        if (height != other.height) return false
        if (timestamp != other.timestamp) return false
        if (difficulty != other.difficulty) return false
        if (nonce != other.nonce) return false
        if (!previousHash.contentEquals(other.previousHash)) return false
        if (transactions != other.transactions) return false
        if (!transactionsRoot.contentEquals(other.transactionsRoot)) return false
        if (memo != other.memo) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = height
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + difficulty
        result = 31 * result + nonce.hashCode()
        result = 31 * result + previousHash.contentHashCode()
        result = 31 * result + transactions.hashCode()
        result = 31 * result + transactionsRoot.contentHashCode()
        result = 31 * result + memo.hashCode()
        result = 31 * result + (hash?.contentHashCode() ?: 0)
        return result
    }
}
