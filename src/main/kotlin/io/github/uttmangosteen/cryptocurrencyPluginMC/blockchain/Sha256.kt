package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import java.security.MessageDigest

internal val localDigest = ThreadLocal.withInitial {
    MessageDigest.getInstance("SHA-256")
}

fun ByteArray.sha256(): ByteArray {
    val digest = localDigest.get()
    digest.reset()
    return digest.digest(this)
}