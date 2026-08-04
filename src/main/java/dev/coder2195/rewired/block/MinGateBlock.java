package dev.coder2195.rewired.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DiodeBlock;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public class MinGateBlock extends GateBlock {
	public static final MapCodec<MinGateBlock> CODEC = simpleCodec(MinGateBlock::new);

	public MinGateBlock(Properties properties) {
		super(properties);
	}

	@Override
	public int calcSignal(Optional<Integer> leftInput, Optional<Integer> centerInput, Optional<Integer> rightInput) {
		int minValue = Integer.MAX_VALUE;
		boolean hasInput = false;

		if (leftInput.isPresent()) {
			minValue = Math.min(minValue, leftInput.get());
			hasInput = true;
		}

		if (centerInput.isPresent()) {
			minValue = Math.min(minValue, centerInput.get());
			hasInput = true;
		}

		if (rightInput.isPresent()) {
			minValue = Math.min(minValue, rightInput.get());
			hasInput = true;
		}

		return hasInput ? Math.max(0, minValue) : 0;
	}

	@Override
	protected @NonNull MapCodec<? extends DiodeBlock> codec() {
		return CODEC;
	}
}