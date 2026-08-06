package dev.seraphina.rewired.fast

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private sealed class EngineCommand {
	data class SetInput(val pos: Long, val kind: NodeKind, val value: Int) : EngineCommand()
	data class Connect(val fromPos: Long, val toPos: Long) : EngineCommand()
	data class Disconnect(val fromPos: Long, val toPos: Long) : EngineCommand()
	data class RemoveNode(val pos: Long) : EngineCommand()
}

/**
 * Runs the Bluestone RedstoneGraph on its own thread at a configurable
 * sub-ticks-per-tick rate, fully decoupled from the 20 TPS main loop.
 *
 * Main-thread contract:
 * - pushInput/connect/disconnect/removeNode are safe to call from any
 *   thread; they just enqueue a command, never touch the graph directly.
 * - pollOutput reads the last-published double buffer snapshot, never
 *   blocks, never sees a torn/mid-step graph.
 *
 * The graph itself (RedstoneGraph) is only ever touched from the logic
 * thread inside [runLoop], so it needs no internal synchronization.
 */
class FastRedstoneEngine(
	private var subTicksPerTick: Int = 20,
) {
	private val graph = RedstoneGraph()
	private val commandQueue = ConcurrentLinkedQueue<EngineCommand>()
	private val outputBuffer = AtomicReference<Map<Long, Int>>(emptyMap())
	private val running = AtomicBoolean(false)
	private val paused = AtomicBoolean(false)
	private var logicThread: Thread? = null

	val isRunning: Boolean get() = running.get()

	fun start() {
		if (!running.compareAndSet(false, true)) return
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
		subTicksPerTick = rate.coerceAtLeast(1)
	}

	fun pushInput(pos: Long, kind: NodeKind, value: Int) {
		commandQueue.add(EngineCommand.SetInput(pos, kind, value))
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
		val nanosPerSubTick = 1_000_000_000L / (subTicksPerTick.coerceAtLeast(1) * 20)
		var nextTick = System.nanoTime()

		while (running.get()) {
			drainCommands()

			if (!paused.get()) {
				graph.step()
				publishOutputs()
			}

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
