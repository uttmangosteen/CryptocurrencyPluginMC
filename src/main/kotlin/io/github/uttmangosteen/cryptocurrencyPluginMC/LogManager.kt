package io.github.uttmangosteen.cryptocurrencyPluginMC

import java.util.logging.Level
import java.util.logging.Logger

enum class LogComponent(val label: String) {
    DATABASE("Database"),
    MEMPOOL_REPOSITORY("MempoolRepository"),
    WALLET_REPOSITORY("WalletRepository"),
    BLOCK_REPOSITORY("BlockRepository"),
    MINING_MACHINE_REPOSITORY("MiningMachineRepository"),
    TRANSACTION_HISTORY_REPOSITORY("TransactionHistoryRepository"),
    UTXO_REPOSITORY("UtxoRepository"),
    MINING_MACHINE("MiningMachine")
}

private const val MAX_LOGGED_BYTE_ARRAY_BYTES = 64

//通常ログ
fun Logger.ccInfo(
    component: LogComponent,
    message: String,
    vararg params: Pair<String, Any?>
) {
    ccLog(Level.INFO, component, message, null, *params)
}

//エラー
fun Logger.ccWarning(
    component: LogComponent,
    message: String,
    cause: Throwable,
    vararg params: Pair<String, Any?>
) {
    ccLog(Level.WARNING, component, message, cause, *params)
}

fun Logger.ccWarning(
    component: LogComponent,
    message: String,
    vararg params: Pair<String, Any?>
) {
    ccLog(Level.WARNING, component, message, null, *params)
}

//重大エラー
fun Logger.ccSevere(
    component: LogComponent,
    message: String,
    cause: Throwable,
    vararg params: Pair<String, Any?>
) {
    ccLog(Level.SEVERE, component, message, cause, *params)
}

private fun Logger.ccLog(
    level: Level,
    component: LogComponent,
    message: String,
    cause: Throwable?,
    vararg params: Pair<String, Any?>
) {
    if (!isLoggable(level)) return

    val formattedMessage = buildString {
        append("[")
        append(component.label)
        append("] ")
        append(message.sanitizeLogValue())

        // キーバリューペアを "key=value" 形式で追記
        for ((key, value) in params) {
            append(" ")
            append(key.sanitizeLogKey())
            append("=")
            append('"')
            append(value.formatLogValue())
            append('"')
        }
    }

    if (cause == null) {
        log(level, formattedMessage)
    } else {
        log(level, formattedMessage, cause)
    }
}

// ログの値を文字列にフォーマットする
// ByteArray は16進数文字列、例外はクラス名+メッセージ
private fun Any?.formatLogValue(): String {
    return when (this) {
        null -> "null"
        is ByteArray -> formatByteArray()
        is Throwable -> "${this::class.simpleName}:${message.orEmpty().sanitizeLogValue()}"
        else -> runCatching { toString() }
            .getOrElse { "<toString failed: ${it::class.simpleName}>" }
            .sanitizeLogValue()
    }
}

private fun ByteArray.formatByteArray(): String {
    if (size <= MAX_LOGGED_BYTE_ARRAY_BYTES) return toHexString()

    return copyOf(MAX_LOGGED_BYTE_ARRAY_BYTES).toHexString() + "...(bytes=$size)"
}

// ログのキー名から英数字・_・-.以外を _ に置換する (ログ注入対策)
private fun String.sanitizeLogKey(): String {
    return buildString(length) {
        for (char in this@sanitizeLogKey) {
            if (char.isAsciiLogKeyCharacter()) {
                append(char)
            } else {
                append('_')
            }
        }
    }.ifEmpty { "_" }
}

private fun Char.isAsciiLogKeyCharacter(): Boolean {
    return this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '_' || this == '.' || this == '-'
}

// ログの値に含まれる改行・タブをエスケープする (ログ注入対策)
private fun String.sanitizeLogValue(): String {
    return buildString(length) {
        for (char in this@sanitizeLogValue) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> {
                    if (Character.isISOControl(char)) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}
