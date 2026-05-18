package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

object TextFormat {
    const val COIN_NAME: String = "MNC"
    //1MNC = 10万Takashi という仮設定
    const val COIN_FRACTION_DIGITS: Int = 5

    fun formatCoin(amount: Long): String {
        val whole = amount / CoinbasePolicy.COIN_SCALE
        val fraction = amount % CoinbasePolicy.COIN_SCALE
        return "%d.%0${COIN_FRACTION_DIGITS}d %s".format(
            whole,
            fraction,
            COIN_NAME
        )
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