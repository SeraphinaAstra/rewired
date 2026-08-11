package dev.coder2195.rewired.registry;


import dev.coder2195.rewired.Rewired;
import dev.coder2195.rewired.block.entity.GateBlockEntity;
import dev.seraphina.rewired.block.BluestoneComparatorBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.minecraft.core.Registry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

public interface RewiredBlockEntityTypes {
	Holder<BlockEntityType<?>> GATE = register("gate", GateBlockEntity::new, RewiredBlocks.GATES);
	Holder<BlockEntityType<?>> BLUESTONE_COMPARATOR = register("bluestone_comparator", BluestoneComparatorBlockEntity::new, RewiredBlocks.BLUESTONE_COMPARATOR);

	@SafeVarargs
	static <T extends BlockEntity> Holder<BlockEntityType<?>> register(
		String id,
		FabricBlockEntityTypeBuilder.Factory<T> entityFactory,
		Holder<Block>... blockHolders
	) {
		return Registry.registerForHolder(BuiltInRegistries.BLOCK_ENTITY_TYPE, Rewired.id(id), FabricBlockEntityTypeBuilder.create(entityFactory, Arrays.stream(blockHolders).map(Holder::value).toArray(Block[]::new)).build());
	}

	static void init() {
		Rewired.LOGGER.info("Registering Rewired Block Entity Types");
	}
}
