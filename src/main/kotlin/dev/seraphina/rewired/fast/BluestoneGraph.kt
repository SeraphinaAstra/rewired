package dev.seraphina.rewired.fast

import java.util.stream.IntStream

/**
 * Owns all Bluestone nodes and their wiring. Every method here is only
 * ever called from the engine's logic thread (see BluestoneEngine) —
 * no synchronization inside this class itself; thread-safety is the
 * engine's job via its input queue / pause-resume around topology edits.
 *
 * Stepping is parallelized: the two-phase design means phase 1 only reads
 * each node's [RedstoneNode.prevPower] (read-only across the whole graph)
 * and writes only that node's own [RedstoneNode.power], so the compute
 * phase is embarrassingly parallel. Phase 2 likewise only touches each
 * node's own [RedstoneNode.renderedPower]. We snapshot the node values
 * into an array and run a parallel-for over it, which scales with the
 * common ForkJoinPool's parallelism (typically = CPU cores).
 */
class BluestoneGraph {
	val nodes: MutableMap<Long, RedstoneNode> = HashMap()

	/** Engine's current sub-ticks-per-game-tick; wire delay, repeater delay and torch burnout duration are scaled by this. */
	var subTicksPerTick: Int = 1

	/** Monotonic sub-tick counter; used as a clock for torch burnout timing. */
	var tickCounter: Long = 0

	/**
	 * Minimum graph size before we bother with the parallel path. For tiny
	 * graphs the fork/join overhead outweighs the benefit, so we fall back
	 * to a plain sequential loop.
	 */
	private val parallelThreshold = 64

	fun getOrCreate(pos: Long, kind: NodeKind): RedstoneNode =
		nodes.getOrPut(pos) { RedstoneNode(pos, kind) }

	/** Updates a node's kind, preserving power; returns the node (creating it if absent). */
	fun setKind(pos: Long, kind: NodeKind): RedstoneNode {
		val node = getOrCreate(pos, kind)
		if (node.kind != kind) {
			// Comparator -> comparator (mode toggle) keeps its main input; other
			// transitions drop it because the edge is meaningless for the new kind.
			if (node.kind !is NodeKind.Comparator || kind !is NodeKind.Comparator) {
				node.mainInput = null
			}
			node.kind = kind
			node.delayCounter = 0
			node.pendingPower = null
			node.delayRemaining = 0
		}
		return node
	}

	/**
	 * Sets the edge this node receives from [from] as its "main" input
	 * (comparator back-input semantics). The edge is still a regular input
	 * for power purposes; [mainInput] just marks which one the comparator
	 * treats as its primary signal.
	 */
	fun setMainInput(to: RedstoneNode, fromPos: Long) {
		val from = nodes[fromPos] ?: return
		connect(from, to)
		to.mainInput = from
	}

	fun remove(pos: Long) {
		val node = nodes.remove(pos) ?: return
		node.clearConnections()
	}

	fun connect(from: RedstoneNode, to: RedstoneNode) {
		if (to !in from.outputs) from.outputs.add(to)
		if (from !in to.inputs) to.inputs.add(from)
	}

	fun disconnect(from: RedstoneNode, to: RedstoneNode) {
		from.outputs.remove(to)
		to.inputs.remove(from)
		if (to.mainInput === from) to.mainInput = null
	}

