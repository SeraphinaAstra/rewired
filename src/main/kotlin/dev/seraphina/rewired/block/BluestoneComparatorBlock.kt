package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
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
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.ComparatorMode
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos

/**
 * Bluestone counterpart to vanilla's ComparatorBlock. Shape, blockstate
 * properties (FACING/MODE/POWERED), click-to-toggle mode, and the block
 * entity for output storage are copied verbatim from vanilla per the
 * architecture doc. Only the power computation is rerouted: instead of
 * vanilla's calculateOutputSignal, the comparator registers a
 * NodeKind.Comparator with the Bluestone engine, which computes compare/
 * subtract from its main (back) and side inputs on its own thread.
 *
 * Deliberately NOT a vanilla signal source: isSignalSource/getSignal/
 * getDirectSignal are stubbed to 0/false. Bluestone and vanilla redstone
 * are independent networks that only ever talk through BluestoneBridgeBlock.
 */
class BluestoneComparatorBlock(properties: BlockBehaviour.Properties) : HorizontalDirectionalBlock(properties), EntityBlock {

	companion object {
		val CODEC: MapCodec<BluestoneComparatorBlock> = simpleCodec(::BluestoneComparatorBlock)

		@JvmField val MODE: EnumProperty<ComparatorMode> = BlockStateProperties.MODE_COMPARATOR
		@JvmField val POWERED: BooleanProperty = BlockStateProperties.POWERED

		private val SHAPE: VoxelShape = column(16.0, 0.0, 2.0)
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(POWERED, false)
				.setValue(MODE, ComparatorMode.COMPARE)
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
		return state
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection)

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING, MODE, POWERED)
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
			val newState = state.cycle(MODE)
			val pitch = if (newState.getValue(MODE) == ComparatorMode.SUBTRACT) 0.55f else 0.5f
			level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, pitch)
			level.setBlock(pos, newState, 2)
			// Update the engine node's mode.
			Bluestone.engine.setNodeKind(packed(pos), NodeKind.Comparator(newState.getValue(MODE) == ComparatorMode.SUBTRACT))
		}
		return InteractionResult.SUCCESS
	}

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	/** Connects the comparator's main (back), side, and output (front) to adjacent Bluestone wires/bridges. */
	private fun connectToBluestoneNeighbors(level: Level, pos: BlockPos, state: BlockState) {
		val self = packed(pos)
		val facing = state.getValue(FACING)
		val behind = pos.relative(facing)
		val front = pos.relative(facing.opposite)
		val left = pos.relative(facing.clockWise)
		val right = pos.relative(facing.counterClockWise)

		// Main input: the block behind the comparator.
		val behindBlock = level.getBlockState(behind).block
		if (behindBlock is BluestoneWireBlock || behindBlock is BluestoneBridgeBlock) {
			Bluestone.engine.setMainInput(self, packed(behind))
		}

		// Side inputs.
		for (side in listOf(left, right)) {
			val sideBlock = level.getBlockState(side).block
			if (sideBlock is BluestoneWireBlock || sideBlock is BluestoneBridgeBlock) {
				Bluestone.engine.connect(packed(side), self)
			}
		}

		// Output: the block in front.
		val frontBlock = level.getBlockState(front).block
		if (frontBlock is BluestoneWireBlock || frontBlock is BluestoneBridgeBlock) {
			Bluestone.engine.connect(self, packed(front))
		}
	}

	override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
		if (level.isClientSide) return
		Bluestone.engine.pushInput(packed(pos), NodeKind.Comparator(state.getValue(MODE) == ComparatorMode.SUBTRACT), 0)
		connectToBluestoneNeighbors(level, pos, state)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Vanilla DiodeBlock.setPlacedBy: if the comparator should turn on immediately,
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
		val output = Bluestone.engine.pollOutput(packed(pos))
		val powered = output > 0
		// Update the block entity's stored output signal (vanilla comparator behavior).
		val blockEntity = level.getBlockEntity(pos)
		if (blockEntity is BluestoneComparatorBlockEntity) {
			blockEntity.setOutputSignal(output)
		}
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

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		BluestoneComparatorBlockEntity(pos, state)
}
