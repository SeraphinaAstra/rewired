package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos
import net.minecraft.world.item.context.BlockPlaceContext

class BluestoneTorchBlock(properties: BlockBehaviour.Properties) : Block(properties) {

	companion object {
		val CODEC: MapCodec<BluestoneTorchBlock> = simpleCodec(::BluestoneTorchBlock)

		@JvmField
		val FACING = BlockStateProperties.FACING

		@JvmField
		val LIT: BooleanProperty = BlockStateProperties.LIT

		// Floor torch shape (placed on top of blocks)
		private val FLOOR_SHAPE: VoxelShape = Shapes.box(6.0, 0.0, 6.0, 10.0, 7.0, 10.0)

		// Wall torch shapes per direction (matching vanilla wall torch hitbox)
		private val WALL_SHAPES: Map<Direction, VoxelShape> = mapOf(
			Direction.NORTH to Shapes.box(6.0, 4.0, 11.0, 10.0, 12.0, 15.0),
			Direction.SOUTH to Shapes.box(6.0, 4.0, 1.0, 10.0, 12.0, 5.0),
			Direction.EAST to Shapes.box(1.0, 4.0, 6.0, 5.0, 12.0, 10.0),
			Direction.WEST to Shapes.box(11.0, 4.0, 6.0, 15.0, 12.0, 10.0)
		)
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, Direction.UP)
				.setValue(LIT, true)
		)
	}

	override fun codec(): MapCodec<out Block> = CODEC

	override fun getShape(
		state: BlockState,
		level: BlockGetter,
		pos: BlockPos,
		context: CollisionContext,
	): VoxelShape = when (state.getValue(FACING)) {
		Direction.UP, Direction.DOWN -> FLOOR_SHAPE
		else -> WALL_SHAPES[state.getValue(FACING)] ?: FLOOR_SHAPE
	}

	override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
		val facing = state.getValue(FACING)
		val attachPos = pos.relative(facing.opposite)
		return canSupportCenter(level, attachPos, facing)
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
		val facing = state.getValue(FACING)
		if (directionToNeighbour == facing.opposite && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState()
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random)
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING, LIT)
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
		val facing = when (context.clickedFace) {
			Direction.DOWN -> Direction.UP
			else -> context.clickedFace
		}
		return defaultBlockState().setValue(FACING, facing)
	}

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	/** Connects this torch to the block it's attached to (for deactivation) and to adjacent wires/bridges. */
	private fun connectToBluestoneNeighbors(level: Level, pos: BlockPos, state: BlockState) {
		val self = packed(pos)
		val facing = state.getValue(FACING)
		val attachPos = pos.relative(facing.opposite)
		val attachBlock = level.getBlockState(attachPos).block

		// Input from the attachment block itself (for torch deactivation)
		Bluestone.engine.connect(packed(attachPos), self)

		// Output to adjacent wires/bridges on all 4 horizontal sides
		for (direction in Direction.Plane.HORIZONTAL) {
			val neighborPos = pos.relative(direction)
			val neighborBlock = level.getBlockState(neighborPos).block
			if (neighborBlock is BluestoneWireBlock || neighborBlock is BluestoneBridgeBlock) {
				Bluestone.engine.connect(self, packed(neighborPos))
			}
		}
	}

	override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
		if (level.isClientSide) return
		Bluestone.engine.pushInput(packed(pos), NodeKind.Torch, 0)
		connectToBluestoneNeighbors(level, pos, state)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
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
		connectToBluestoneNeighbors(level, pos, state)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Tick every game tick to stay in sync with vanilla redstone.
	// The engine runs at sub-tick rate internally, but blocks only poll
	// once per game tick to avoid unnecessary work.
	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		// Per the wiki, a torch deactivates when the block it's attached to is
		// powered. Vanilla RedstoneTorchBlock.hasNeighborSignal checks
		// `level.hasSignal(pos.below(), Direction.DOWN)` for floor torches,
		// and RedstoneWallTorchBlock checks `level.hasSignal(attachPos, facing)`
		// for wall torches. The torch itself emits 0 toward its attachment
		// face, so there's no feedback.
		val facing = state.getValue(FACING)
		val attachPos = pos.relative(facing.opposite)
		val attachmentPower = if (
			if (facing == Direction.UP) level.hasSignal(pos.below(), Direction.DOWN)
			else level.hasSignal(attachPos, facing)
		) 15 else 0

		// Push this as our input to the engine (torch inverts: on when input=0, off when input>0)
		Bluestone.engine.pushInput(packed(pos), NodeKind.Torch, attachmentPower)

		// Read the engine's output
		val lit = Bluestone.engine.pollOutput(packed(pos)) > 0
		if (state.getValue(LIT) != lit) {
			level.setBlock(pos, state.setValue(LIT, lit), 3)
		}

		// Always re-schedule to stay in sync
		level.scheduleTick(pos, this, 1)
	}

	// The torch IS always a vanilla signal source (vanilla returns true
	// unconditionally; ownSignal returns 0 when unlit).
	override fun isSignalSource(state: BlockState): Boolean = true

	/**
	 * Vanilla getSignal: returns ownSignal for all directions EXCEPT UP.
	 * The torch does NOT power the block above it (that's getDirectSignal's job).
	 */
	override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
		if (direction == Direction.UP) 0 else ownSignal(state, level, pos)

	/**
	 * Vanilla getDirectSignal: only returns signal for DOWN direction
	 * (strongly powers the block below).
	 */
	override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
		if (direction == Direction.DOWN) ownSignal(state, level, pos) else 0

	override fun ownSignal(state: BlockState, level: BlockGetter, pos: BlockPos): Int =
		if (state.getValue(LIT)) 15 else 0

	override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
		if (!state.getValue(LIT)) return
		val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.2
		val y = pos.y + 0.7 + (random.nextDouble() - 0.5) * 0.2
		val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.2
		level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0)
	}
}