package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.GpuItems
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.ItemKeys
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bukkit.entity.Player

class MachineGpuCommand(
    private val plugin: Main
) {
    private val prefix = plugin.pluginConfig.prefix
    private val gpuKeys = ItemKeys.gpu(plugin)

    fun execute(player: Player, args: Array<out String>) {
        val machineId = args.getOrNull(1) ?: return
        val slot = args.getOrNull(2)?.toIntOrNull() ?: return

        plugin.launchAsync {
            val machine = plugin.miningMachineService?.getMachine(machineId)

            if (machine == null ||
                (!machine.canAccess(player.uniqueId.toString()) && !player.hasPermission("cryptocurrency.admin"))
            ) {
                plugin.runSync { player.sendMessage("$prefix§cGPUの操作はできません") }
                return@launchAsync
            }

            val hasGpu = machine.gpuSlots.getOrNull(slot) != null
            if (hasGpu) {
                removeGpu(player, machineId, slot)
            } else {
                setGpu(player, machineId, slot)
            }
        }
    }

    private suspend fun removeGpu(player: Player, machineId: String, slot: Int) {
        var removedGpu: Gpu? = null

        val updated = plugin.miningMachineService?.modifyMachine(
            machineId = machineId,
            requesterUuid = player.uniqueId.toString(),
            requireOwner = false,
            bypassPermission = player.hasPermission("cryptocurrency.admin")
        ) { targetMachine ->
            removedGpu = targetMachine.takeGpu(slot) ?: return@modifyMachine false
            true
        } ?: false

        plugin.runSync {
            val gpu = removedGpu
            if (!updated || gpu == null) {
                player.sendMessage("$prefix§cGPUの取り外しに失敗しました")
                return@runSync
            }

            val gpuItem = GpuItems.create(gpu, gpuKeys)
            val overflow = player.inventory.addItem(gpuItem)
            overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

            player.sendMessage("$prefix§aGPUをslot $slot から取り外しました")
        }
    }

    private fun setGpu(player: Player, machineId: String, slot: Int) {
        plugin.runSync {
            val handItem = player.inventory.itemInMainHand
            val gpuToSet = GpuItems.read(handItem, gpuKeys)

            if (gpuToSet == null) {
                player.sendMessage("$prefix§cGPUを手に持ってください")
                return@runSync
            }

            handItem.amount -= 1

            plugin.launchAsync {
                val updated = plugin.miningMachineService?.modifyMachine(
                    machineId = machineId,
                    requesterUuid = player.uniqueId.toString(),
                    requireOwner = false,
                    bypassPermission = player.hasPermission("cryptocurrency.admin")
                ) { targetMachine ->
                    targetMachine.setGpu(slot, gpuToSet)
                } ?: false

                plugin.runSync {
                    if (updated) {
                        player.sendMessage("$prefix§aGPUをslot $slot にセットしました")
                    } else {
                        val refundItem = GpuItems.create(gpuToSet, gpuKeys)
                        val overflow = player.inventory.addItem(refundItem)
                        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

                        player.sendMessage("$prefix§cGPUのセットに失敗したため、GPUを返却しました")
                    }
                }
            }
        }
    }

    fun getTabCompletions(args: Array<out String>): List<String> {
        return when (args.size) {
            2 -> listOf("<machineId>").filter { it.startsWith(args[1]) }
            3 -> listOf("<slot>").filter { it.startsWith(args[2]) }
            else -> emptyList()
        }
    }
}