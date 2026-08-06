package dev.coder2195.rewired.block;

import dev.coder2195.rewired.block.entity.GateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * When I wrote this, only God knows what actually is happening here, because diodes are hard, most of it is copied from comparator
 * if you got a better impl, make a PR, otherwise DO NOT TOUCH
 */
public abstract class GateBlock extends DiodeBlock implements EntityBlock {
	public static final BooleanProperty LEFT_INPUT = BooleanProperty.create("left_input");
	public static final BooleanProperty RIGHT_INPUT = BooleanProperty.create("right_input");
	public static final BooleanProperty CENTER_INPUT = BooleanProperty.create("center_input");

	public GateBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.getStateDefinition().any().setValue(LEFT_INPUT, true).setValue(RIGHT_INPUT, true).setValue(CENTER_INPUT, true).setValue(POWERED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LEFT_INPUT, RIGHT_INPUT, CENTER_INPUT, POWERED, FACING);
	}

	@Override
	protected int getDelay(@NonNull BlockState state) {
		return 0;
	}

	@Override
	protected boolean shouldTurnOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state) {
		return this.getOutputSignal(level, pos, state) > 0;
	}

	@Override
	protected void checkTickOnNeighbor(Level level, @NonNull BlockPos pos, @NonNull BlockState state) {
		if (!level.getBlockTicks().willTickThisTick(pos, this)) {
			int outputValue = this.calculateOutputSignal(level, pos, state);
			int oldValue = level.getBlockEntity(pos) instanceof GateBlockEntity blockEntity ? blockEntity.getOutputSignal() : 0;
			if (outputValue != oldValue || state.getValue(POWERED) != this.shouldTurnOn(level, pos, state)) {
				TickPriority priority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
				level.scheduleTick(pos, this, this.getDelay(state), priority);
			}
		}
	}


	@Override
	protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof GateBlockEntity blockEntity ? blockEntity.getOutputSignal() : 0;
	}

	private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
		int outputValue = this.calculateOutputSignal(level, pos, state);
		int oldValue = 0;
		if (level.getBlockEntity(pos) instanceof GateBlockEntity gateBlockEntity) {
			oldValue = gateBlockEntity.getOutputSignal();
			gateBlockEntity.setOutputSignal(outputValue);
		}

		if (oldValue != outputValue) {
			boolean sourceOn = this.shouldTurnOn(level, pos, state);
			boolean isOn = state.getValue(POWERED);
			if (isOn && !sourceOn) {
				level.setBlock(pos, state.setValue(POWERED, false), UPDATE_CLIENTS);
			} else if (!isOn && sourceOn) {
				level.setBlock(pos, state.setValue(POWERED, true), UPDATE_CLIENTS);
			}

			this.updateNeighborsInFront(level, pos, state);
		}
	}

	@Override
	protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState oldState, boolean movedByPiston) {
		this.refreshOutputState(level, pos, state);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		this.refreshOutputState(level, pos, state);
	}

	private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof GateBlockEntity blockEntity)) return 0;

		Direction direction = state.getValue(FACING);

		var leftOn = state.getValue(LEFT_INPUT);
		var centerOn = state.getValue(CENTER_INPUT);
		var rightOn = state.getValue(RIGHT_INPUT);

		BlockPos centerPos = pos.relative(direction);
		BlockState centerBlockState = level.getBlockState(centerPos);


		int centerSignal = Math.max(level.getSignal(centerPos, direction), centerBlockState.is(Blocks.REDSTONE_WIRE) ? centerBlockState.getValue(RedStoneWireBlock.POWER) : 0);

		direction = direction.getCounterClockWise();
		BlockPos rightPos = pos.relative(direction);
		BlockState rightBlockState = level.getBlockState(rightPos);
		int rightSignal = Math.max(level.getSignal(rightPos, direction), rightBlockState.is(Blocks.REDSTONE_WIRE) ? rightBlockState.getValue(RedStoneWireBlock.POWER) : 0);

		direction = direction.getOpposite();
		BlockPos leftPos = pos.relative(direction);
		BlockState leftBlockState = level.getBlockState(leftPos);
		int leftSignal = Math.max(level.getSignal(leftPos, direction), leftBlockState.is(Blocks.REDSTONE_WIRE) ? leftBlockState.getValue(RedStoneWireBlock.POWER) : 0);

		int outSignal = calcSignal(
			leftOn ? Optional.of(leftSignal) : Optional.empty(),
			centerOn ? Optional.of(centerSignal) : Optional.empty(),
			rightOn ? Optional.of(rightSignal) : Optional.empty()
		);

		return outSignal;
	}


	public abstract int calcSignal(Optional<Integer> leftInput, Optional<Integer> centerInput, Optional<Integer> rightInput);

	@Override
	protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hitResult) {
		if (!player.getAbilities().mayBuild) {
			return InteractionResult.PASS;
		}

		var facing = state.getValue(FACING);
		var hitPosition = hitResult.getLocation();
		var xOffset = hitPosition.x - pos.getX();
		var zOffset = hitPosition.z - pos.getZ();


		BooleanProperty toToggle = null;
		// directions are hella weird, facing actually points opposite, but i'm gonna use the direction where the signal faces as north when the gate is north
		boolean northClick = 0.33 < xOffset && xOffset < 0.67 && 0.67 < zOffset;
		boolean westClick = 0.67 < xOffset && 0.33 < zOffset && zOffset < 0.67;
		boolean eastClick = xOffset < 0.33 && 0.33 < zOffset && zOffset < 0.67;
		boolean southClick = 0.33 < xOffset && xOffset < 0.67 && zOffset < 0.33;

		if (facing.equals(Direction.NORTH)) {
			toToggle = westClick ? LEFT_INPUT : southClick ? CENTER_INPUT : eastClick ? RIGHT_INPUT : null;
		} else if (facing.equals(Direction.SOUTH)) {
			toToggle = eastClick ? LEFT_INPUT : northClick ? CENTER_INPUT : westClick ? RIGHT_INPUT : null;
		} else if (facing.equals(Direction.EAST)) {
			toToggle = northClick ? LEFT_INPUT : westClick ? CENTER_INPUT : southClick ? RIGHT_INPUT : null;
		} else if (facing.equals(Direction.WEST)) {
			toToggle = southClick ? LEFT_INPUT : eastClick ? CENTER_INPUT : northClick ? RIGHT_INPUT : null;
		}

		if (toToggle == null) return super.useWithoutItem(state, level, pos, player, hitResult);
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(toToggle, !state.getValue(toToggle)), Block.UPDATE_CLIENTS);
			this.refreshOutputState(level, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos worldPosition, @NonNull BlockState blockState) {
		return new GateBlockEntity(worldPosition, blockState);
	}
}
