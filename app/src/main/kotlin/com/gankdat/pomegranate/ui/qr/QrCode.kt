package com.gankdat.pomegranate.ui.qr

/**
 * Minimal QR Code encoder (ISO/IEC 18004): byte mode, error correction
 * level M, versions 1 to 10 (up to 213 bytes), which is ample for a Picker
 * session link. Written in-house because no AndroidX library generates QR
 * codes and third-party dependencies are not allowed. Output is verified
 * module-for-module against a reference encoder in the unit tests.
 */
internal class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    /** True for a dark module; row-major ([y] then [x]). */
    fun isDark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        const val MAX_VERSION: Int = 10

        /** Encodes [text] as UTF-8; picks the lowest-penalty mask unless [forceMask] is given. */
        fun encode(text: String, forceMask: Int? = null): QrCode {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = (1..MAX_VERSION).firstOrNull { capacityBits(it) >= headerBits(it) + data.size * 8 }
                ?: throw IllegalArgumentException("Text too long for a version $MAX_VERSION QR code (${data.size} bytes)")
            val codewords = interleave(version, dataCodewords(version, data))
            val builder = Builder(version)
            builder.drawFunctionPatterns()
            builder.drawCodewords(codewords)
            val mask = forceMask ?: (0..7).minBy { m ->
                builder.applyMask(m)
                builder.drawFormatBits(m)
                val penalty = builder.penalty()
                builder.applyMask(m) // XOR again to undo
                penalty
            }
            require(mask in 0..7) { "mask must be 0..7" }
            builder.applyMask(mask)
            builder.drawFormatBits(mask)
            return QrCode(version, mask, builder.modules)
        }

        // ---- Capacity tables for level M, versions 1..10 ----
        // ecPerBlock, group-1 blocks, group-1 data codewords, group-2 blocks, group-2 data codewords
        private val BLOCKS = arrayOf(
            intArrayOf(10, 1, 16, 0, 0),
            intArrayOf(16, 1, 28, 0, 0),
            intArrayOf(26, 1, 44, 0, 0),
            intArrayOf(18, 2, 32, 0, 0),
            intArrayOf(24, 2, 43, 0, 0),
            intArrayOf(16, 4, 27, 0, 0),
            intArrayOf(18, 4, 31, 0, 0),
            intArrayOf(22, 2, 38, 2, 39),
            intArrayOf(22, 3, 36, 2, 37),
            intArrayOf(26, 4, 43, 1, 44),
        )

        private val ALIGNMENT = arrayOf(
            intArrayOf(),
            intArrayOf(6, 18),
            intArrayOf(6, 22),
            intArrayOf(6, 26),
            intArrayOf(6, 30),
            intArrayOf(6, 34),
            intArrayOf(6, 22, 38),
            intArrayOf(6, 24, 42),
            intArrayOf(6, 26, 46),
            intArrayOf(6, 28, 50),
        )

        private fun totalDataCodewords(v: Int): Int = BLOCKS[v - 1].let { it[1] * it[2] + it[3] * it[4] }

        private fun capacityBits(v: Int): Int = totalDataCodewords(v) * 8

        private fun countBits(v: Int): Int = if (v <= 9) 8 else 16

        private fun headerBits(v: Int): Int = 4 + countBits(v)

        private fun dataCodewords(v: Int, data: ByteArray): ByteArray {
            val bits = BitBuffer()
            bits.append(0b0100, 4) // byte mode
            bits.append(data.size, countBits(v))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }
            val capacity = capacityBits(v)
            bits.append(0, minOf(4, capacity - bits.length)) // terminator
            bits.append(0, (8 - bits.length % 8) % 8) // byte align
            var pad = 0xEC
            while (bits.length < capacity) {
                bits.append(pad, 8)
                pad = if (pad == 0xEC) 0x11 else 0xEC
            }
            return bits.toBytes()
        }

        /** Splits into blocks, appends Reed-Solomon codewords, and interleaves per the standard. */
        private fun interleave(v: Int, data: ByteArray): ByteArray {
            val (ecLen, g1, g1Len, g2, g2Len) = BLOCKS[v - 1].let { listOf(it[0], it[1], it[2], it[3], it[4]) }
            val generator = ReedSolomon.generator(ecLen)
            val dataBlocks = ArrayList<ByteArray>()
            val ecBlocks = ArrayList<ByteArray>()
            var offset = 0
            repeat(g1) { dataBlocks += data.copyOfRange(offset, offset + g1Len); offset += g1Len }
            repeat(g2) { dataBlocks += data.copyOfRange(offset, offset + g2Len); offset += g2Len }
            dataBlocks.forEach { ecBlocks += ReedSolomon.remainder(it, generator) }
            val out = java.io.ByteArrayOutputStream()
            val maxData = dataBlocks.maxOf { it.size }
            for (i in 0 until maxData) dataBlocks.forEach { if (i < it.size) out.write(it[i].toInt()) }
            for (i in 0 until ecLen) ecBlocks.forEach { out.write(it[i].toInt()) }
            return out.toByteArray()
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val length: Int get() = bits.size

        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += (value ushr i) and 1 == 1
        }

        fun toBytes(): ByteArray = ByteArray(bits.size / 8) { b ->
            var v = 0
            for (i in 0 until 8) v = (v shl 1) or (if (bits[b * 8 + i]) 1 else 0)
            v.toByte()
        }
    }

    private object ReedSolomon {
        private val exp = IntArray(512)
        private val log = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                exp[i] = x
                log[x] = i
                x = x shl 1
                if (x and 0x100 != 0) x = x xor 0x11D
            }
            for (i in 255 until 512) exp[i] = exp[i - 255]
        }

        private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

        /** Coefficients of prod_{i<degree} (x - a^i), highest power first, leading 1 omitted. */
        fun generator(degree: Int): IntArray {
            var poly = intArrayOf(1)
            for (i in 0 until degree) {
                val next = IntArray(poly.size + 1)
                for (j in poly.indices) {
                    next[j] = next[j] xor poly[j]
                    next[j + 1] = next[j + 1] xor mul(poly[j], exp[i])
                }
                poly = next
            }
            return poly.copyOfRange(1, poly.size)
        }

        fun remainder(data: ByteArray, generator: IntArray): ByteArray {
            val result = IntArray(generator.size)
            for (byte in data) {
                val factor = (byte.toInt() and 0xFF) xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in generator.indices) result[i] = result[i] xor mul(generator[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }
    }

    private class Builder(private val version: Int) {
        val size = 17 + 4 * version
        val modules = Array(size) { BooleanArray(size) }
        private val isFunction = Array(size) { BooleanArray(size) }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val positions = ALIGNMENT[version - 1]
            val last = positions.size - 1
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)
                    if (!corner) drawAlignment(positions[i], positions[j])
                }
            }
            drawFormatBits(0) // reserve; real bits drawn after masking
            drawVersionBits()
        }

        private fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, d != 2 && d != 4)
                }
            }
        }

        private fun drawAlignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        fun drawFormatBits(mask: Int) {
            val data = (0b00 shl 3) or mask // level M = 00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 == 1
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // dark module
        }

        private fun drawVersionBits() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0..17) {
                val dark = (bits ushr i) and 1 == 1
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        fun drawCodewords(codewords: ByteArray) {
            var i = 0
            val totalBits = codewords.size * 8
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < totalBits) {
                            modules[y][x] = (codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (isFunction[y][x]) continue
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert) modules[y][x] = !modules[y][x]
                }
            }
        }

        /** Standard penalty rules N1–N4; only used to choose a mask, so exact parity with other encoders is not required. */
        fun penalty(): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) {
                        run++
                    } else {
                        if (run >= 5) result += 3 + run - 5
                        run = 1
                    }
                }
                if (run >= 5) result += 3 + run - 5
                val pattern = booleanArrayOf(true, false, true, true, true, false, true)
                for (i in 0..size - 7) {
                    if ((0 until 7).all { get(i + it) == pattern[it] }) {
                        val before = (1..4).all { i - it < 0 || !get(i - it) }
                        val after = (1..4).all { i + 6 + it >= size || !get(i + 6 + it) }
                        if (before || after) result += 40
                    }
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
            }
            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            result += kotlin.math.abs(dark * 20 - total * 10) / total * 10
            return result
        }
    }
}
