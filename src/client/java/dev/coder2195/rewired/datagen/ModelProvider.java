package dev.coder2195.rewired.datagen;

import dev.coder2195.rewired.Rewired;
import dev.coder2195.rewired.block.GateBlock;
import dev.coder2195.rewired.registry.RewiredBlocks;
import dev.coder2195.rewired.registry.RewiredItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

import static net.minecraft.client.data.models.BlockModelGenerators.*;

public class ModelProvider extends FabricModelProvider {
	public ModelProvider(FabricPackOutput output) {
		super(output);
	}

	public interface Slots {
		TextureSlot LEFT_INPUT = TextureSlot.create("left_input");
		TextureSlot CENTER_INPUT = TextureSlot.create("center_input");
		TextureSlot RIGHT_INPUT = TextureSlot.create("right_input");
		TextureSlot LABEL = TextureSlot.create("label");
	}

	private static final PropertyDispatch<VariantMutator> ROTATION_HORIZONTAL_FACING_ALT = PropertyDispatch.modify(BlockStateProperties.HORIZONTAL_FACING)
		.select(Direction.SOUTH, NOP)
		.select(Direction.WEST, Y_ROT_90)
		.select(Direction.NORTH, Y_ROT_180)
		.select(Direction.EAST, Y_ROT_270);

	ModelTemplate GATE = create("template_gate", TextureSlot.TORCH, Slots.LEFT_INPUT, Slots.CENTER_INPUT, Slots.RIGHT_INPUT, Slots.LABEL);

	@Override
	public void generateBlockStateModels(@NonNull BlockModelGenerators generator) {
		for (var gate : RewiredBlocks.GATES) {
			var gateVariants = PropertyDispatch.initial(GateBlock.LEFT_INPUT, GateBlock.CENTER_INPUT, GateBlock.RIGHT_INPUT, DiodeBlock.POWERED);
			for (var left : List.of(false, true)) {
				for (var center : List.of(false, true)) {
					for (var right : List.of(false, true)) {
						for (var powered : List.of(false, true)) {
							var gateName = gate.unwrapKey().orElseThrow().identifier().getPath();
							gateVariants.select(
								left, center, right, powered,
								BlockModelGenerators.plainVariant(
									GATE.createWithSuffix(
										gate.value(), (left ? "_left" : "") + (center ? "_center" : "") + (right ? "_right" : "") + (powered ? "_on" : ""),
										new TextureMapping()
											.put(TextureSlot.TORCH, new Material(Rewired.mcId("block/redstone_torch" + (powered ? "" : "_off"))))
											.put(Slots.LABEL, new Material(Rewired.id("block/" + gateName + "_label")))
											.put(Slots.LEFT_INPUT, new Material(Rewired.mcId("block/redstone_torch" + (left ? "" : "_off"))))
											.put(Slots.CENTER_INPUT, new Material(Rewired.mcId("block/redstone_torch" + (center ? "" : "_off"))))
											.put(Slots.RIGHT_INPUT, new Material(Rewired.mcId("block/redstone_torch" + (right ? "" : "_off")))), generator.modelOutput)
								)
							);
						}
					}
				}
			}
			generator.blockStateOutput.accept(MultiVariantGenerator.dispatch(gate.value()).with(gateVariants).with(ROTATION_HORIZONTAL_FACING_ALT));
		}
	}


	@Override
	public void generateItemModels(@NonNull ItemModelGenerators generator) {
		for (var item : RewiredItems.GATES) {
			generator.generateFlatItem(item.value(), ModelTemplates.FLAT_ITEM);
		}
	}

	private static ModelTemplate create(final String id, final TextureSlot... slots) {
		return new ModelTemplate(Optional.of(Rewired.id("block/" + id)), Optional.empty(), slots);
	}

	private static ModelTemplate createItem(final String id, final TextureSlot... slots) {
		return new ModelTemplate(Optional.of(Rewired.id("item/" + id)), Optional.empty(), slots);
	}

}