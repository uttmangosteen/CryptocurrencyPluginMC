package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import java.security.MessageDigest

private val digestThreadLocal = ThreadLocal.withInitial {
    MessageDigest.getInstance("SHA-256")
}

fun ByteArray.sha256(): ByteArray {
    val digest = digestThreadLocal.get()
    digest.reset()
    return digest.digest(this)
}