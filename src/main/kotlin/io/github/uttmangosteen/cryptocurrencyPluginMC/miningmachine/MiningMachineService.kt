package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.BlockFactory
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import kotlinx.coroutines.Job
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

class MiningMachineService(
    private val plugin: Main
) {
    private var task: BukkitTask? = null
    private var runningJob: Job? = null
    private var miningTickCount: Int = 0

    private val activeBlocksCache = mutableMapOf<String, Block?>()
    private val pendingFuelCache = mutableMapOf<String, Int>()
    private val pendingGpuCache = mutableMapOf<String, Int>()

    private val saveIntervalMiningTicks = plugin.pluginConfig.miningMachineSaveIntervalMiningTicks
        .coerceAtLeast(1)

    private val blockFactory = BlockFactory(
        plugin = plugin,
        mempoolRepository = plugin.repositories.mempoolRepo
    )

    fun start() {
        if (task != null) return

        task = Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                if (!plugin.pluginConfig.enable) return@Runnable
                if (runningJob?.isActive == true) return@Runnable

                runningJob = plugin.launchAsync {
                    tick()
                }
            },
            plugin.pluginConfig.miningMachineMiningDelayTicks.toLong(),
            plugin.pluginConfig.miningMachineMiningDelayTicks.toLong()
        )
    }

    fun stop() {
        task?.cancel()
        task = null
        runningJob?.cancel()
        runningJob = null
    }

    private suspend fun tick() {
        val machines = plugin.repositories.miningMachineRepo.getRunnableMachines()

        if (machines.isEmpty()) {
            activeBlocksCache.clear()
            pendingFuelCache.clear()
            pendingGpuCache.clear()
            return
        }

        val networkMiningPower = plugin.repositories.miningMachineRepo.calculateNetworkMiningPower()
        if (networkMiningPower <= 0L) return

        miningTickCount++
        val shouldSaveMiningState = miningTickCount >= saveIntervalMiningTicks
        if (shouldSaveMiningState) miningTickCount = 0

        val currentIds = machines.map { it.id }.toSet()
        activeBlocksCache.keys.retainAll(currentIds)
        pendingFuelCache.keys.retainAll(currentIds)
        pendingGpuCache.keys.retainAll(currentIds)

        for (machine in machines) {
            val id = machine.id
            if (activeBlocksCache.containsKey(id)) {
                val cachedBlock = activeBlocksCache[id]
                // 新しいブロックに切り替わっていない場合のみ引き継ぐ
                if (cachedBlock == null || cachedBlock.height == machine.miningBlock?.height) {
                    machine.replaceMiningBlock(cachedBlock)
                }
            }
            val pendingFuel = pendingFuelCache.getOrDefault(id, 0)
            val pendingGpu = pendingGpuCache.getOrDefault(id, 0)

            val wasSaved = processMachine(
                machine = machine,
                networkMiningPower = networkMiningPower,
                shouldSaveMiningState = shouldSaveMiningState,
                pendingFuel = pendingFuel,
                pendingGpu = pendingGpu
            )

            if (wasSaved) {
                pendingFuelCache[id] = 0
                pendingGpuCache[id] = 0
                activeBlocksCache[id] = machine.miningBlock
            } else {
                pendingFuelCache[id] = pendingFuel + 1
                pendingGpuCache[id] = pendingGpu + 1
                activeBlocksCache[id] = machine.miningBlock
            }
        }
    }

    private suspend fun processMachine(
        machine: MiningMachine,
        networkMiningPower: Long,
        shouldSaveMiningState: Boolean,
        pendingFuel: Int,
        pendingGpu: Int
    ): Boolean {
        try {
            machine.refreshStatus()
            if (machine.status != MiningMachineStatus.MINING) {
                plugin.repositories.miningMachineRepo.save(machine)
                return true
            }

            val latestBlock = plugin.repositories.blockRepo.getLatestBlock()
            if (latestBlock == null) {
                machine.halt()
                plugin.repositories.miningMachineRepo.save(machine)
                plugin.logger.ccWarning(
                    LogComponent.MINING_MACHINE_REPOSITORY,
                    "mining halted because latest block was not found",
                    IllegalStateException("latest block not found"),
                    "machineId" to machine.id
                )
                return true
            }

            val miningBlock = machine.miningBlock
                ?.takeIf { it.height == latestBlock.height + 1 && it.previousHash == latestBlock.hash }
                ?: createMiningBlock(latestBlock, networkMiningPower, machine)

            if (miningBlock == null) {
                machine.refreshStatus()
                plugin.repositories.miningMachineRepo.save(machine)
                return true
            }

            machine.replaceMiningBlock(miningBlock)

            val mined = tryMine(machine, miningBlock)

            machine.consumeGpuLife()
            machine.consumeFuel(pendingFuel + 1)

            repeat(pendingGpu + 1) {
                machine.consumeGpuLife()
            }

            if (mined) {
                val accepted = plugin.repositories.blockchainManager.acceptNewBlock(miningBlock)

                if (accepted) {
                    machine.replaceMiningBlock(null)

                    plugin.logger.ccInfo(
                        LogComponent.MINING_MACHINE_REPOSITORY,
                        "mined block accepted",
                        "machineId" to machine.id,
                        "height" to miningBlock.height,
                        "hash" to miningBlock.hash
                    )

                    notifyMined(machine, miningBlock)
                } else {
                    machine.replaceMiningBlock(null)

                    plugin.logger.ccInfo(
                        LogComponent.MINING_MACHINE_REPOSITORY,
                        "mined block rejected",
                        "machineId" to machine.id,
                        "height" to miningBlock.height
                    )
                }

                machine.refreshStatus()
                plugin.repositories.miningMachineRepo.save(machine)
                return true
            }

            machine.refreshStatus()
            if (shouldSaveMiningState) {
                plugin.repositories.miningMachineRepo.save(machine)
                return true
            }
            return false
        } catch (e: Exception) {
            plugin.logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to process mining machine",
                e,
                "machineId" to machine.id
            )
            return false
        }
    }

    private suspend fun createMiningBlock(
        latestBlock: Block,
        networkMiningPower: Long,
        machine: MiningMachine
    ): Block? {
        return blockFactory.createMiningBlock(
            latestBlock = latestBlock,
            networkMiningPower = networkMiningPower,
            machine = machine
        )
    }

    private fun tryMine(machine: MiningMachine, block: Block): Boolean {
        val power = machine.totalGpuPower()
        if (power <= 0) return false

        repeat(power) {
            val hashBytes = block.calculateHash(block.nonce)
            if (Block.isMined(hashBytes, block.difficulty)) {
                block.hash = hashBytes.toHexString()
                return true
            }
            block.nonce++
        }

        return false
    }

    private fun notifyMined(machine: MiningMachine, block: Block) {
        plugin.runSync {
            val ownerUuid = machine.ownerUuid ?: return@runSync
            val owner = Bukkit.getPlayer(java.util.UUID.fromString(ownerUuid)) ?: return@runSync

            val minerName = if (machine.shareNameOnMined) {
                "§f${owner.name}"
            } else {
                "§7§k00000000"
            }
            val message =
                "${plugin.pluginConfig.prefix}§a$minerName §aがブロックを採掘しました §7height=§f${block.height}"
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage(message)
            }
        }
    }
}