	/**
	 * Advances every node by one Bluestone sub-tick.
	 *
	 * Evaluation is two-phase: first every node computes its *next* power
	 * from the previous sub-tick's snapshot ([RedstoneNode.prevPower]), then
	 * all powers are committed. This makes stepping deterministic and
	 * iteration-order independent — vanilla's evaluate() has the same
	 * property (it uses a `getPowers` pass then a `powerChanges`
	 * callback pass), and it prevents a long wire from lighting up all the
	 * way in a single sub-tick just because the engine happened to iterate
	 * in the direction of propagation.
	 *
	 * Wire power is max(inputs) - 1 (vanilla dust decay); repeaters add a
	 * configurable delay in game ticks; comparators compare their main input
	 * against their strongest side input; torches invert with vanilla
	 * anti-flicker burnout; bridges pass through max(external, wired inputs).
	 */
	fun step() {
		val stpt = subTicksPerTick.coerceAtLeast(1)
		tickCounter++

		// Snapshot the node set so the parallel-for has a stable, indexable
		// view. The map itself is only mutated from the engine thread between
		// steps (via drainCommands), so this snapshot is safe.
		val snapshot = nodes.values.toTypedArray()
		val n = snapshot.size

		// Phase 1a: snapshot prevPower for every node. Must be a separate
		// pass (even in parallel) so no thread reads another thread's
		// prevPower before it's been written — otherwise computePower()
		// would see a torn/mid-update snapshot.
		if (n >= parallelThreshold) {
			IntStream.range(0, n).parallel().forEach { i ->
				val node = snapshot[i]
				node.prevPower = node.power
			}
		} else {
			for (i in 0 until n) {
				snapshot[i].prevPower = snapshot[i].power
			}
		}

		// Phase 1b: compute next power from the now-stable prevPower snapshot.
		if (n >= parallelThreshold) {
			IntStream.range(0, n).parallel().forEach { i ->
				snapshot[i].power = computePower(snapshot[i], stpt)
			}
		} else {
			for (i in 0 until n) {
				snapshot[i].power = computePower(snapshot[i], stpt)
			}
		}

		// Phase 2: commit — renderedPower is what blocks read via pollOutput().
		// For wires, keep the previous rendered power when nothing changed so
		// unpowered wires don't fade through all 15 levels on removal.
		if (n >= parallelThreshold) {
			IntStream.range(0, n).parallel().forEach { i ->
				val node = snapshot[i]
				node.renderedPower = if (node.kind is NodeKind.Wire && node.power == node.prevPower) {
					node.renderedPower
				} else {
					node.power
				}
			}
		} else {
			for (i in 0 until n) {
				val node = snapshot[i]
				node.renderedPower = if (node.kind is NodeKind.Wire && node.power == node.prevPower) {
					node.renderedPower
				} else {
					node.power
				}
			}
		}
	}

	/** Computes a single node's next power from its inputs' prevPower. */
	private fun computePower(node: RedstoneNode, stpt: Int): Int = when (val kind = node.kind) {
		is NodeKind.Wire -> {
			val maxInput = node.inputs.maxOfOrNull { it.prevPower } ?: 0
			(maxInput - 1).coerceAtLeast(0)
		}

		is NodeKind.Repeater -> stepRepeater(node, kind, stpt)
		is NodeKind.Comparator -> stepComparator(node, kind, stpt)
		is NodeKind.Torch -> stepTorch(node, stpt)
		is NodeKind.Bridge -> maxOf(node.externalInput, node.inputs.maxOfOrNull { it.prevPower } ?: 0)
	}

	/**
	 * Vanilla repeater: delay = DELAY * 2 game ticks (RepeaterBlock.getDelay).
	 * When input changes, the repeater holds its current output for the delay
	 * period, then switches. A locked repeater ignores all input changes.
	 */
	private fun stepRepeater(node: RedstoneNode, kind: NodeKind.Repeater, stpt: Int): Int {
		if (kind.locked) return node.power

		val inputPowered = (node.inputs.maxOfOrNull { it.prevPower } ?: 0) > 0
		val currentlyOn = node.power > 0

		// If we have a pending state change, count down the delay.
		if (node.pendingPower != null) {
			node.delayRemaining--
			if (node.delayRemaining <= 0) {
				val result = node.pendingPower!!
				node.pendingPower = null
				node.delayRemaining = 0
				return result
			}
			return node.power
		}

		// No pending change — check if input changed.
		if (inputPowered != currentlyOn) {
			// Vanilla delay is DELAY * 2 game ticks.
			val delaySubTicks = (kind.delayTicks * 2 * stpt).coerceAtLeast(1)
			node.pendingPower = if (inputPowered) 15 else 0
			node.delayRemaining = delaySubTicks
			return node.power
		}

		return node.power
	}

