package io.github.uttmangosteen.cryptocurrencyPluginMC

import io.github.uttmangosteen.cryptocurrencyPluginMC.util.toHex
import java.util.Collections
import java.util.WeakHashMap
import java.util.logging.Level
import java.util.logging.Logger

enum class LogComponent(val label: String) {
    DATABASE("Database"),
    MEMPOOL_REPOSITORY("MempoolRepository"),
    WALLET_REPOSITORY("WalletRepository"),
}

private val verboseEnabled: MutableSet<Logger> = Collections.newSetFromMap(WeakHashMap())

fun Logger.setVerbose(enabled: Boolean) {
    if (enabled) {
        verboseEnabled.add(this)
    } else {
        verboseEnabled.remove(this)
    }
}

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

//重大エラー
fun Logger.ccSevere(
    component: LogComponent,
    message: String,
    cause: Throwable,
    vararg params: Pair<String, Any?>
) {
    ccLog(Level.SEVERE, component, message, cause, *params)
}

//デバック用ログ
fun Logger.mncVerbose(
    component: LogComponent,
    message: String,
    vararg params: Pair<String, Any?>
) {
    if (this !in verboseEnabled) return
    ccLog(Level.INFO, component, message, null, *params)
}

private fun Logger.ccLog(
    level: Level,
    component: LogComponent,
    message: String,
    cause: Throwable?,
    vararg params: Pair<String, Any?>
) {
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
            append(value.formatLogValue())
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
        is ByteArray -> toHex()
        is Throwable -> "${this::class.simpleName}:${message.orEmpty().sanitizeLogValue()}"
        else -> toString().sanitizeLogValue()
    }
}

// ログのキー名から英数字・_・-.以外を _ に置換する (ログ注入対策)
private fun String.sanitizeLogKey(): String {
    return replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

// ログの値に含まれる改行・タブをエスケープする (ログ注入対策)
private fun String.sanitizeLogValue(): String {
    return replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
}