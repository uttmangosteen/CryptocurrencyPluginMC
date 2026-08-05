package io.github.uttmangosteen.cryptocurrencyPluginMC.mongodb

import com.mongodb.kotlin.client.coroutine.ClientSession
import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import kotlinx.coroutines.CancellationException
import java.util.logging.Logger

sealed interface MongoTransactionOutcome<out T> {
    data class Commit<T>(val value: T) : MongoTransactionOutcome<T>
    data class Abort<T>(val value: T) : MongoTransactionOutcome<T>
}

class MongoTransactionRunner(
    private val provider: MongoDatabaseProvider,
    private val logger: Logger
) {
    suspend fun <T> run(
        operation: String,
        block: suspend (ClientSession) -> MongoTransactionOutcome<T>,
        onFailure: (Exception) -> Unit = { error ->
            logger.ccWarning(LogComponent.DATABASE, operation, error)
        }
    ): T? {
        var session: ClientSession? = null
        var transactionStarted = false
        var transactionCommitted = false

        try {
            session = provider.startSession()
            session.startTransaction()
            transactionStarted = true

            return when (val outcome = block(session)) {
                is MongoTransactionOutcome.Commit -> {
                    session.commitTransaction()
                    transactionCommitted = true
                    outcome.value
                }

                is MongoTransactionOutcome.Abort -> outcome.value
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
            return null
        } finally {
            val activeSession = session
            if (activeSession != null) {
                if (transactionStarted && !transactionCommitted) {
                    try {
                        activeSession.abortTransaction()
                    } catch (error: Exception) {
                        logger.ccWarning(
                            LogComponent.DATABASE,
                            "failed to abort MongoDB transaction",
                            error,
                            "operation" to operation
                        )
                    }
                }
                activeSession.close()
            }
        }
    }
}
