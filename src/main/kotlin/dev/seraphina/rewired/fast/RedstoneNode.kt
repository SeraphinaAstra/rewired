package dev.seraphina.rewired.fast

/**
 * What kind of Bluestone component a node represents. Mirrors vanilla's
 * wire / repeater / comparator / torch behavior, minus block-state/shape
 * concerns (those stay in the Block classes; this is pure logic).
 */
sealed class NodeKind {
	data object Wire : NodeKind()

	/**
	 * [delayTicks] is the vanilla DELAY property (1–4). Vanilla's actual
	 * delay is `delayTicks * 2` game ticks (see RepeaterBlock.getDelay).
	 * The graph converts to sub-ticks internally using the engine's current
	 * [BluestoneEngine.subTicksPerTick] so delay length stays constant
	 * regardless of the configured sub-tick rate. [locked] repeaters hold
	 * their output and ignore input changes (vanilla side-lock behavior).
	 */
	data class Repeater(val delayTicks: Int, val locked: Boolean = false) : NodeKind()

	data class Comparator(val subtractMode: Boolean = false) : NodeKind()

	data object Torch : NodeKind()

	/**
	 * A bridge node doesn't compute anything itself — it just holds whatever
	 * this graph was told from the outside. Its step output is
	 * `max(externalInput, max(inputs))`, so both an external (vanilla) push
	 * and Bluestone wire input can drive it, and the block reads the same
	 * value back out via pollOutput().
	 */
	data object Bridge : NodeKind()
}

/**
 * A single position in the Bluestone graph. Positions are the identity key
 * (see [BluestoneGraph.nodes]); everything else here is mutable working
 * state owned exclusively by the engine thread.
 *
 * [mainInput] is only used by comparator nodes — it's the edge from the
 * block *behind* the comparator, distinguished from regular side inputs so
 * compare/subtract semantics match vanilla (main strength vs strongest side).
 */
class RedstoneNode(
	val pos: Long,
	var kind: NodeKind,
	var power: Int = 0,
	var renderedPower: Int = 0,
	val inputs: MutableList<RedstoneNode> = mutableListOf(),
	val outputs: MutableList<RedstoneNode> = mutableListOf(),
	var mainInput: RedstoneNode? = null,
	var delayCounter: Int = 0,
	var externalInput: Int = 0,
) {
	/**
	 * Power from the previous sub-tick, used by [BluestoneGraph.step]'s
	 * two-phase evaluation so every node in a step sees a consistent,
	 * deterministic view of the graph (no iteration-order dependence).
	 */
	var prevPower: Int = 0

	// --- Delay state (repeater / comparator / torch) ---
	/**
	 * The power this node should output once its delay elapses, or null
	 * if no state change is pending. Used by delayed components to hold
	 * their output while the delay timer runs.
	 */
	var pendingPower: Int? = null

	/** Sub-ticks remaining before [pendingPower] is applied. */
	var delayRemaining: Int = 0

	// --- Torch burnout state (vanilla RedstoneTorchBlock's anti-flicker) ---
	/** How many power-state toggles have happened recently. */
	var torchToggles: Int = 0

	/** Sub-tick timestamp until which this torch stays forced-off after burning out. */
	var torchBurnoutUntil: Long = 0

	/** True while the torch has recently been toggling fast enough to risk burnout. */
	var torchRecentlyToggled: Boolean = false

	/** Sub-tick timestamp of the last toggle, for the 60-game-tick window check. */
	var lastToggleTime: Long = 0

	fun clearConnections() {
		for (input in inputs) input.outputs.remove(this)
		for (output in outputs) output.inputs.remove(this)
		inputs.clear()
		outputs.clear()
		mainInput = null
	}
}