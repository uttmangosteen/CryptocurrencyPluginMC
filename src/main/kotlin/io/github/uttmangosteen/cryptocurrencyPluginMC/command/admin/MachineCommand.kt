package io.github.uttmangosteen.cryptocurrencyPluginMC.command.admin

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import org.bukkit.Bukkit

class MachineCommand(
    private val plugin: Main,
) {
    fun execute(args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return

        when (args[0]) {
            "create" -> {
                val ownerName = args.getOrNull(2) ?: return
                create(machineId, ownerName)
            }

            "remove" -> remove(machineId)

            else -> return
        }
    }

    private fun create(machineId: String, ownerName: String) {
        val owner = Bukkit.getPlayerExact(ownerName) ?: return

        plugin.launchAsync {
            plugin.repositories.miningMachineRepo.create(
                machineId = machineId,
                ownerUuid = owner.uniqueId.toString(),
            )

        }
    }

    private fun remove(machineId: String) {
        plugin.launchAsync {
            plugin.miningMachineService?.modifyMachine(
                machineId = machineId,
                bypassPermission = true
            ) { machine ->
                machine.halt()
                true
            }

            plugin.repositories.miningMachineRepo.delete(
                machineId = machineId,
                bypassPermission = true
            )
        }
    }
}