package io.github.uttmangosteen.cryptocurrencyPluginMC.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommandRateLimiter(
    private val intervalMillis: Long
) {
    private val lastExecutionMillis = ConcurrentHashMap<UUID, Long>()

    fun tryAcquire(sender: CommandSender): Boolean {
        if (intervalMillis <= 0L) return true
        if (sender !is Player) return true

        val now = System.currentTimeMillis()
        val uuid = sender.uniqueId
        while (true) {
            val last = lastExecutionMillis[uuid]
            if (last != null && now - last < intervalMillis) {
                return false
            }
            if (last == null) {
                if (lastExecutionMillis.putIfAbsent(uuid, now) == null) return true
            } else {
                if (lastExecutionMillis.replace(uuid, last, now)) return true
            }
        }
    }
}