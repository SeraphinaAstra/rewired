package dev.coder2195.rewired.registry;

import dev.coder2195.rewired.Rewired;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;

import java.util.function.Supplier;

public interface RewiredCreativeModeTabs {
	Holder<CreativeModeTab> GATES = register("gates", () -> new ItemStack(RewiredItems.OR_GATE.value()), (itemDisplayParameters, output) -> {
		for (var item: RewiredItems.GATES) {
			output.accept(item.value());
		}
		output.accept(RewiredItems.DYED_LAMP.value());
		output.accept(RewiredItems.ANALOG_LAMP.value());
		output.accept(RewiredItems.BLUESTONE_BRIDGE.value());
		output.accept(RewiredItems.BLUESTONE_WIRE.value());
		output.accept(RewiredItems.BLUESTONE_TORCH.value());
	});

	static Holder<CreativeModeTab> register(String id, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator generator) {
		var builder = FabricCreativeModeTab.builder();
		var built = builder.title(Component.translatable("itemGroup." + Rewired.MOD_ID + "." + id)).icon(icon).displayItems(generator).build();
		return Registry.registerForHolder(BuiltInRegistries.CREATIVE_MODE_TAB, Rewired.id(id), built);
	}

	static void init() {
		Rewired.LOGGER.info("Registering Rewired Creative Mode tabs");
	}
}