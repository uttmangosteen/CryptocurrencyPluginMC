package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

// この情報でUTXOが一意に定まる
data class OutPoint(
    val txHashHex: String,
    val outputIndex: Int
)
