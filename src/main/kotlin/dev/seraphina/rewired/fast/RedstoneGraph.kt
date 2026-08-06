package dev.seraphina.rewired.fast

/**
 * Owns all Bluestone nodes and their wiring. Every method here is only
 * ever called from the engine's logic thread (see FastRedstoneEngine) —
 * no synchronization inside this class itself; thread-safety is the
 * engine's job via its input queue / pause-resume around topology edits.
 */
class RedstoneGraph {
	val nodes: MutableMap<Long, RedstoneNode> = HashMap()

	fun getOrCreate(pos: Long, kind: NodeKind): RedstoneNode =
		nodes.getOrPut(pos) { RedstoneNode(pos, kind) }

	fun remove(pos: Long) {
		val node = nodes.remove(pos) ?: return
		for (input in node.inputs) input.outputs.remove(node)
		for (output in node.outputs) output.inputs.remove(node)
	}

	fun connect(from: RedstoneNode, to: RedstoneNode) {
		if (to !in from.outputs) from.outputs.add(to)
		if (from !in to.inputs) to.inputs.add(from)
	}

	fun disconnect(from: RedstoneNode, to: RedstoneNode) {
		from.outputs.remove(to)
		to.inputs.remove(from)
	}

	/**
	 * Advances every node by one Bluestone sub-tick. Wire power is
	 * recomputed as max(inputs) minus 1 per hop (matches vanilla dust
	 * decay); repeaters/comparators/torches use their own delay/threshold
	 * rules; bridge nodes just hold whatever externalInput was pushed into
	 * them by the main thread.
	 */
	fun step() {
		for (node in nodes.values) {
			node.power = when (val kind = node.kind) {
				is NodeKind.Wire -> {
					val maxInput = node.inputs.maxOfOrNull { it.power } ?: 0
					(maxInput - 1).coerceAtLeast(0)
				}

				is NodeKind.Repeater -> stepRepeater(node, kind)
				is NodeKind.Comparator -> stepComparator(node, kind)
				is NodeKind.Torch -> stepTorch(node)
				is NodeKind.Bridge -> node.externalInput
			}
		}

		for (node in nodes.values) {
			node.renderedPower = node.power
		}
	}

	private fun stepRepeater(node: RedstoneNode, kind: NodeKind.Repeater): Int {
		if (kind.locked) return node.power

		val inputPowered = (node.inputs.maxOfOrNull { it.power } ?: 0) > 0
		val currentlyOn = node.power > 0

		return if (inputPowered != currentlyOn) {
			node.delayCounter++
			if (node.delayCounter >= kind.delayTicks) {
				node.delayCounter = 0
				if (inputPowered) 15 else 0
			} else {
				node.power
			}
		} else {
			node.delayCounter = 0
			node.power
		}
	}

	private fun stepComparator(node: RedstoneNode, kind: NodeKind.Comparator): Int {
		// Simplified vanilla comparator model: main input is the strongest
		// input, side inputs subtract in subtract mode. Full parity with
		// vanilla's side-input semantics (which side is which) is a Block
		// concern once BluestoneComparatorBlock exists; the engine only
		// needs "main vs side" here.
		if (node.inputs.isEmpty()) return 0
		val sorted = node.inputs.sortedByDescending { it.power }
		val main = sorted.getOrNull(0)?.power ?: 0
		val side = sorted.getOrNull(1)?.power ?: 0

		return if (kind.subtractMode) (main - side).coerceAtLeast(0) else main
	}

	private fun stepTorch(node: RedstoneNode): Int {
		val inputPowered = (node.inputs.maxOfOrNull { it.power } ?: 0) > 0
		// Torches invert: powered input = torch off, unpowered = torch on.
		return if (inputPowered) 0 else 15
	}
}
