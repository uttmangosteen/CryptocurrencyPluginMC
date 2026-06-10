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
import org.bukkit.Sound
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

    suspend fun modifyMachine(
        machineId: String,
        requesterUuid: String? = null,
        requireOwner: Boolean = false,
        bypassPermission: Boolean = false,
        block: (MiningMachine) -> Boolean
    ): Boolean {
        if (machineId.isBlank()) return false
        if (!bypassPermission && requesterUuid.isNullOrBlank()) return false

        // メモリ上に稼働中のマシンがあればそれを、無ければDBから取得
        val machine = activeMachines[machineId] ?: plugin.repositories.miningMachineRepo.get(machineId) ?: return false

        val allowed = when {
            bypassPermission -> true
            requesterUuid == null -> false
            requireOwner -> machine.isOwner(requesterUuid)
            else -> machine.canAccess(requesterUuid)
        }

        if (!allowed || !block(machine)) return false

        machine.refreshStatus()
        if (!plugin.repositories.miningMachineRepo.save(machine)) return false
        machine.isDirty = false
        if (machine.status == MiningMachineStatus.MINING) {
            val wasActive = activeMachines.containsKey(machine.id)
            activeMachines[machine.id] = machine

            if (!wasActive) clearActiveMiningBlocks()
        } else {
            activeMachines.remove(machine.id)
        }

        notifyMachineUpdated(machine)
        return true
    }

    private fun clearActiveMiningBlocks() {
        activeMachines.values.forEach { machine -> machine.clearMiningBlock() }
    }

    suspend fun getMachine(machineId: String): MiningMachine? {
        if (machineId.isBlank()) return null
        return activeMachines[machineId] ?: plugin.repositories.miningMachineRepo.get(machineId)
    }

    //GUI開いてる人に対する更新を呼ぶ
    private fun notifyMachineUpdated(machine: MiningMachine) {
        plugin.runSync {
            Bukkit.getPluginManager().callEvent(
                MiningMachineUpdatedEvent(
                    machineId = machine.id,
                    actorUuid = "",
                    success = true
                )
            )
        }
    }

    fun stop() {
        task?.cancel()
        task = null
        runningJob?.cancel()
        runningJob = null

        val machinesToSave = ArrayList<MiningMachine>()
        for (machine in activeMachines.values) {
            if (machine.isDirty) machinesToSave.add(machine)
        }

        if (machinesToSave.isNotEmpty()) {
            plugin.logger.ccInfo(
                LogComponent.MINING_MACHINE_REPOSITORY,
                "saving ${machinesToSave.size} active mining machines to database on plugin stop"
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

        val runnableMachines = activeMachines.values.filter { machine ->
            machine.hasActiveGpu() && machine.rewardAccountPubKey != null
        }

        if (runnableMachines.isEmpty()) return

        val networkMiningPower = runnableMachines.fold(0L) { sum, machine ->
            Math.addExact(sum, machine.totalGpuPower().toLong())
        }
        if (networkMiningPower <= 0L) return

        miningTickCount++
        val shouldSaveMiningState = miningTickCount >= saveIntervalMiningTicks
        if (shouldSaveMiningState) miningTickCount = 0

        val latestBlock = plugin.repositories.blockRepo.getLatestBlock() ?: return

        for (machine in activeMachines.values) {
            val mined = processMachine(machine, networkMiningPower, latestBlock)
            if (mined) {
                clearActiveMiningBlocks()
                break
            }
        }

        if (shouldSaveMiningState) {
            val machinesToSave = ArrayList<MiningMachine>()
            for (machine in activeMachines.values) {
                if (machine.isDirty) machinesToSave.add(machine)
            }

            if (machinesToSave.isNotEmpty()) {
                val saved = plugin.repositories.miningMachineRepo.saveAll(machinesToSave)
                if (saved) machinesToSave.forEach { it.isDirty = false }
            }
        }
    }

    private suspend fun processMachine(
        machine: MiningMachine,
        networkMiningPower: Long,
        latestBlock: Block
    ): Boolean {
        var guiNeedsUpdate = false
        try {
            machine.refreshStatus()
            if (machine.status != MiningMachineStatus.MINING) {
                plugin.repositories.miningMachineRepo.save(machine)
                activeMachines.remove(machine.id)
                guiNeedsUpdate = true
                return false
            }

            val activeGpuCount = machine.activeGpuCount()
            if (activeGpuCount == 0) return false

            //燃料がなかったら掘れない(idle移行してメモリから消す)
            if (machine.fuelAmount < activeGpuCount) {
                machine.halt()
                plugin.repositories.miningMachineRepo.save(machine)
                activeMachines.remove(machine.id)
                return false
            }

            val miningBlock = machine.miningBlock
                ?.takeIf { it.height == latestBlock.height + 1 && it.previousHash == latestBlock.hash }
                ?: blockFactory.createMiningBlock(latestBlock, networkMiningPower, machine)

            if (miningBlock == null) return false
            machine.replaceMiningBlock(miningBlock)

            // 燃料消費とGPU耐久値減少
            machine.consumeFuel(activeGpuCount)
            machine.consumeGpuLife()

            val mined = tryMine(machine, miningBlock)
            notifyMachineUpdated(machine)

            if (mined) {
                val accepted = plugin.repositories.blockchainManager.acceptNewBlock(miningBlock)
                if (accepted) {
                    notifyMined(machine, miningBlock)
                    machine.replaceMiningBlock(null)
                    machine.refreshStatus()
                    notifyMachineUpdated(machine)
                    return true
                } else {
                    machine.replaceMiningBlock(null)
                }
                machine.refreshStatus()
                notifyMachineUpdated(machine)
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
        } finally {
            if (guiNeedsUpdate) {
                notifyMachineUpdated(machine)
            }
        }
    }

    private fun tryMine(machine: MiningMachine, block: Block): Boolean {
        val power = machine.totalGpuPower()
        if (power <= 0) return false
        return block.tryMine(power)
    }

    private fun notifyMined(machine: MiningMachine, block: Block) {
        val prefix = plugin.pluginConfig.prefix
        plugin.runSync {
            val ownerUuid = machine.ownerUuid ?: return@runSync
            val owner = Bukkit.getOfflinePlayer(java.util.UUID.fromString(ownerUuid))
            val minerName = if (machine.shareNameOnMined) "${owner.name}" else "§k00000000"

            val message =
                "$prefix§e§l§ka §f§l$minerName §a§lが§f§l ${block.height} §a§l番目のブロックを証明しました！§e§l§ka"
            for (player in Bukkit.getOnlinePlayers()) {
                player.sendMessage(message)
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 2f)
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

                    val relatedTxs = block.transactions.filter { tx ->
                        tx.inputs.any { it.publicKey in userPubKeys } || tx.outputs.any { it.receiverPubKey in userPubKeys }
                    }
                    if (relatedTxs.isEmpty()) continue

                    player.sendMessage("$prefix§f§l========== §e§lTransaction Confirmed §f§l==========")
                    relatedTxs.forEach { tx ->
                        player.sendMessage("$prefix§8§l-------------------------------------------------------")
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
                        txMessageFactory.buildMessages(
                            prefix = prefix,
                            tx = tx,
                            targetPubKeys = userPubKeys,
                            inputUtxos = inputUtxos,
                            fee = fee
                        ).forEach { message ->
                            player.sendMessage(message)
                        }
                    }
                    player.sendMessage("$prefix§f§l==========================================")
                }
            }
        }
    }
}