package dev.seraphina.rewired.mixin;

import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin that bridges to {@code GameRules}' private static
 * {@code registerInteger(String, GameRuleCategory, int, int)} method.
 *
 * <p>This 4-argument overload sets a max of {@link Integer#MAX_VALUE}, giving
 * the rule an effectively unlimited upper bound. The min parameter is set to
 * 0 by the caller, giving an effectively unlimited lower bound (0 simply
 * means a piston can push zero blocks).</p>
 */
@Mixin(GameRules.class)
public interface GameRulesAccess {

	@Invoker("registerInteger")
	static GameRule<Integer> invokeRegisterInteger(
		String name,
		GameRuleCategory category,
		int defaultValue,
		int min) {
		throw new AssertionError("Untransformed @Invoker");
	}
}
