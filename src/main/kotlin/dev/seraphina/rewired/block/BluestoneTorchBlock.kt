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
import net.minecraft.world.phys.shapes.VoxelShape
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos

/**
 * Bluestone counterpart to vanilla's RedstoneTorchBlock. Shape/survival
 * copied verbatim from BaseTorchBlock + RedstoneTorchBlock (per the
 * architecture doc — visuals/shape/survival stay identical to vanilla).
 * Power inversion and toggle delay are computed by the Bluestone engine
 * (NodeKind.Torch) instead of vanilla's tick-scheduling; LIT is synced
 * from the engine once per game tick in tick().
 *
 * Burnout/anti-flicker protection (vanilla's RECENT_TOGGLES / 160-tick
 * restart delay) is intentionally NOT ported yet — the engine doesn't
 * have a toggle-frequency concept. Left as a follow-up once the engine
 * side of torches is validated to actually work.
 */
class BluestoneTorchBlock(properties: BlockBehaviour.Properties) : Block(properties) {

	companion object {
		val CODEC: MapCodec<BluestoneTorchBlock> = simpleCodec(::BluestoneTorchBlock)

		@JvmField
		val LIT: BooleanProperty = BlockStateProperties.LIT

		private val SHAPE: VoxelShape = column(4.0, 0.0, 10.0)
	}

	init {
		registerDefaultState(stateDefinition.any().setValue(LIT, true))
	}

	override fun codec(): MapCodec<out Block> = CODEC

	override fun getShape(
		state: BlockState,
		level: BlockGetter,
		pos: BlockPos,
		context: CollisionContext,
	): VoxelShape = SHAPE

	override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean =
		canSupportCenter(level, pos.below(), Direction.UP)

	override fun updateShape(
		state: BlockState,
		level: LevelReader,
		ticks: ScheduledTickAccess,
		pos: BlockPos,
		directionToNeighbour: Direction,
		neighbourPos: BlockPos,
		neighbourState: BlockState,
		random: RandomSource,
	): BlockState =
		if (directionToNeighbour == Direction.DOWN && !canSurvive(state, level, pos)) {
			Blocks.AIR.defaultBlockState()
		} else {
			super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random)
		}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(LIT)
	}

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
		if (level.isClientSide) return
		Bluestone.engine.pushInput(packed(pos), NodeKind.Torch, if (level.hasSignal(pos.below(), Direction.DOWN)) 15 else 0)
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

		val inputSignal = if (level.hasSignal(pos.below(), Direction.DOWN)) 15 else 0
		Bluestone.engine.pushInput(packed(pos), NodeKind.Torch, inputSignal)

		if (!level.blockTicks.willTickThisTick(pos, this)) {
			level.scheduleTick(pos, this, 1)
		}
	}

	// Vanilla-visible LIT syncs from the engine once per game tick, same
	// cadence rule as lamps/pistons per the architecture doc.
	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		val lit = Bluestone.engine.pollOutput(packed(pos)) > 0
		if (state.getValue(LIT) != lit) {
			level.setBlock(pos, state.setValue(LIT, lit), 3)
		}
		level.scheduleTick(pos, this, 1)
	}

	override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
		if (direction == Direction.DOWN) getSignal(state, level, pos, direction) else 0

	override fun isSignalSource(state: BlockState): Boolean = true

	override fun ownSignal(state: BlockState, level: BlockGetter, pos: BlockPos): Int =
		if (state.getValue(LIT)) 15 else 0

	override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
		if (direction != Direction.UP) ownSignal(state, level, pos) else 0

	override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
		if (!state.getValue(LIT)) return
		val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.2
		val y = pos.y + 0.7 + (random.nextDouble() - 0.5) * 0.2
		val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.2
		level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0.0, 0.0, 0.0)
	}
}
