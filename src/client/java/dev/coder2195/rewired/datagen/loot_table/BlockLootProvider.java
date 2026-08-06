package dev.coder2195.rewired.datagen.loot_table;

import dev.coder2195.rewired.registry.RewiredBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

public class BlockLootProvider extends FabricBlockLootSubProvider {
	public BlockLootProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registriesFuture) {
		super(packOutput, registriesFuture);
	}

	@Override
	public void generate() {
		for (var gate : RewiredBlocks.GATES) {
			dropSelf(gate.value());
		}
		dropSelf(RewiredBlocks.DYED_LAMP.value());
		dropSelf(RewiredBlocks.ANALOG_LAMP.value());
	}
}
