package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

object TextFormat {
    const val COIN_NAME: String = "MTC"

    //1MTC = 10万Takashi という仮設定
    const val COIN_FRACTION_DIGITS: Int = 5

    fun formatCoin(amount: Long): String {
        val sign = if (amount < 0L) "-" else ""
        val absoluteAmount = if (amount == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            kotlin.math.abs(amount)
        }

        val whole = absoluteAmount / CoinbasePolicy.COIN_SCALE
        val fraction = absoluteAmount % CoinbasePolicy.COIN_SCALE

        return "%s%d.%0${COIN_FRACTION_DIGITS}d %s".format(
            sign,
            whole,
            fraction,
            COIN_NAME
        )
    }

    fun formatKey(key: String, prefixLen: Int = 6, suffixLen: Int = 6): String {
        if (key.length <= prefixLen + suffixLen + 3) {
            return key
        }
        val prefix = key.take(prefixLen)
        val suffix = key.takeLast(suffixLen)
        return "$prefix...$suffix"
    }

    fun parseCoin(value: String?): Long? {
        if (value == null) return null

        val normalized = value.trim()
        if (normalized.isBlank()) return null

        val parts = normalized.split(".")
        if (parts.size > 2) return null

        val whole = parts[0].toLongOrNull() ?: return null
        if (whole < 0L) return null

        val fraction = if (parts.size == 2) {
            val raw = parts[1]
            if (raw.length > COIN_FRACTION_DIGITS) return null
            raw.padEnd(COIN_FRACTION_DIGITS, '0').toLongOrNull() ?: return null
        } else {
            0L
        }

        return Math.addExact(
            Math.multiplyExact(whole, CoinbasePolicy.COIN_SCALE),
            fraction
        )
    }
}