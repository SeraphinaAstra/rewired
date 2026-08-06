package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.util.Util
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.block.state.properties.RedstoneSide
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos

/**
 * Bluestone counterpart to vanilla's RedStoneWireBlock. Shape building,
 * connectivity math (getConnectionState / getMissingConnections /
 * getConnectingSide), particle spawning, and the color table are copied
 * near-verbatim per the architecture doc — visuals/connectivity stay
 * identical to vanilla. Only power computation is rerouted: instead of
 * DefaultRedstoneWireEvaluator, this pushes into the Bluestone engine and
 * reads POWER back from it once per game tick.
 */
class BluestoneWireBlock(properties: BlockBehaviour.Properties) : Block(properties) {

	companion object {
		val CODEC: MapCodec<BluestoneWireBlock> = simpleCodec(::BluestoneWireBlock)

		@JvmField val NORTH: EnumProperty<RedstoneSide> = BlockStateProperties.NORTH_REDSTONE
		@JvmField val EAST: EnumProperty<RedstoneSide> = BlockStateProperties.EAST_REDSTONE
		@JvmField val SOUTH: EnumProperty<RedstoneSide> = BlockStateProperties.SOUTH_REDSTONE
		@JvmField val WEST: EnumProperty<RedstoneSide> = BlockStateProperties.WEST_REDSTONE
		@JvmField val POWER: IntegerProperty = BlockStateProperties.POWER

		private val PROPERTY_BY_DIRECTION: Map<Direction, EnumProperty<RedstoneSide>> = mapOf(
			Direction.NORTH to NORTH,
			Direction.EAST to EAST,
			Direction.SOUTH to SOUTH,
			Direction.WEST to WEST,
		)

		private val COLORS: IntArray = IntArray(16).also { arr ->
			for (i in 0..15) {
				val power = i / 15.0f
				val red = power * 0.6f + (if (power > 0.0f) 0.4f else 0.3f)
				val green = Mth.clamp(power * power * 0.7f - 0.5f, 0.0f, 1.0f)
				val blue = Mth.clamp(power * power * 0.6f - 0.7f, 0.0f, 1.0f)
				arr[i] = ARGB.colorFromFloat(1.0f, red, green, blue)
			}
		}

		fun getColorForPower(power: Int): Int = COLORS[power]
	}

