package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos

/**
 * Bluestone counterpart to vanilla's RepeaterBlock. Shape, blockstate
 * properties (FACING/DELAY/LOCKED/POWERED), click-to-cycle delay, and
 * side-lock detection are copied verbatim from vanilla per the architecture
 * doc. Only the delay scheduling is rerouted: instead of
 * `level.scheduleTick(pos, this, delay)`, the repeater registers a
 * NodeKind.Repeater with the Bluestone engine, which applies the delay in
 * sub-ticks on its own thread.
 *
 * Deliberately NOT a vanilla signal source: isSignalSource/getSignal/
 * getDirectSignal are stubbed to 0/false. Bluestone and vanilla redstone
 * are independent networks that only ever talk through BluestoneBridgeBlock.
 */
class BluestoneRepeaterBlock(properties: BlockBehaviour.Properties) : HorizontalDirectionalBlock(properties) {

	companion object {
		val CODEC: MapCodec<BluestoneRepeaterBlock> = simpleCodec(::BluestoneRepeaterBlock)

		@JvmField val LOCKED: BooleanProperty = BlockStateProperties.LOCKED
		@JvmField val DELAY: IntegerProperty = BlockStateProperties.DELAY
		@JvmField val POWERED: BooleanProperty = BlockStateProperties.POWERED

		private val SHAPE: VoxelShape = column(16.0, 0.0, 2.0)
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(DELAY, 1)
				.setValue(LOCKED, false)
				.setValue(POWERED, false)
		)
	}

	override fun codec(): MapCodec<out HorizontalDirectionalBlock> = CODEC

	override fun getShape(
		state: BlockState,
		level: BlockGetter,
		pos: BlockPos,
		context: CollisionContext,
	): VoxelShape = SHAPE

	override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
		val below = pos.below()
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
	}

	override fun updateShape(
		state: BlockState,
		level: LevelReader,
		ticks: ScheduledTickAccess,
		pos: BlockPos,
		directionToNeighbour: Direction,
		neighbourPos: BlockPos,
		neighbourState: BlockState,
		random: RandomSource,
	): BlockState {
		if (directionToNeighbour == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState()
		}
		if (!level.isClientSide && directionToNeighbour.axis != state.getValue(FACING).axis) {
			// Side neighbor changed — re-evaluate lock state.
			val locked = isLocked(level, pos, state)
			if (state.getValue(LOCKED) != locked) {
				return state.setValue(LOCKED, locked)
			}
		}
		return state
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
		val state = defaultBlockState().setValue(FACING, context.horizontalDirection)
		return state.setValue(LOCKED, isLocked(context.level, context.clickedPos, state))
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING, DELAY, LOCKED, POWERED)
	}

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hitResult: BlockHitResult,
	): InteractionResult {
		if (!player.abilities.mayBuild) return InteractionResult.PASS
		if (!level.isClientSide) {
			val newState = state.cycle(DELAY)
			level.setBlock(pos, newState, 3)
			// Update the engine node's delay.
			Bluestone.engine.setNodeKind(packed(pos), NodeKind.Repeater(newState.getValue(DELAY), newState.getValue(LOCKED)))
		}
		return InteractionResult.SUCCESS
	}

	/** Vanilla side-lock: a strong side signal locks the repeater. */
	private fun isLocked(level: LevelReader, pos: BlockPos, state: BlockState): Boolean {
		val facing = state.getValue(FACING)
		val clockWise = facing.clockWise
		val counterClockWise = facing.counterClockWise
		return maxOf(
			level.getControlInputSignal(pos.relative(clockWise), clockWise, true),
			level.getControlInputSignal(pos.relative(counterClockWise), counterClockWise, true),
		) > 0
	}

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	/** Connects this repeater's input (behind) and output (front) to adjacent Bluestone wires. */
	private fun connectToBluestoneNeighbors(level: Level, pos: BlockPos, state: BlockState) {
		val self = packed(pos)
		val facing = state.getValue(FACING)
		val behind = pos.relative(facing)
		val front = pos.relative(facing.opposite)
		val behindBlock = level.getBlockState(behind).block
		val frontBlock = level.getBlockState(front).block
		if (behindBlock is BluestoneWireBlock || behindBlock is BluestoneBridgeBlock) {
			Bluestone.engine.connect(packed(behind), self)
		}
		if (frontBlock is BluestoneWireBlock || frontBlock is BluestoneBridgeBlock) {
			Bluestone.engine.connect(self, packed(front))
		}
		// Side-lock: a powered repeater/comparator on the side locks this repeater.
		// Connect side inputs so the engine can detect lock state changes.
		val clockWise = facing.clockWise
		val counterClockWise = facing.counterClockWise
		for (side in listOf(clockWise, counterClockWise)) {
			val sideBlock = level.getBlockState(pos.relative(side)).block
			if (sideBlock is BluestoneRepeaterBlock || sideBlock is BluestoneComparatorBlock) {
				Bluestone.engine.connect(packed(pos.relative(side)), self)
			}
		}
	}

	override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
		if (level.isClientSide) return
		Bluestone.engine.pushInput(packed(pos), NodeKind.Repeater(state.getValue(DELAY), state.getValue(LOCKED)), 0)
		connectToBluestoneNeighbors(level, pos, state)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Vanilla DiodeBlock.setPlacedBy: if the repeater should turn on immediately,
	// schedule a tick so it powers up right away.
	override fun setPlacedBy(
		level: Level,
		pos: BlockPos,
		state: BlockState,
		placer: net.minecraft.world.entity.LivingEntity?,
		stack: net.minecraft.world.item.ItemStack,
	) {
		val behind = pos.relative(state.getValue(FACING))
		val behindBlock = level.getBlockState(behind).block
		if (behindBlock is BluestoneWireBlock || behindBlock is BluestoneBridgeBlock) {
			val inputPower = Bluestone.engine.pollOutput(packed(behind))
			if (inputPower > 0 && !level.blockTicks.willTickThisTick(pos, this)) {
				level.scheduleTick(pos, this, 1)
			}
		}
	}

	override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
		if (movedByPiston) return
		Bluestone.engine.removeNode(packed(pos))
	}

	override fun neighborChanged(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		neighborBlock: Block,
		orientation: Orientation?,
		movedByPiston: Boolean,
	) {
		if (level.isClientSide) return
		if (!state.canSurvive(level, pos)) {
			dropResources(state, level, pos)
			level.removeBlock(pos, false)
			return
		}
		connectToBluestoneNeighbors(level, pos, state)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Vanilla-visible POWERED syncs from the engine once per game tick.
	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		val powered = Bluestone.engine.pollOutput(packed(pos)) > 0
		if (state.getValue(POWERED) != powered) {
			level.setBlock(pos, state.setValue(POWERED, powered), 2)
			// Cascade to the front so downstream wires re-sync.
			level.updateNeighborsAt(pos.relative(state.getValue(FACING).opposite), this)
		}
	}

	// Deliberately not a vanilla signal source — Bluestone and vanilla
	// redstone are independent networks (see class doc).
	override fun isSignalSource(state: BlockState): Boolean = false

	override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int = 0

	override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int = 0

	override fun ownSignal(state: BlockState, level: BlockGetter, pos: BlockPos): Int = 0

	override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
		if (!state.getValue(POWERED)) return
		val direction = state.getValue(FACING)
		val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.2
		val y = pos.y + 0.4 + (random.nextDouble() - 0.5) * 0.2
		val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.2
		var offset = -5.0f
		if (random.nextBoolean()) {
			offset = (state.getValue(DELAY) * 2 - 1).toFloat()
		}
		offset /= 16.0f
		val xo = offset * direction.stepX
		val zo = offset * direction.stepZ
		level.addParticle(DustParticleOptions.REDSTONE, x + xo, y, z + zo, 0.0, 0.0, 0.0)
	}
}
