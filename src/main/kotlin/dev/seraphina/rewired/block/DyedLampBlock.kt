package dev.seraphina.rewired.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

class DyedLampBlock(properties: BlockBehaviour.Properties) : Block(properties) {

	companion object {
		val CODEC: MapCodec<DyedLampBlock> = simpleCodec(::DyedLampBlock)

		@JvmField
		val COLOR: EnumProperty<DyeColor> = EnumProperty.create("color", DyeColor::class.java)

		@JvmField
		val LIT: BooleanProperty = BooleanProperty.create("lit")

		private val DYE_TO_COLOR: Map<Item, DyeColor> = DyeColor.entries.associateBy { color ->
			Items.DYE.pick(color)
		}

		@JvmStatic
		fun getLuminance(state: BlockState): Int =
			if (state.getValue(LIT)) 15 else 0
	}

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(COLOR, DyeColor.WHITE)
				.setValue(LIT, false)
		)
	}

	override fun codec(): MapCodec<out Block> = CODEC

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(COLOR, LIT)
	}

	override fun useItemOn(
		stack: ItemStack,
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hand: InteractionHand,
		hit: BlockHitResult,
	): InteractionResult {
		val item = stack.item
		if (item !is DyeItem) return InteractionResult.PASS

		val newColor = DYE_TO_COLOR[item] ?: return InteractionResult.PASS
		if (state.getValue(COLOR) == newColor) return InteractionResult.PASS

		if (!level.isClientSide) {
			level.setBlockAndUpdate(pos, state.setValue(COLOR, newColor))
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f)

			if (!player.abilities.instabuild) {
				stack.shrink(1)
			}
		}

		return InteractionResult.SUCCESS
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

		val lit = state.getValue(LIT)
		val powered = level.hasNeighborSignal(pos)

		if (lit != powered) {
			if (lit) {
				level.scheduleTick(pos, this, 4)
			} else {
				level.setBlock(pos, state.setValue(LIT, true), 2)
			}
		}
	}

	override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
		if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
			level.setBlock(pos, state.setValue(LIT, false), 2)
		}
	}
}