	private val shapes: (BlockState) -> VoxelShape
	private val crossState: BlockState

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(NORTH, RedstoneSide.NONE)
				.setValue(EAST, RedstoneSide.NONE)
				.setValue(SOUTH, RedstoneSide.NONE)
				.setValue(WEST, RedstoneSide.NONE)
				.setValue(POWER, 0)
		)
		shapes = makeShapes()
		crossState = defaultBlockState()
			.setValue(NORTH, RedstoneSide.SIDE)
			.setValue(EAST, RedstoneSide.SIDE)
			.setValue(SOUTH, RedstoneSide.SIDE)
			.setValue(WEST, RedstoneSide.SIDE)
	}

	override fun codec(): MapCodec<out Block> = CODEC

	private fun makeShapes(): (BlockState) -> VoxelShape {
		val dot = column(10.0, 0.0, 1.0)
		val floor = Shapes.rotateHorizontal(boxZ(10.0, 0.0, 1.0, 0.0, 8.0))
		val up = Shapes.rotateHorizontal(boxZ(10.0, 16.0, 0.0, 1.0))

		val cache = HashMap<BlockState, VoxelShape>()
		return { state ->
			cache.getOrPut(state) {
				var shape: VoxelShape = dot
				for ((direction, property) in PROPERTY_BY_DIRECTION) {
					shape = when (state.getValue(property)) {
						RedstoneSide.UP -> Shapes.or(shape, floor.getValue(direction), up.getValue(direction))
						RedstoneSide.SIDE -> Shapes.or(shape, floor.getValue(direction))
						RedstoneSide.NONE -> shape
					}
				}
				shape
			}
		}
	}

	override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
		shapes(state)

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		getConnectionState(context.level, crossState, context.clickedPos)

	private fun getConnectionState(level: BlockGetter, initialState: BlockState, pos: BlockPos): BlockState {
		val wasDot = isDot(initialState)
		var state = getMissingConnections(level, defaultBlockState().setValue(POWER, initialState.getValue(POWER)), pos)
		if (wasDot && isDot(state)) return state

		val north = state.getValue(NORTH).isConnected
		val south = state.getValue(SOUTH).isConnected
		val east = state.getValue(EAST).isConnected
		val west = state.getValue(WEST).isConnected
		val northSouthEmpty = !north && !south
		val eastWestEmpty = !east && !west

		if (!west && northSouthEmpty) state = state.setValue(WEST, RedstoneSide.SIDE)
		if (!east && northSouthEmpty) state = state.setValue(EAST, RedstoneSide.SIDE)
		if (!north && eastWestEmpty) state = state.setValue(NORTH, RedstoneSide.SIDE)
		if (!south && eastWestEmpty) state = state.setValue(SOUTH, RedstoneSide.SIDE)

		return state
	}

	private fun getMissingConnections(level: BlockGetter, initialState: BlockState, pos: BlockPos): BlockState {
		var state = initialState
		val canConnectUp = !level.getBlockState(pos.above()).isRedstoneConductor(level, pos)
		for (direction in Direction.Plane.HORIZONTAL) {
			val property = PROPERTY_BY_DIRECTION.getValue(direction)
			if (!state.getValue(property).isConnected) {
				state = state.setValue(property, getConnectingSide(level, pos, direction, canConnectUp))
			}
		}
		return state
	}

	private fun getConnectingSide(level: BlockGetter, pos: BlockPos, direction: Direction): RedstoneSide =
		getConnectingSide(level, pos, direction, !level.getBlockState(pos.above()).isRedstoneConductor(level, pos))

	private fun getConnectingSide(level: BlockGetter, pos: BlockPos, direction: Direction, canConnectUp: Boolean): RedstoneSide {
		val relativePos = pos.relative(direction)
		val relativeState = level.getBlockState(relativePos)
		if (canConnectUp) {
			val isPlaceableAbove = relativeState.block is net.minecraft.world.level.block.TrapDoorBlock || canSurviveOn(level, relativePos, relativeState)
			if (isPlaceableAbove && shouldConnectTo(level.getBlockState(relativePos.above()))) {
				return if (relativeState.isFaceSturdy(level, relativePos, direction.opposite)) RedstoneSide.UP else RedstoneSide.SIDE
			}
		}

		return if (!shouldConnectTo(relativeState, direction) &&
			(relativeState.isRedstoneConductor(level, relativePos) || !shouldConnectTo(level.getBlockState(relativePos.below())))
		) RedstoneSide.NONE else RedstoneSide.SIDE
	}

	private fun canSurviveOn(level: BlockGetter, relativePos: BlockPos, relativeState: BlockState): Boolean =
		relativeState.isFaceSturdy(level, relativePos, Direction.UP) || relativeState.`is`(Blocks.HOPPER)

	override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
		val below = pos.below()
		return canSurviveOn(level, below, level.getBlockState(below))
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
		if (directionToNeighbour == Direction.DOWN) {
			return if (!canSurvive(state, level, pos)) Blocks.AIR.defaultBlockState() else state
		}
		if (directionToNeighbour == Direction.UP) {
			return getConnectionState(level, state, pos)
		}

		val sideConnection = getConnectingSide(level, pos, directionToNeighbour)
		val property = PROPERTY_BY_DIRECTION.getValue(directionToNeighbour)
		return if (sideConnection.isConnected == state.getValue(property).isConnected && !isCross(state)) {
			state.setValue(property, sideConnection)
		} else {
			getConnectionState(
				level,
				crossState.setValue(POWER, state.getValue(POWER)).setValue(property, sideConnection),
				pos,
			)
		}
	}

	private fun shouldConnectTo(blockState: BlockState, direction: Direction? = null): Boolean {
		if (blockState.block === this) return true
		if (blockState.`is`(net.minecraft.world.level.block.Blocks.REPEATER)) {
			val repeaterDirection = blockState.getValue(net.minecraft.world.level.block.RepeaterBlock.FACING)
			return repeaterDirection == direction || repeaterDirection.opposite == direction
		}
		return if (blockState.`is`(net.minecraft.world.level.block.Blocks.OBSERVER)) {
			direction == blockState.getValue(net.minecraft.world.level.block.ObserverBlock.FACING)
		} else {
			blockState.isSignalSource && direction != null
		}
	}

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	private fun isCross(state: BlockState): Boolean =
		state.getValue(NORTH).isConnected && state.getValue(SOUTH).isConnected &&
			state.getValue(EAST).isConnected && state.getValue(WEST).isConnected

	private fun isDot(state: BlockState): Boolean =
		!state.getValue(NORTH).isConnected && !state.getValue(SOUTH).isConnected &&
			!state.getValue(EAST).isConnected && !state.getValue(WEST).isConnected

	private fun connectToNeighborWires(level: Level, pos: BlockPos) {
		val self = packed(pos)
		for (direction in Direction.values()) {
			val neighborPos = pos.relative(direction)
			if (level.getBlockState(neighborPos).block === this) {
				Bluestone.engine.connect(packed(neighborPos), self)
				Bluestone.engine.connect(self, packed(neighborPos))
			}
		}
	}

	override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
		if (oldState.block === state.block || level.isClientSide) return
		Bluestone.engine.pushInput(packed(pos), NodeKind.Wire, 0)
		connectToNeighborWires(level, pos)
		for (direction in Direction.Plane.VERTICAL) {
			level.updateNeighborsAt(pos.relative(direction), this)
		}
	}

	override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
		if (movedByPiston) return
		for (direction in Direction.values()) {
			level.updateNeighborsAt(pos.relative(direction), this)
		}
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
		connectToNeighborWires(level, pos)
		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Vanilla-visible POWER syncs from the engine once per game tick.
	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		val power = Bluestone.engine.pollOutput(packed(pos))
		if (state.getValue(POWER) != power) {
			level.setBlock(pos, state.setValue(POWER, power), 2)
		}
	}

	override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
		state.getSignal(level, pos, direction)

	override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
		if (direction == Direction.DOWN) return 0
		val power = ownSignal(state, level, pos)
		if (power == 0) return 0
		return if (direction != Direction.UP &&
			!getConnectionState(level, state, pos).getValue(PROPERTY_BY_DIRECTION.getValue(direction.opposite)).isConnected
		) 0 else power
	}

	override fun ownSignal(state: BlockState, level: BlockGetter, pos: BlockPos): Int = state.getValue(POWER)

	override fun isSignalSource(state: BlockState): Boolean = true

	override fun rotate(state: BlockState, rotation: net.minecraft.world.level.block.Rotation): BlockState = when (rotation) {
		net.minecraft.world.level.block.Rotation.CLOCKWISE_180 -> state
			.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST))
			.setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST))
		net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90 -> state
			.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH))
			.setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH))
		net.minecraft.world.level.block.Rotation.CLOCKWISE_90 -> state
			.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH))
			.setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH))
		else -> state
	}

	override fun mirror(state: BlockState, mirror: net.minecraft.world.level.block.Mirror): BlockState = when (mirror) {
		net.minecraft.world.level.block.Mirror.LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH))
		net.minecraft.world.level.block.Mirror.FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST))
		else -> super.mirror(state, mirror)
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(NORTH, EAST, SOUTH, WEST, POWER)
	}

	override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
		if (!player.abilities.mayBuild) return InteractionResult.PASS

		if (isCross(state) || isDot(state)) {
			var newState = if (isCross(state)) defaultBlockState() else crossState
			newState = newState.setValue(POWER, state.getValue(POWER))
			newState = getConnectionState(level, newState, pos)
			if (newState != state) {
				level.setBlock(pos, newState, 3)
				return InteractionResult.SUCCESS
			}
		}
		return InteractionResult.PASS
	}

	override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
		val power = state.getValue(POWER)
		if (power == 0) return
		for (horizontal in Direction.Plane.HORIZONTAL) {
			val connection = state.getValue(PROPERTY_BY_DIRECTION.getValue(horizontal))
			when (connection) {
				RedstoneSide.UP -> {
					spawnParticlesAlongLine(level, random, pos, COLORS[power], horizontal, Direction.UP, -0.5f, 0.5f)
					spawnParticlesAlongLine(level, random, pos, COLORS[power], Direction.DOWN, horizontal, 0.0f, 0.5f)
				}
				RedstoneSide.SIDE -> spawnParticlesAlongLine(level, random, pos, COLORS[power], Direction.DOWN, horizontal, 0.0f, 0.5f)
				RedstoneSide.NONE -> spawnParticlesAlongLine(level, random, pos, COLORS[power], Direction.DOWN, horizontal, 0.0f, 0.3f)
			}
		}
	}

	private fun spawnParticlesAlongLine(
		level: Level,
		random: RandomSource,
		pos: BlockPos,
		color: Int,
		side: Direction,
		along: Direction,
		from: Float,
		to: Float,
	) {
		val span = to - from
		if (random.nextFloat() >= 0.2f * span) return
		val sideOffset = 0.4375f
		val positionOnLine = from + span * random.nextFloat()
		val x = 0.5 + sideOffset * side.stepX + positionOnLine * along.stepX
		val y = 0.5 + sideOffset * side.stepY + positionOnLine * along.stepY
		val z = 0.5 + sideOffset * side.stepZ + positionOnLine * along.stepZ
		level.addParticle(DustParticleOptions(color, 1.0f), pos.x + x, pos.y + y, pos.z + z, 0.0, 0.0, 0.0)
	}
}
