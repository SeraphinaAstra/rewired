package dev.coder2195.rewired.registry;

import dev.coder2195.rewired.Rewired;
import dev.coder2195.rewired.block.*;
import dev.seraphina.rewired.block.DyedLampBlock;
import dev.seraphina.rewired.block.AnalogLampBlock;
import dev.seraphina.rewired.block.BluestoneBridgeBlock;
import dev.seraphina.rewired.block.BluestoneWireBlock;
import dev.seraphina.rewired.block.BluestoneTorchBlock;
import dev.seraphina.rewired.block.BluestoneRepeaterBlock;
import dev.seraphina.rewired.block.BluestoneComparatorBlock;
import net.minecraft.core.Holder;
import net.minecraft.references.BlockItemId;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Function;

public interface RewiredBlocks {
	BlockItemId AND_GATE_ID = blockItem("and_gate");
	BlockItemId OR_GATE_ID = blockItem("or_gate");
	BlockItemId XOR_GATE_ID = blockItem("xor_gate");
	BlockItemId NAND_GATE_ID = blockItem("nand_gate");
	BlockItemId NOR_GATE_ID = blockItem("nor_gate");
	BlockItemId XNOR_GATE_ID = blockItem("xnor_gate");
	BlockItemId AVERAGE_GATE_ID = blockItem("average_gate");
	BlockItemId MIN_GATE_ID = blockItem("min_gate");
	BlockItemId MAX_GATE_ID = blockItem("max_gate");
	BlockItemId DYED_LAMP_ID = blockItem("dyed_lamp");
	BlockItemId ANALOG_LAMP_ID = blockItem("analog_lamp");
	BlockItemId BLUESTONE_BRIDGE_ID = blockItem("bluestone_bridge");
	BlockItemId BLUESTONE_WIRE_ID = BlockItemId.create(Rewired.id("bluestone_wire"), Rewired.id("bluestone_dust"));
	BlockItemId BLUESTONE_TORCH_ID = blockItem("bluestone_torch");
	BlockItemId BLUESTONE_REPEATER_ID = blockItem("bluestone_repeater");
	BlockItemId BLUESTONE_COMPARATOR_ID = blockItem("bluestone_comparator");
	static BlockItemId blockItem(String id) {
		return BlockItemId.create(Rewired.id(id), Rewired.id(id));
	}

	Holder<Block> AND_GATE = register(AND_GATE_ID, AndGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> OR_GATE = register(OR_GATE_ID, OrGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> XOR_GATE = register(XOR_GATE_ID, XorGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> NAND_GATE = register(NAND_GATE_ID, NandGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> NOR_GATE = register(NOR_GATE_ID, NorGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> XNOR_GATE = register(XNOR_GATE_ID, XnorGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> AVERAGE_GATE = register(AVERAGE_GATE_ID, AverageGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> MIN_GATE = register(MIN_GATE_ID, MinGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> MAX_GATE = register(MAX_GATE_ID, MaxGateBlock::new, BlockBehaviour.Properties.of());
	Holder<Block> DYED_LAMP = register(DYED_LAMP_ID, DyedLampBlock::new, BlockBehaviour.Properties.of()
		.sound(net.minecraft.world.level.block.SoundType.GLASS)
		.lightLevel(DyedLampBlock::getLuminance)
		.strength(0.3f));
	Holder<Block> ANALOG_LAMP = register(ANALOG_LAMP_ID, AnalogLampBlock::new, BlockBehaviour.Properties.of()
		.sound(net.minecraft.world.level.block.SoundType.GLASS)
		.lightLevel(AnalogLampBlock::getLuminance)
		.strength(0.3f));
	Holder<Block> BLUESTONE_BRIDGE = register(BLUESTONE_BRIDGE_ID, BluestoneBridgeBlock::new, BlockBehaviour.Properties.of()
		.strength(1.5f));
	Holder<Block> BLUESTONE_WIRE = register(BLUESTONE_WIRE_ID, BluestoneWireBlock::new, BlockBehaviour.Properties.of()
		.noCollision()
		.instabreak()
		.sound(net.minecraft.world.level.block.SoundType.WOOL));
	Holder<Block> BLUESTONE_TORCH = register(BLUESTONE_TORCH_ID, BluestoneTorchBlock::new, BlockBehaviour.Properties.of()
		.noCollision()
		.instabreak()
		.lightLevel(state -> state.getValue(BluestoneTorchBlock.LIT) ? 7 : 0)
		.sound(net.minecraft.world.level.block.SoundType.WOOD));
	Holder<Block> BLUESTONE_REPEATER = register(BLUESTONE_REPEATER_ID, BluestoneRepeaterBlock::new, BlockBehaviour.Properties.of()
		.instabreak()
		.sound(net.minecraft.world.level.block.SoundType.WOOD));
	Holder<Block> BLUESTONE_COMPARATOR = register(BLUESTONE_COMPARATOR_ID, BluestoneComparatorBlock::new, BlockBehaviour.Properties.of()
		.instabreak()
		.sound(net.minecraft.world.level.block.SoundType.WOOD));

	Holder<Block>[] GATES = new Holder[]{
		AND_GATE,
		OR_GATE,
		XOR_GATE,
		NAND_GATE,
		NOR_GATE,
		XNOR_GATE,
		AVERAGE_GATE,
		MIN_GATE,
		MAX_GATE
	};

	static Holder<Block> register(BlockItemId id, Function<BlockBehaviour.Properties, Block> block, BlockBehaviour.Properties properties) {
		var blockKey = id.block();
		properties.setId(blockKey);

		return Registry.registerForHolder(BuiltInRegistries.BLOCK, blockKey, block.apply(properties));
	}

  static void init() {
		Rewired.LOGGER.info("Registering Rewired blocks");
  }
}
