package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class MachineGpuCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix

    fun execute(player: Player, args: Array<out String>) {
        val action = args.getOrNull(1) ?: return
        val machineId = args.getOrNull(2) ?: return
        val slot = args.getOrNull(3)?.toIntOrNull() ?: return

        when (action.lowercase()) {
            "set" -> setGpu(player, machineId, slot)
            "take" -> takeGpu(player, machineId, slot)
        }
    }

    private fun setGpu(player: Player, machineId: String, slot: Int) {
        val item = player.inventory.itemInMainHand
        val gpu = readGpuFromItem(item)

        if (gpu == null) {
            player.sendMessage("$prefix§c有効なGPUアイテムを手に持ってください")
            return
        }

        plugin.launchAsync {
            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                machine.setGpu(slot, gpu)
            }

            plugin.runSync {
                if (updated) {
                    item.amount -= 1
                    player.sendMessage("$prefix§aGPUをslot $slot に設置しました")
                } else {
                    player.sendMessage("$prefix§cGPUの設置に失敗しました")
                }
            }
        }
    }

    private fun takeGpu(player: Player, machineId: String, slot: Int) {
        plugin.launchAsync {
            var takenGpu: Gpu? = null

            val updated = plugin.repositories.miningMachineRepo.updateMachine(
                machineId = machineId,
                requesterUuid = player.uniqueId.toString()
            ) { machine ->
                val gpu = machine.takeGpu(slot) ?: return@updateMachine false
                takenGpu = gpu
                true
            }

            plugin.runSync {
                val gpu = takenGpu

                if (!updated || gpu == null) {
                    player.sendMessage("$prefix§cGPUの取り外しに失敗しました")
                    return@runSync
                }

                val item = gpu.createItem(plugin)
                val overflow = player.inventory.addItem(item)
                overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

                player.sendMessage("$prefix§aGPUをslot $slot から取り外しました")
            }
        }
    }

    private fun readGpuFromItem(item: ItemStack?): Gpu? {
        val reader = Gpu(
            gpuName = "",
            material = Material.IRON_INGOT.name,
            description = "",
            life = 0,
            breakChance = 0.0,
            power = 1
        )
        return reader.createGpu(item, plugin)
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("set", "take").filter { it.startsWith(args[1]) }
            3 -> listOf("<machineId>").filter { it.startsWith(args[2]) }
            4 -> listOf("<slot>").filter { it.startsWith(args[3]) }
            else -> emptyList()
        }
    }
}