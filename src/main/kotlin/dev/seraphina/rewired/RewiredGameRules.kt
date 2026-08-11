package dev.seraphina.rewired

import dev.seraphina.rewired.mixin.GameRulesAccess
import net.minecraft.world.level.gamerules.GameRuleCategory

/**
 * Holder for Rewired's custom game rules, following the same init-on-bootstrap
 * pattern used by [RewiredBlocks] and [RewiredItems].
 *
 * The [PISTON_PUSH_LIMIT] rule replaces the hardcoded `12` value in
 * `PistonStructureResolver.addBlockLine`.  It uses the 4-argument
 * `GameRules.registerInteger` overload which sets max to
 * `Integer.MAX_VALUE`, so there is effectively no upper bound.  Min is 0,
 * meaning "pistons push nothing" — any positive integer is a valid limit.
 *
 * The rule is registered during `ModInitializer.onInitialize()`, which runs
 * before `BuiltInRegistries` freeze their registries, so the registration
 * succeeds without needing access wideners or bootstrap injection.
 */
object RewiredGameRules {

    /**
     * The maximum number of blocks a piston can push.
     *
     * Registered with min=0 and max=Integer.MAX_VALUE (via the 4-arg
     * `registerInteger` overload), giving players full freedom to set it
     * to any number — from 0 (no pushing) to billions.
     */
    @JvmField
    val PISTON_PUSH_LIMIT = GameRulesAccess.invokeRegisterInteger(
        "rewired:piston_push_limit",
        GameRuleCategory.MISC,
        12,
        0
    )

    /**
     * Touches [PISTON_PUSH_LIMIT] to ensure the game rule is registered in
     * `BuiltInRegistries.GAME_RULE` before the registry is frozen.
     *
     * Called from `RewiredFabric.onInitialize()`.
     */
    /**
     * How many Bluestone sub-ticks run per game tick. Higher values make
     * Bluestone circuits propagate faster than vanilla redstone (the whole
     * point of the parallel engine). Live-editable; the engine picks up the
     * new value on its next step.
     */
    @JvmField
    val FAST_REDSTONE_SUBTICKS = GameRulesAccess.invokeRegisterInteger(
        "rewired:fast_redstone_subticks",
        GameRuleCategory.MISC,
        1,
        1
    )

    @JvmStatic
    fun init() {
        // Force-initialise the object so the static field initializer for
        // PISTON_PUSH_LIMIT runs during mod bootstrap (before registry freeze).
        val rule = PISTON_PUSH_LIMIT
        val subtickRule = FAST_REDSTONE_SUBTICKS
        // Silent — registration happens as a side-effect of field init.
    }
}
