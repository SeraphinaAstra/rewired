package dev.coder2195.rewired.registry;

import net.minecraft.core.Holder;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

import static dev.coder2195.rewired.Rewired.LOGGER;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public interface RewiredItems {
	Holder<Item> AND_GATE = registerBlock(RewiredBlocks.AND_GATE_ID, RewiredBlocks.AND_GATE);
	Holder<Item> OR_GATE = registerBlock(RewiredBlocks.OR_GATE_ID, RewiredBlocks.OR_GATE);
	Holder<Item> XOR_GATE = registerBlock(RewiredBlocks.XOR_GATE_ID, RewiredBlocks.XOR_GATE);
	Holder<Item> NAND_GATE = registerBlock(RewiredBlocks.NAND_GATE_ID, RewiredBlocks.NAND_GATE);
	Holder<Item> NOR_GATE = registerBlock(RewiredBlocks.NOR_GATE_ID, RewiredBlocks.NOR_GATE);
	Holder<Item> XNOR_GATE = registerBlock(RewiredBlocks.XNOR_GATE_ID, RewiredBlocks.XNOR_GATE);
	Holder<Item> AVERAGE_GATE = registerBlock(RewiredBlocks.AVERAGE_GATE_ID, RewiredBlocks.AVERAGE_GATE);
	Holder<Item> MIN_GATE = registerBlock(RewiredBlocks.MIN_GATE_ID, RewiredBlocks.MIN_GATE);
	Holder<Item> MAX_GATE = registerBlock(RewiredBlocks.MAX_GATE_ID, RewiredBlocks.MAX_GATE);
	Holder<Item> DYED_LAMP = registerBlock(RewiredBlocks.DYED_LAMP_ID, RewiredBlocks.DYED_LAMP);
	Holder<Item> ANALOG_LAMP = registerBlock(RewiredBlocks.ANALOG_LAMP_ID, RewiredBlocks.ANALOG_LAMP);
	Holder<Item> BLUESTONE_BRIDGE = registerBlock(RewiredBlocks.BLUESTONE_BRIDGE_ID, RewiredBlocks.BLUESTONE_BRIDGE);
	Holder<Item> BLUESTONE_WIRE = registerBlock(RewiredBlocks.BLUESTONE_WIRE_ID, RewiredBlocks.BLUESTONE_WIRE);
	Holder<Item> BLUESTONE_TORCH = registerBlock(RewiredBlocks.BLUESTONE_TORCH_ID, RewiredBlocks.BLUESTONE_TORCH);
	Holder<Item> BLUESTONE_REPEATER = registerBlock(RewiredBlocks.BLUESTONE_REPEATER_ID, RewiredBlocks.BLUESTONE_REPEATER);
	Holder<Item> BLUESTONE_COMPARATOR = registerBlock(RewiredBlocks.BLUESTONE_COMPARATOR_ID, RewiredBlocks.BLUESTONE_COMPARATOR);

	Holder<Item>[] GATES = new Holder[]{
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

	static Holder<Item> register(ResourceKey<Item> key, Function<Item.Properties, Item> itemFactory, Item.Properties properties) {
		properties.setId(key);

		return Registry.registerForHolder(BuiltInRegistries.ITEM, key, itemFactory.apply(properties));
	}

	static Holder<Item> registerBlock(BlockItemId key, Holder<Block> block) {
		return registerBlock(key, block, new Item.Properties());
	}

	static Holder<Item> registerBlock(BlockItemId key, Holder<Block> block, Item.Properties properties) {
		var itemKey = key.item();
		properties.useBlockDescriptionPrefix().setId(itemKey);

		return register(itemKey, properties1 -> new BlockItem(block.value(), properties), properties);
	}


	static Holder<Item> register(ResourceKey<Item> key, Function<Item.Properties, Item> itemFactory) {
		return register(key, itemFactory, new Item.Properties());
	}

	static Holder<Item> register(ResourceKey<Item> key) {
		return register(key, Item::new);
	}

	static Holder<Item> register(ResourceKey<Item> key, Item.Properties properties) {
		return register(key, Item::new, properties);
	}

	static void init() {
		LOGGER.info("Initializing Rewired Items...");
	}
}