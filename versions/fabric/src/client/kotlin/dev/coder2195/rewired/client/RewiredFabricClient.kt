package dev.coder2195.rewired.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.world.item.DyeColor
import dev.seraphina.rewired.block.AnalogLampBlock
import dev.seraphina.rewired.block.DyedLampBlock
import dev.coder2195.rewired.registry.RewiredBlocks

object RewiredFabricClient : ClientModInitializer {
	private val DYE_RGB: Map<DyeColor, Int> = mapOf(
		DyeColor.WHITE to 0xF9FFFE,
		DyeColor.ORANGE to 0xF9801D,
		DyeColor.MAGENTA to 0xC74EBD,
		DyeColor.LIGHT_BLUE to 0x3AB3DA,
		DyeColor.YELLOW to 0xFED83D,
		DyeColor.LIME to 0x80C71F,
		DyeColor.PINK to 0xF38BAA,
		DyeColor.GRAY to 0x474F52,
		DyeColor.LIGHT_GRAY to 0x9D9D97,
		DyeColor.CYAN to 0x169C9C,
		DyeColor.PURPLE to 0x8932B8,
		DyeColor.BLUE to 0x3C44AA,
		DyeColor.BROWN to 0x835432,
		DyeColor.GREEN to 0x5E7C16,
		DyeColor.RED to 0xB02E26,
		DyeColor.BLACK to 0x1D1D21,
	)

	private val DYED_LAMP_TINT = BlockTintSource { state ->
		DYE_RGB.getValue(state.getValue(DyedLampBlock.COLOR))
	}

	// Analog Lamp tint: hue 1-15 maps to a color wheel.
	// When unlit (hue 0), return white so the grayscale texture shows normally.
	private val ANALOG_LAMP_TINT = BlockTintSource { state ->
		val hue = state.getValue(AnalogLampBlock.HUE)
		if (hue == 0) 0xFFFFFF else hueToRgb(hue)
	}

	// Simple 15-hue color wheel (approximate RGB for each step).
	private fun hueToRgb(hue: Int): Int {
		val h = hue / 15.0
		val x = (1.0 - kotlin.math.abs((h * 6.0) % 2.0 - 1.0))
		val (r, g, b) = when ((hue * 6 / 15) % 6) {
			0 -> Triple(1.0, x, 0.0)
			1 -> Triple(x, 1.0, 0.0)
			2 -> Triple(0.0, 1.0, x)
			3 -> Triple(0.0, x, 1.0)
			4 -> Triple(x, 0.0, 1.0)
			else -> Triple(1.0, 0.0, x)
		}
		return (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
	}

	override fun onInitializeClient() {
		BlockColorRegistry.register(listOf(DYED_LAMP_TINT), RewiredBlocks.DYED_LAMP.value())
		BlockColorRegistry.register(listOf(ANALOG_LAMP_TINT), RewiredBlocks.ANALOG_LAMP.value())
	}
}
