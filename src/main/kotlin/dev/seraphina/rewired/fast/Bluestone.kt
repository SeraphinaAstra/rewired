package dev.seraphina.rewired.fast

import dev.seraphina.rewired.RewiredGameRules
import net.minecraft.server.MinecraftServer

/**
 * The single shared Bluestone engine instance for this game session.
 * Blocks (BluestoneWireBlock, BluestoneTorchBlock, BluestoneBridgeBlock,
 * etc.) all push/poll through this object rather than owning their own
 * engine. Started/stopped from the mod's main entrypoint lifecycle
 * (server starting / stopping events).
 */
object Bluestone {
	val engine: BluestoneEngine = BluestoneEngine()

	/** Default matches the architecture doc's suggested starting point: 1 sub-tick per game tick (i.e. same rate as vanilla until tuned). */
	const val DEFAULT_SUBTICKS_PER_TICK = 1

	fun start(server: MinecraftServer) {
		// Live gamerule value wins; fall back to the default if the rule
		// isn't registered yet (shouldn't happen — it's registered in init()).
		val rate = server.gameRules.get(RewiredGameRules.FAST_REDSTONE_SUBTICKS)
			.coerceAtLeast(1)
		engine.setSubTicksPerTick(rate)
		engine.start()
	}

	fun stop() {
		engine.stop()
	}
}
