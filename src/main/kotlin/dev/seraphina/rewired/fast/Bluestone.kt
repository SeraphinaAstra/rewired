package dev.seraphina.rewired.fast

/**
 * The single shared Bluestone engine instance for this game session.
 * Blocks (BluestoneWireBlock, BluestoneTorchBlock, BluestoneBridgeBlock,
 * etc.) all push/poll through this object rather than owning their own
 * engine. Started/stopped from the mod's main entrypoint lifecycle
 * (server starting / stopping events).
 */
object Bluestone {
	val engine: FastRedstoneEngine = FastRedstoneEngine()

	/** Default matches the architecture doc's suggested starting point: 1 sub-tick per game tick (i.e. same rate as vanilla until tuned). */
	const val DEFAULT_SUBTICKS_PER_TICK = 1

	fun start() {
		engine.setSubTicksPerTick(DEFAULT_SUBTICKS_PER_TICK)
		engine.start()
	}

	fun stop() {
		engine.stop()
	}
}
