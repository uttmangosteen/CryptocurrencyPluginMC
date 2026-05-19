package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.BlockFactory
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.policy.TextFormat.formatCoin
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

        val machinesToSave = mutableListOf<MiningMachine>()

        for (machine in machines) {
            val id = machine.id
            if (activeBlocksCache.containsKey(id)) {
                val cachedBlock = activeBlocksCache[id]
                if (cachedBlock == null || cachedBlock.height == machine.miningBlock?.height) {
                    machine.replaceMiningBlock(cachedBlock)
                }
            }
            val pendingFuel = pendingFuelCache.getOrDefault(id, 0)

            val needsSave = processMachine(
                machine = machine,
                networkMiningPower = networkMiningPower,
                shouldSaveMiningState = shouldSaveMiningState,
                pendingFuel = pendingFuel,
            )

            if (needsSave) {
                machinesToSave.add(machine)
                pendingFuelCache[id] = 0
                activeBlocksCache[id] = machine.miningBlock
            } else {
                pendingFuelCache[id] = pendingFuel + 1
                activeBlocksCache[id] = machine.miningBlock
            }
        }
        if (machinesToSave.isNotEmpty()) {
            plugin.repositories.miningMachineRepo.saveAll(machinesToSave)
        }
    }

    private suspend fun processMachine(
        machine: MiningMachine,
        networkMiningPower: Long,
        shouldSaveMiningState: Boolean,
        pendingFuel: Int,
    ): Boolean {
        try {
            machine.refreshStatus()
            if (machine.status != MiningMachineStatus.MINING) return true

            val latestBlock = plugin.repositories.blockRepo.getLatestBlock()
            if (latestBlock == null) {
                machine.halt()
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
                return true
            }

            machine.replaceMiningBlock(miningBlock)
            val mined = tryMine(machine, miningBlock)

            val consumeAmount = pendingFuel + 1
            machine.consumeFuel(consumeAmount)
            repeat(consumeAmount) {
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
                return true
            }

            machine.refreshStatus()
            return shouldSaveMiningState
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
        val prefix = plugin.pluginConfig.prefix
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
        plugin.launchAsync {
            val onlinePlayers = Bukkit.getOnlinePlayers().toList()
            if (onlinePlayers.isEmpty()) return@launchAsync

            val onlineUuids = onlinePlayers.map { it.uniqueId.toString() }
            val wallets = plugin.repositories.walletRepo.getWallets(onlineUuids)
            val walletMap = wallets.associateBy { it.ownerUUID }

            for (player in onlinePlayers) {
                val wallet = walletMap[player.uniqueId.toString()] ?: continue

                val userPubKeys = wallet.accounts.map { it.publicKey }.toSet()
                if (userPubKeys.isEmpty()) continue

                val displayLines = mutableListOf<String>()
                block.transactions.forEach { tx ->
                    val senderPubKey = tx.inputs.firstOrNull()?.publicKey
                    val isSender = senderPubKey in userPubKeys
                    var isRelated = false
                    val txLines = mutableListOf<String>()
                    tx.outputs.forEach { output ->
                        val receiver = output.receiverPubKey
                        val amount = output.amount
                        val isReceiver = receiver in userPubKeys

                        when {
                            senderPubKey == null && isReceiver -> {
                                txLines.add("§a+${formatCoin(amount)} §7(coinbase)")
                                isRelated = true
                            }
                            isSender && !isReceiver -> {
                                txLines.add("§c-${formatCoin(amount)} §7-> §8$receiver")
                                isRelated = true
                            }
                            isReceiver && !isSender -> {
                                txLines.add("§a+${formatCoin(amount)} §7<- §8$senderPubKey")
                                isRelated = true
                            }
                        }
                    }
                    if (isRelated) {
                        val memo = tx.memo.takeIf { it.isNotBlank() } ?: "no memo"
                        displayLines.add("§7memo: §f$memo")
                        displayLines.addAll(txLines)
                    }
                }
                if (displayLines.isNotEmpty()) {
                    plugin.runSync {
                        player.sendMessage("$prefix§f§l========== §8§lTransaction Confirmed! §f§l==========")
                        displayLines.forEach { line ->
                            player.sendMessage("$prefix$line")
                        }
                        player.sendMessage("$prefix§f§l============================================")
                    }
                }
            }
        }
    }
}