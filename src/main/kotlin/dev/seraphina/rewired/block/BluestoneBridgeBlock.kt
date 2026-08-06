package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult
import dev.seraphina.rewired.fast.Bluestone
import dev.seraphina.rewired.fast.NodeKind
import dev.seraphina.rewired.fast.PackedPos

/**
 * Bluestone <-> vanilla-redstone interop block.
 *
 * Single block, two configurable faces via FACING (which way it points)
 * and INVERTED (swaps which side is input vs output) rather than two
 * separate in/out blocks — one item to place, orientation toggled by
 * right-click like a repeater/observer, matches how the rest of the mod
 * already handles directional blocks (see GateBlock's FACING usage).
 *
 * Bluestone engine hooks: the bridge is a NodeKind.Bridge node in the
 * graph. pushInput() writes the vanilla-side signal into the node's
 * externalInput (read directly by NodeKind.Bridge's step, i.e. it just
 * passes through — see RedstoneGraph.step()). pollOutput() reads back
 * whatever the graph computed for this same node — meaning a Bluestone
 * network can drive a Bridge node's "input" through wire connections
 * feeding into it, not just from pushInput; both paths land on the same
 * node so either one's presence is enough to light it up.
 */
class BluestoneBridgeBlock(properties: BlockBehaviour.Properties) : HorizontalDirectionalBlock(properties) {

	companion object {
		val CODEC: MapCodec<BluestoneBridgeBlock> = simpleCodec(::BluestoneBridgeBlock)

		// true = this block currently reads vanilla redstone on its back face
		// and writes Bluestone engine output on its front face.
		// false = reversed: reads Bluestone on the back, writes vanilla on the front.
		@JvmField
		val INVERTED: BooleanProperty = BooleanProperty.create("inverted")
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(INVERTED, false)
		)
	}

	override fun codec(): MapCodec<out HorizontalDirectionalBlock> = CODEC

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING, INVERTED)
	}

	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hit: BlockHitResult,
	): InteractionResult {
		if (!player.abilities.mayBuild) return InteractionResult.PASS

		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(INVERTED, !state.getValue(INVERTED)), Block.UPDATE_CLIENTS)
		}
		return InteractionResult.SUCCESS
	}

	// --- Vanilla redstone side ---

	private fun vanillaInputFace(state: BlockState): Direction =
		if (!state.getValue(INVERTED)) state.getValue(FACING).opposite else state.getValue(FACING)

	private fun vanillaOutputFace(state: BlockState): Direction =
		vanillaInputFace(state).opposite

	override fun isSignalSource(state: BlockState): Boolean = true

	override fun getSignal(
		state: BlockState,
		level: net.minecraft.world.level.BlockGetter,
		pos: BlockPos,
		direction: Direction,
	): Int {
		if (direction != vanillaOutputFace(state)) return 0
		return pollOutput(pos)
	}

	override fun getDirectSignal(
		state: BlockState,
		level: net.minecraft.world.level.BlockGetter,
		pos: BlockPos,
		direction: Direction,
	): Int = getSignal(state, level, pos, direction)

	override fun neighborChanged(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		neighborBlock: Block,
		orientation: Orientation?,
		movedByPiston: Boolean,
	) {
		if (level.isClientSide) return

		val inputFace = vanillaInputFace(state)
		val inputPos = pos.relative(inputFace)
		val signal = level.getSignal(inputPos, inputFace)

		pushInput(pos, signal)
	}

	// --- Bluestone engine hooks ---

	private fun packed(pos: BlockPos): Long = PackedPos.pack(pos.x, pos.y, pos.z)

	/** Called whenever the vanilla-facing side's input signal may have changed. */
	private fun pushInput(pos: BlockPos, signal: Int) {
		Bluestone.engine.pushInput(packed(pos), NodeKind.Bridge, signal)
	}

	/** Called whenever vanilla asks this block for its current output signal. */
	private fun pollOutput(pos: BlockPos): Int {
		return Bluestone.engine.pollOutput(packed(pos))
	}
}