	/**
	 * Vanilla comparator: output = the *main* back input's strength compared
	 * against / reduced by the strongest *side* input. Has a 2-game-tick
	 * delay (ComparatorBlock.getDelay returns 2). If there is no explicit
	 * main input (shouldn't happen with the block wired correctly), fall
	 * back to the strongest input.
	 */
	private fun stepComparator(node: RedstoneNode, kind: NodeKind.Comparator, stpt: Int): Int {
		// If we have a pending state change, count down the delay.
		if (node.pendingPower != null) {
			node.delayRemaining--
			if (node.delayRemaining <= 0) {
				val result = node.pendingPower!!
				node.pendingPower = null
				node.delayRemaining = 0
				return result
			}
			return node.power
		}

		val main = node.mainInput?.prevPower ?: (node.inputs.maxOfOrNull { it.prevPower } ?: 0)
		val side = node.inputs
			.asSequence()
			.filter { it !== node.mainInput }
			.maxOfOrNull { it.prevPower } ?: 0

		val newPower = if (kind.subtractMode) (main - side).coerceAtLeast(0) else if (main >= side) main else 0

		if (newPower != node.power) {
			// Vanilla comparator has a 2-game-tick delay.
			val delaySubTicks = (2 * stpt).coerceAtLeast(1)
			node.pendingPower = newPower
			node.delayRemaining = delaySubTicks
			return node.power
		}

		return node.power
	}

	/**
	 * Vanilla torch: output is 15 unless any input is powered (invert), with
	 * anti-flicker protection. Takes 2 game ticks to change state
	 * (RedstoneTorchBlock.TOGGLE_DELAY = 2). Burnout: if the torch is forced
	 * to turn off more than 8 times in 60 game ticks, it burns out and stays
	 * off for 160 game ticks (RESTART_DELAY).
	 */
	private fun stepTorch(node: RedstoneNode, stpt: Int): Int {
		// The torch deactivates when the block it's attached to is powered.
		// That power arrives via node.externalInput (set from the Block's
		// tick() reading level.getSignal of the attachment face). Wired
		// inputs are also included for cases where the torch directly sits
		// on a wire/bridge.
		val wiredPower = node.inputs.maxOfOrNull { it.prevPower } ?: 0
		val inputPowered = maxOf(wiredPower, node.externalInput) > 0

		// Burnout: if burned out, stay off until the timer elapses.
		if (node.torchBurnoutUntil > tickCounter) {
			return 0 // forced off, torches stay out until the timer elapses
		}

		val shouldBeOn = !inputPowered
		val currentlyOn = node.power > 0

		// If we have a pending state change, count down the delay.
		if (node.pendingPower != null) {
			node.delayRemaining--
			if (node.delayRemaining <= 0) {
				val result = node.pendingPower!!
				node.pendingPower = null
				node.delayRemaining = 0
				return result
			}
			return node.power
		}

		if (shouldBeOn != currentlyOn) {
			// Vanilla torch takes 2 game ticks to change state.
			val delaySubTicks = (2 * stpt).coerceAtLeast(1)
			node.pendingPower = if (shouldBeOn) 15 else 0
			node.delayRemaining = delaySubTicks

			// Vanilla only counts toggles on the OFF transition —
			// `isToggledTooFrequently(level, pos, true)` is called only when
			// the torch turns off (add=true). Turning back on just checks
			// without adding (add=false). Burnout triggers when >8 OFF
			// transitions happen within a 60-game-tick window, staying off
			// for 160 game ticks (RESTART_DELAY).
			if (!shouldBeOn) {
				val windowSubTicks = 60L * stpt
				if (tickCounter - node.lastToggleTime > windowSubTicks) {
					// Window expired — reset the counter.
					node.torchToggles = 0
				}
				node.torchToggles++
				node.lastToggleTime = tickCounter
				node.torchRecentlyToggled = true

				if (node.torchToggles >= 8) {
					// Vanilla's 8-toggle burnout threshold within 60 game ticks.
					node.torchBurnoutUntil = tickCounter + 160L * stpt
					node.torchToggles = 0
				}
			}
		} else if (node.torchToggles > 0) {
			// Input is stable (output didn't toggle); decay the counter like
			// vanilla's RECENT_TOGGLES timer.
			node.torchToggles--
			node.torchRecentlyToggled = false
		}

		return node.power
	}
}