package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

// この情報でUTXOが一意に定まる
data class OutPoint(
    val txHash: String,
    val outputIndex: Int
)
