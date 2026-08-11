package dev.seraphina.rewired.fast

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private sealed class EngineCommand {
	data class SetInput(val pos: Long, val kind: NodeKind, val value: Int) : EngineCommand()
	data class SetNodeKind(val pos: Long, val kind: NodeKind) : EngineCommand()
	data class SetMainInput(val toPos: Long, val fromPos: Long) : EngineCommand()
	data class Connect(val fromPos: Long, val toPos: Long) : EngineCommand()
	data class Disconnect(val fromPos: Long, val toPos: Long) : EngineCommand()
	data class RemoveNode(val pos: Long) : EngineCommand()
}

/**
 * Runs the Bluestone BluestoneGraph on its own thread at a configurable
 * sub-ticks-per-tick rate, fully decoupled from the 20 TPS main loop.
 *
 * Main-thread contract:
 * - pushInput/connect/disconnect/removeNode are safe to call from any
 *   thread; they just enqueue a command, never touch the graph directly.
 * - pollOutput reads the last-published double buffer snapshot, never
 *   blocks, never sees a torn/mid-step graph.
 *
 * The graph itself (BluestoneGraph) is only ever touched from the logic
 * thread inside [runLoop], so it needs no internal synchronization.
 */
class BluestoneEngine(
	private var subTicksPerTick: Int = 20,
) {
	private val graph = BluestoneGraph()
	private val commandQueue = ConcurrentLinkedQueue<EngineCommand>()
	private val outputBuffer = AtomicReference<Map<Long, Int>>(emptyMap())
	private val running = AtomicBoolean(false)
	private val paused = AtomicBoolean(false)
	private val subTicks = AtomicInteger(subTicksPerTick.coerceAtLeast(1))
	private var logicThread: Thread? = null

	val isRunning: Boolean get() = running.get()

	/** Current sub-ticks-per-game-tick; safe to read from any thread. */
	val currentSubTicksPerTick: Int get() = subTicks.get()

	fun start() {
		if (!running.compareAndSet(false, true)) return
		graph.subTicksPerTick = subTicks.get()
		logicThread = thread(name = "bluestone-engine", isDaemon = true) { runLoop() }
	}

	fun stop() {
		running.set(false)
		logicThread?.join(1000)
		logicThread = null
	}

	/** Pauses stepping (topology edits still get applied) without tearing down the thread. */
	fun pause() = paused.set(true)

	fun resume() = paused.set(false)

	fun setSubTicksPerTick(rate: Int) {
		val clamped = rate.coerceAtLeast(1)
		subTicks.set(clamped)
		graph.subTicksPerTick = clamped
	}

	fun pushInput(pos: Long, kind: NodeKind, value: Int) {
		commandQueue.add(EngineCommand.SetInput(pos, kind, value))
	}

	/** Updates a node's kind without changing its external input (used when a block is re-placed as a different type). */
	fun setNodeKind(pos: Long, kind: NodeKind) {
		commandQueue.add(EngineCommand.SetNodeKind(pos, kind))
	}

	/** Marks [fromPos] as the "main" (back) input of the node at [toPos] (comparator semantics). */
	fun setMainInput(toPos: Long, fromPos: Long) {
		commandQueue.add(EngineCommand.SetMainInput(toPos, fromPos))
	}

	fun connect(fromPos: Long, toPos: Long) {
		commandQueue.add(EngineCommand.Connect(fromPos, toPos))
	}

	fun disconnect(fromPos: Long, toPos: Long) {
		commandQueue.add(EngineCommand.Disconnect(fromPos, toPos))
	}

	fun removeNode(pos: Long) {
		commandQueue.add(EngineCommand.RemoveNode(pos))
	}

	/** Never blocks; returns 0 for unknown positions (e.g. before the first step runs). */
	fun pollOutput(pos: Long): Int = outputBuffer.get()[pos] ?: 0

	private fun runLoop() {
		var nextTick = System.nanoTime()

		while (running.get()) {
			drainCommands()

			if (!paused.get()) {
				graph.step()
				publishOutputs()
			}

			// Re-read the rate each iteration so live gamerule changes take effect.
			val nanosPerSubTick = 1_000_000_000L / (subTicks.get() * 20)
			nextTick += nanosPerSubTick
			val sleepNanos = nextTick - System.nanoTime()
			if (sleepNanos > 0) {
				Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
			} else {
				// We fell behind; don't try to catch up by burning the CPU, just resync.
				nextTick = System.nanoTime()
			}
		}
	}

	private fun drainCommands() {
		while (true) {
			val command = commandQueue.poll() ?: break
			when (command) {
				is EngineCommand.SetInput -> {
					val node = graph.getOrCreate(command.pos, command.kind)
					node.externalInput = command.value
				}

				is EngineCommand.SetNodeKind -> graph.setKind(command.pos, command.kind)

				is EngineCommand.SetMainInput -> {
					val to = graph.nodes[command.toPos] ?: continue
					graph.setMainInput(to, command.fromPos)
				}

				is EngineCommand.Connect -> {
					val from = graph.nodes[command.fromPos] ?: continue
					val to = graph.nodes[command.toPos] ?: continue
					graph.connect(from, to)
				}

				is EngineCommand.Disconnect -> {
					val from = graph.nodes[command.fromPos] ?: continue
					val to = graph.nodes[command.toPos] ?: continue
					graph.disconnect(from, to)
				}

				is EngineCommand.RemoveNode -> graph.remove(command.pos)
			}
		}
	}

	private fun publishOutputs() {
		val snapshot = HashMap<Long, Int>(graph.nodes.size)
		for ((pos, node) in graph.nodes) {
			snapshot[pos] = node.renderedPower
		}
		outputBuffer.set(snapshot)
	}
}
