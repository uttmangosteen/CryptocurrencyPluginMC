package io.github.uttmangosteen.cryptocurrencyPluginMC.util

import java.security.MessageDigest

fun sha256Digest(): MessageDigest {
    return MessageDigest.getInstance("SHA-256")
}

fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

fun ByteArray.sha256(): ByteArray {
    return sha256Digest().digest(this)
}

fun ByteArray.toSha256Hex(): String {
    return sha256().toHex()
}
