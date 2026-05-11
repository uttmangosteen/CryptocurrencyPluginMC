package io.github.uttmangosteen.cryptocurrencyPluginMC.util

import java.security.MessageDigest

//インスタンス生成
fun sha256Digest(): MessageDigest {
    return MessageDigest.getInstance("SHA-256")
}

//ByteArray→16進数文字列
fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

//16進数文字列→ByteArray
fun String.decodeHex(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

//ByteArray→(SHA-256)→ByteArray
fun ByteArray.sha256(): ByteArray {
    return sha256Digest().digest(this)
}

fun ByteArray.toSha256Hex(): String {
    return sha256().toHex()
}
