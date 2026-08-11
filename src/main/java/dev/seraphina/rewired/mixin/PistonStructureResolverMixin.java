package dev.seraphina.rewired.mixin;

import dev.seraphina.rewired.RewiredGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Replaces the hardcoded piston push limit of 12 (found in three
 * comparison sites within {@code addBlockLine}) with a value read from the
 * {@code rewired:piston_push_limit} game rule at runtime.
 *
 * <p>The game rule is read live on every piston push resolution, so changes
 * made via {@code /gamerule rewired:piston_push_limit <n>} take effect
 * immediately without a restart.</p>
 */
@Mixin(PistonStructureResolver.class)
public class PistonStructureResolverMixin {

	@Shadow
	private Level level;

	/**
	 * Targets every {@code int} constant {@code 12} used in a comparison
	 * inside {@code addBlockLine}:
	 *
	 * <ul>
	 *   <li>{@code blockCount + this.toPush.size() > 12}</li>
	 *   <li>{@code ++blockCount + this.toPush.size() > 12}</li>
	 *   <li>{@code this.toPush.size() >= 12}</li>
	 * </ul>
	 *
	 * @param original the vanilla constant (always 12)
	 * @return the configured game-rule value, or the vanilla default on the client
	 */
	@ModifyConstant(
		method = "addBlockLine",
		constant = @Constant(intValue = 12)
	)
	private int modifyPistonPushLimit(int original) {
		if (this.level instanceof ServerLevel serverLevel) {
			return serverLevel.getGameRules().get(RewiredGameRules.INSTANCE.PISTON_PUSH_LIMIT);
		}
		// Piston resolution only happens server-side; fall back to vanilla on client.
		return original;
	}
}
