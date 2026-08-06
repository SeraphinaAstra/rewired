package dev.seraphina.rewired.fast

/**
 * What kind of Bluestone component a node represents. Mirrors vanilla's
 * wire / repeater / comparator / torch behavior, minus block-state/shape
 * concerns (those stay in the Block classes; this is pure logic).
 */
sealed class NodeKind {
	data object Wire : NodeKind()
	data class Repeater(val delayTicks: Int, val locked: Boolean) : NodeKind()
	data class Comparator(val subtractMode: Boolean) : NodeKind()
	data object Torch : NodeKind()

	/**
	 * A bridge node doesn't compute anything itself — it's just a place
	 * where an external (vanilla redstone) signal is injected as input,
	 * or where this graph's computed power is read back out.
	 */
	data object Bridge : NodeKind()
}

/**
 * A single position in the Bluestone graph. Positions are the identity key
 * (see [RedstoneGraph.nodes]); everything else here is mutable working
 * state owned exclusively by the engine thread.
 */
class RedstoneNode(
	val pos: Long,
	var kind: NodeKind,
	var power: Int = 0,
	var renderedPower: Int = 0,
	val inputs: MutableList<RedstoneNode> = mutableListOf(),
	val outputs: MutableList<RedstoneNode> = mutableListOf(),
	var delayCounter: Int = 0,
	var externalInput: Int = 0,
)
