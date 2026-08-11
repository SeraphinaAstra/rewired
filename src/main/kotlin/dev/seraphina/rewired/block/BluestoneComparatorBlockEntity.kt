package dev.seraphina.rewired.block

import dev.coder2195.rewired.registry.RewiredBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * Bluestone counterpart to vanilla's ComparatorBlockEntity. Stores the
 * computed output signal so the block can report it to vanilla redstone
 * (via getSignal) without re-running the engine on every query.
 */
class BluestoneComparatorBlockEntity(worldPosition: BlockPos, blockState: BlockState) : BlockEntity(RewiredBlockEntityTypes.BLUESTONE_COMPARATOR.value(), worldPosition, blockState) {
	private var output = 0

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		output.putInt("OutputSignal", this.output)
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		this.output = input.getIntOr("OutputSignal", 0)
	}

	fun getOutputSignal(): Int = this.output

	fun setOutputSignal(value: Int) {
		this.output = value
	}
}
