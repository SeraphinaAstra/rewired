package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.redstone.Orientation

class AnalogLampBlock(properties: BlockBehaviour.Properties) : Block(properties) {

	companion object {
		val CODEC: MapCodec<AnalogLampBlock> = simpleCodec(::AnalogLampBlock)

		@JvmField
		val LIT: BooleanProperty = BooleanProperty.create("lit")

		@JvmField
		val HUE: IntegerProperty = IntegerProperty.create("hue", 0, 15)

		@JvmStatic
		fun getLuminance(state: BlockState): Int =
			if (state.getValue(LIT)) 15 else 0
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(LIT, false)
				.setValue(HUE, 0)
		)
	}

	override fun codec(): MapCodec<out Block> = CODEC

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(LIT, HUE)
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

		val powered = level.hasNeighborSignal(pos)

		if (powered) {
			level.scheduleTick(pos, this, 1)
		} else if (state.getValue(LIT)) {
			level.setBlock(pos, state.setValue(LIT, false).setValue(HUE, 0), 2)
		}
	}

	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		val powered = level.hasNeighborSignal(pos)
		if (!powered) {
			if (state.getValue(LIT)) {
				level.setBlock(pos, state.setValue(LIT, false).setValue(HUE, 0), 2)
			}
			return
		}

		val signal = level.getBestNeighborSignal(pos)
		val hue = signal.coerceIn(1, 15)

		if (!state.getValue(LIT) || state.getValue(HUE) != hue) {
			level.setBlock(pos, state.setValue(LIT, true).setValue(HUE, hue), 2)
			level.scheduleTick(pos, this, 1)
		}
	}
}