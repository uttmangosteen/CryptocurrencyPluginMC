package io.github.uttmangosteen.cryptocurrencyPluginMC.miningmachine

import io.github.uttmangosteen.cryptocurrencyPluginMC.LogComponent
import io.github.uttmangosteen.cryptocurrencyPluginMC.Main
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.Block
import io.github.uttmangosteen.cryptocurrencyPluginMC.blockchain.OutPoint
import io.github.uttmangosteen.cryptocurrencyPluginMC.command.user.TxMessageFactory
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccInfo
import io.github.uttmangosteen.cryptocurrencyPluginMC.ccWarning
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

class MiningMachineService(
    private val plugin: Main
) {
    private var task: BukkitTask? = null
    private var runningJob: Job? = null
    private var miningTickCount: Int = 0

    private val activeMachines = ConcurrentHashMap<String, MiningMachine>()

    private val saveIntervalMiningTicks = plugin.pluginConfig.miningMachineSaveIntervalMiningTicks
        .coerceAtLeast(1)

    private val blockFactory = BlockFactory(
        plugin = plugin,
        mempoolRepository = plugin.repositories.mempoolRepo
    )

    private val txMessageFactory = TxMessageFactory()

    fun start() {
        if (task != null) return

        plugin.launchAsync {
            val machines = plugin.repositories.miningMachineRepo.getRunnableMachines()
            machines.forEach { activeMachines[it.id] = it }
        }

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

    suspend fun modifyMachineExternal(
        machineId: String,
        requesterUuid: String,
        requireOwner: Boolean = false,
        block: (MiningMachine) -> Boolean
    ): Boolean {
        if (machineId.isBlank() || requesterUuid.isBlank()) return false

        // メモリ上に稼働中のマシンがあればそれを、無ければDBから取得
        val machine = activeMachines[machineId] ?: plugin.repositories.miningMachineRepo.get(machineId) ?: return false

        // 権限チェック
        val allowed = if (requireOwner) machine.isOwner(requesterUuid) else machine.canAccess(requesterUuid)
        if (!allowed) return false

        // マシンの状態を変更（isDirty = true）
        val changed = block(machine)
        if (!changed) return false

        // 即時単独saveがいるか?
        val saved = plugin.repositories.miningMachineRepo.save(machine)
        if (saved) {
            machine.isDirty = false
            machine.refreshStatus()
            if (machine.status == MiningMachineStatus.MINING) {
                activeMachines[machine.id] = machine
            } else {
                activeMachines.remove(machine.id)
            }
        }
        return saved
    }

    suspend fun getMachine(machineId: String): MiningMachine? {
        if (machineId.isBlank()) return null
        return activeMachines[machineId] ?: plugin.repositories.miningMachineRepo.get(machineId)
    }

    fun stop() {
        task?.cancel()
        task = null
        runningJob?.cancel()
        runningJob = null

        val machinesToSave = activeMachines.values.filter { it.isDirty }
        if (machinesToSave.isNotEmpty()) {
            plugin.logger.ccInfo(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "Saving ${machinesToSave.size} active mining machines to database on plugin stop..."
            )
            // メインスレッド
            runBlocking {
                val saved = plugin.repositories.miningMachineRepo.saveAll(machinesToSave)
                if (saved) machinesToSave.forEach { it.isDirty = false }
            }
            activeMachines.clear()
        }
    }

    private suspend fun tick() {
        if (activeMachines.isEmpty()) return

        val networkMiningPower = activeMachines.values.fold(0L) { sum, machine ->
            Math.addExact(sum, machine.totalGpuPower().toLong())
        }
        if (networkMiningPower <= 0L) return

        miningTickCount++
        val shouldSaveMiningState = miningTickCount >= saveIntervalMiningTicks
        if (shouldSaveMiningState) miningTickCount = 0

        for (machine in activeMachines.values) {
            processMachine(machine, networkMiningPower)
        }

        if (shouldSaveMiningState) {
            val machinesToSave = activeMachines.values.filter { it.isDirty }
            if (machinesToSave.isNotEmpty()) {
                val saved = plugin.repositories.miningMachineRepo.saveAll(machinesToSave)
                if (saved) machinesToSave.forEach { it.isDirty = false }
            }
        }
    }

    private suspend fun processMachine(
        machine: MiningMachine,
        networkMiningPower: Long
    ) {
        try {
            machine.refreshStatus()
            if (machine.status != MiningMachineStatus.MINING) {
                plugin.repositories.miningMachineRepo.save(machine)
                activeMachines.remove(machine.id)
                return
            }

            val activeGpuCount = machine.gpuSlots.count { it != null && it.isActive() }
            if (activeGpuCount == 0) return

            //燃料がなかったら掘れない(idle移行してメモリから消す)
            if (machine.fuelAmount < activeGpuCount) {
                machine.halt()
                plugin.repositories.miningMachineRepo.save(machine)
                activeMachines.remove(machine.id)
                return
            }

            val latestBlock = plugin.repositories.blockRepo.getLatestBlock() ?: return

            val miningBlock = machine.miningBlock
                ?.takeIf { it.height == latestBlock.height + 1 && it.previousHash == latestBlock.hash }
                ?: blockFactory.createMiningBlock(latestBlock, networkMiningPower, machine)

            if (miningBlock == null) return
            machine.replaceMiningBlock(miningBlock)

            // 燃料消費とGPU耐久値減少
            machine.consumeFuel(activeGpuCount)
            machine.consumeGpuLife()

            val mined = tryMine(machine, miningBlock)

            if (mined) {
                val accepted = plugin.repositories.blockchainManager.acceptNewBlock(miningBlock)
                if (accepted) {
                    notifyMined(machine, miningBlock)
                    val newLatestBlock = plugin.repositories.blockRepo.getLatestBlock()
                    if (newLatestBlock != null) {
                        val nextBlock = blockFactory.createMiningBlock(newLatestBlock, networkMiningPower, machine)
                        machine.replaceMiningBlock(nextBlock)
                    } else {
                        machine.replaceMiningBlock(null)
                    }
                } else {
                    machine.replaceMiningBlock(null)
                }
                machine.refreshStatus()
            }

        } catch (e: Exception) {
            plugin.logger.ccWarning(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "failed to process mining machine",
                e,
                "machineId" to machine.id
            )
        }
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
            val minerName = if (machine.shareNameOnMined) "§f${owner.name}" else "§7§k00000000"

            val message =
                "${plugin.pluginConfig.prefix}§a$minerName §aがブロックを採掘しました §7height=§f${block.height}"
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage(message)
                player.playSound(player.location, "note.pling", 1f, 0.5f)
            }
        }
        plugin.launchAsync {
            val onlinePlayers = Bukkit.getOnlinePlayers().toList()
            if (onlinePlayers.isEmpty()) return@launchAsync

            val onlineUuids = onlinePlayers.map { it.uniqueId.toString() }
            val wallets = plugin.repositories.walletRepo.getWallets(onlineUuids)
            val walletMap = wallets.associateBy { it.ownerUUID }

            val outPoints = block.transactions
                .flatMap { tx ->
                    tx.inputs.map { input ->
                        OutPoint(
                            txHash = input.prevTxHash,
                            outputIndex = input.outputIndex
                        )
                    }
                }
                .distinct()

            val utxoMap = plugin.repositories.utxoRepo.findUtxos(outPoints = outPoints)

            for (player in onlinePlayers) {
                val wallet = walletMap[player.uniqueId.toString()] ?: continue

                val userPubKeys = wallet.accounts.map { it.publicKey }.toSet()
                if (userPubKeys.isEmpty()) continue

                block.transactions.forEach { tx ->
                    val related = tx.inputs.any { it.publicKey in userPubKeys } ||
                            tx.outputs.any { it.receiverPubKey in userPubKeys }

                    if (!related) return@forEach

                    val inputUtxos = tx.inputs.mapNotNull { input ->
                        utxoMap[
                            OutPoint(
                                txHash = input.prevTxHash,
                                outputIndex = input.outputIndex
                            )
                        ]
                    }

                    val inputAmount = inputUtxos.sumOf { it.amount }
                    val outputAmount = tx.outputs.sumOf { it.amount }
                    val fee = if (tx.isCoinbase) 0L else inputAmount - outputAmount

                    player.sendMessage("$prefix§f§l========== Transaction Confirmed ==========")

                    txMessageFactory.buildMessages(
                        prefix = prefix,
                        tx = tx,
                        targetPubKeys = userPubKeys,
                        inputUtxos = inputUtxos,
                        fee = fee
                    ).forEach { message ->
                        player.sendMessage(message)
                    }
                    player.sendMessage("$prefix§f§l===========================================")
                }
            }
        }
    }
}