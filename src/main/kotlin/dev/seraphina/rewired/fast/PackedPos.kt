package dev.seraphina.rewired.fast

/**
 * Packs/unpacks a block position into a single Long so the engine and its
 * graph never need to import net.minecraft.core.BlockPos. Same bit layout
 * as vanilla's BlockPos.asLong (26 bits X, 12 bits Y, 26 bits Z, signed),
 * duplicated here rather than depending on Minecraft so this file compiles
 * and unit-tests standalone (per the architecture doc's build order).
 */
object PackedPos {
	private const val PACKED_X_LENGTH = 26
	private const val PACKED_Z_LENGTH = 26
	private const val PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH
	private const val Y_OFFSET = 0L
	private const val Z_OFFSET = PACKED_Y_LENGTH.toLong()
	private const val X_OFFSET = Z_OFFSET + PACKED_Z_LENGTH
	private const val PACKED_X_MASK = (1L shl PACKED_X_LENGTH) - 1L
	private const val PACKED_Y_MASK = (1L shl PACKED_Y_LENGTH) - 1L
	private const val PACKED_Z_MASK = (1L shl PACKED_Z_LENGTH) - 1L

	fun pack(x: Int, y: Int, z: Int): Long {
		var result = 0L
		result = result or ((x.toLong() and PACKED_X_MASK) shl X_OFFSET.toInt())
		result = result or ((y.toLong() and PACKED_Y_MASK) shl Y_OFFSET.toInt())
		result = result or ((z.toLong() and PACKED_Z_MASK) shl Z_OFFSET.toInt())
		return result
	}

	fun unpackX(packed: Long): Int = (packed shl (64 - X_OFFSET - PACKED_X_LENGTH).toInt() shr (64 - PACKED_X_LENGTH).toInt()).toInt()
	fun unpackY(packed: Long): Int = (packed shl (64 - Y_OFFSET - PACKED_Y_LENGTH).toInt() shr (64 - PACKED_Y_LENGTH).toInt()).toInt()
	fun unpackZ(packed: Long): Int = (packed shl (64 - Z_OFFSET - PACKED_Z_LENGTH).toInt() shr (64 - PACKED_Z_LENGTH).toInt()).toInt()
}
