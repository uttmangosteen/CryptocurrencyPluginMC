package io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain

import java.security.MessageDigest

internal const val SHA_256_HASH_BYTES = 32

internal class Sha256Workspace {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    val nonceBytes: ByteArray = ByteArray(Long.SIZE_BYTES)
    val hashBytes: ByteArray = ByteArray(SHA_256_HASH_BYTES)
}

internal val localSha256Workspace = ThreadLocal.withInitial(::Sha256Workspace)

fun ByteArray.sha256(): ByteArray {
    val digest = localSha256Workspace.get().digest
    digest.reset()
    return digest.digest(this)
}
