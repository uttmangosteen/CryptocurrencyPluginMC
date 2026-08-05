package io.github.uttmangosteen.cryptocurrencyPluginMC.command.miner

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.GpuItems
import io.github.uttmangosteen.cryptocurrencyPluginMC.item.ItemKeys
import io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine.gpu.Gpu
import org.bukkit.Sound
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
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE,
                    "GPU operation denied",
                    "machineId" to machineId,
                    "slot" to slot,
                    "player" to player.name,
                    "playerUuid" to player.uniqueId,
                    "reason" to if (machine == null) "machine_not_found" else "access_denied"
                )
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
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE,
                    "Failed to remove GPU from mining machine",
                    "machineId" to machineId,
                    "slot" to slot,
                    "player" to player.name,
                    "playerUuid" to player.uniqueId
                )
                player.sendMessage("$prefix§cGPUの取り外しに失敗しました")
                return@runSync
            }

            val gpuItem = GpuItems.create(gpu, gpuKeys)
            val overflow = player.inventory.addItem(gpuItem)
            overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

            plugin.logger.ccInfo(
                LogComponent.MINING_MACHINE,
                "GPU removed from mining machine",
                "machineId" to machineId,
                "slot" to slot,
                "player" to player.name,
                "playerUuid" to player.uniqueId,
                "gpuName" to gpu.gpuName,
                "gpuMaterial" to gpu.material,
                "gpuLife" to gpu.life,
                "gpuPower" to gpu.power,
                "droppedAmount" to overflow.values.sumOf { it.amount }
            )
            if (overflow.isNotEmpty()) {
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE,
                    "Removed GPU dropped because the player inventory was full",
                    "machineId" to machineId,
                    "slot" to slot,
                    "player" to player.name,
                    "playerUuid" to player.uniqueId,
                    "gpuName" to gpu.gpuName,
                    "droppedAmount" to overflow.values.sumOf { it.amount }
                )
            }

            player.sendMessage("$prefix§aGPUをslot $slot から取り外しました")
            player.playSound(
                player.location,
                Sound.ITEM_LEAD_BREAK,
                1.0f,
                0.7f
            )

            //man10FunctionalEquipmentの表示更新
            val displayUpdated = plugin.server.dispatchCommand(
                plugin.server.consoleSender,
                "mfe display flag $machineId $slot false"
            )
            if (!displayUpdated) {
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE,
                    "Failed to hide GPU display after removal",
                    "machineId" to machineId,
                    "slot" to slot
                )
            }
        }
    }

    private fun setGpu(player: Player, machineId: String, slot: Int) {
        plugin.runSync {
            val handItem = player.inventory.itemInMainHand
            val gpuToSet = GpuItems.read(handItem, gpuKeys)

            if (gpuToSet == null) {
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE,
                    "GPU placement rejected because the held item was not a GPU",
                    "machineId" to machineId,
                    "slot" to slot,
                    "player" to player.name,
                    "playerUuid" to player.uniqueId,
                    "itemType" to handItem.type
                )
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
                        plugin.logger.ccInfo(
                            LogComponent.MINING_MACHINE,
                            "GPU installed in mining machine",
                            "machineId" to machineId,
                            "slot" to slot,
                            "player" to player.name,
                            "playerUuid" to player.uniqueId,
                            "gpuName" to gpuToSet.gpuName,
                            "gpuMaterial" to gpuToSet.material,
                            "gpuLife" to gpuToSet.life,
                            "gpuPower" to gpuToSet.power
                        )
                        player.sendMessage("$prefix§aGPUをslot $slot にセットしました")
                        player.playSound(
                            player.location,
                            Sound.ITEM_LEAD_BREAK,
                            1.0f,
                            0.7f
                        )
                        //man10FunctionalEquipmentの表示更新
                        val displayUpdated = plugin.server.dispatchCommand(
                            plugin.server.consoleSender,
                            "mfe display flag $machineId $slot true"
                        )
                        if (!displayUpdated) {
                            plugin.logger.ccWarning(
                                LogComponent.MINING_MACHINE,
                                "Failed to show GPU display after installation",
                                "machineId" to machineId,
                                "slot" to slot
                            )
                        }
                    } else {
                        val refundItem = GpuItems.create(gpuToSet, gpuKeys)
                        val overflow = player.inventory.addItem(refundItem)
                        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

                        plugin.logger.ccWarning(
                            LogComponent.MINING_MACHINE,
                            "Failed to install GPU in mining machine; item was refunded",
                            "machineId" to machineId,
                            "slot" to slot,
                            "player" to player.name,
                            "playerUuid" to player.uniqueId,
                            "gpuName" to gpuToSet.gpuName,
                            "droppedAmount" to overflow.values.sumOf { it.amount }
                        )
                        if (overflow.isNotEmpty()) {
                            plugin.logger.ccWarning(
                                LogComponent.MINING_MACHINE,
                                "Refunded GPU dropped because the player inventory was full",
                                "machineId" to machineId,
                                "slot" to slot,
                                "player" to player.name,
                                "playerUuid" to player.uniqueId,
                                "gpuName" to gpuToSet.gpuName,
                                "droppedAmount" to overflow.values.sumOf { it.amount }
                            )
                        }

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
