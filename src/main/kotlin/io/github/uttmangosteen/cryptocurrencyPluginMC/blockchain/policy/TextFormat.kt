package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy

class TextFormat {
    private val fractionDigits: Int = CoinbasePolicy.COIN_SCALE
        .toString()
        .length - 1

    fun format(amount: Long): String {
        val whole = amount / CoinbasePolicy.COIN_SCALE
        val fraction = amount % CoinbasePolicy.COIN_SCALE
        return "%d.%0${fractionDigits}d %s".format(
            whole,
            fraction,
            CoinbasePolicy.COIN_NAME
        )
    }

    fun parse(value: String?): Long? {
        if (value == null) return null

        val normalized = value.trim()
        if (normalized.isBlank()) return null

        val parts = normalized.split(".")
        if (parts.size > 2) return null

        val whole = parts[0].toLongOrNull() ?: return null
        if (whole < 0L) return null

        val fraction = if (parts.size == 2) {
            val raw = parts[1]
            if (raw.length > fractionDigits) return null
            raw.padEnd(fractionDigits, '0').toLongOrNull() ?: return null
        } else {
            0L
        }

        return Math.addExact(
            Math.multiplyExact(whole, CoinbasePolicy.COIN_SCALE),
            fraction
        )
    }
}