package com.picklecal.lg.patterns

import com.picklecal.lg.model.DrawCommand

/**
 * Generates test patterns matching dogegen's implementations.
 * All patterns use 4K UHD (3840x2160) reference coordinates internally,
 * converted to NDC for rendering.
 */
object PatternGenerator {

    private const val WIDTH_4K = 3840f
    private const val HEIGHT_4K = 2160f

    // ---- PLUGE Pattern (BT.814-4) ----

    fun drawPluge(hdr: Boolean, tenBit: Boolean): List<DrawCommand> {
        val maxV = if (tenBit) 1023f else 255f
        val commands = mutableListOf<DrawCommand>()

        val higher: Int = if (hdr) 399 else 940
        val black = 64
        val lighter = 80
        val darker = 48

        val horz = intArrayOf(0, 624, 1199, 1776, 2063, 2640, 3215, 3839)
        val vert = intArrayOf(0, 648, 690, 935, 936, 1223, 1224, 1469, 1511, 2159)

        fun drawCoords(x1: Int, y1: Int, x2: Int, y2: Int, code: Int) {
            val level = (code.toFloat() / (if (tenBit) 1f else 4f)) / maxV
            val cmd = DrawCommand()
            for (i in 0..2) {
                cmd.color1[i] = level; cmd.color2[i] = level
                cmd.color3[i] = level; cmd.color4[i] = level
            }
            cmd.x1 = -1f + 2f * x1 / WIDTH_4K
            cmd.y1 = 1f - 2f * y1 / HEIGHT_4K
            cmd.x2 = -1f + 2f * (x2 + 1) / WIDTH_4K
            cmd.y2 = 1f - 2f * (y2 + 1) / HEIGHT_4K
            commands.add(cmd)
        }

        fun idx(c: Char): Int = c - 'a'
        fun draw(horz1: Char, vert1: Char, horz2: Char, vert2: Char, code: Int) {
            drawCoords(horz[idx(horz1)], vert[idx(vert1)], horz[idx(horz2)], vert[idx(vert2)], code)
        }

        draw('a', 'a', 'h', 'j', black)
        draw('d', 'e', 'e', 'f', higher)
        draw('f', 'b', 'g', 'd', lighter)
        draw('f', 'g', 'g', 'i', darker)

        for (i in 0 until 20) {
            val barX1 = horz[idx('b')]
            val barY1 = vert[idx('c')] + 2 * 20 * i
            val barX2 = horz[idx('c')]
            val barY2 = barY1 + 19
            val color = if (i < 10) lighter else darker
            drawCoords(barX1, barY1, barX2, barY2, color)
        }

        return commands
    }

    // ---- Color Bars (BT.2111-2) ----

    fun drawBars(limited: Boolean): List<DrawCommand> {
        val maxV = 1023f
        val commands = mutableListOf<DrawCommand>()

        val barVals = intArrayOf(1920, 1080, 240, 206, 204, 136, 70, 68, 238, 438, 282)
        fun bar(c: Char): Int {
            val idx = c.lowercaseChar() - 'a'
            return if (c.isUpperCase()) barVals[idx] / 2 else barVals[idx]
        }

        val barWidth = bar('a')
        val barHeight = bar('b')

        fun drawCoords(x1: Int, y1: Int, x2: Int, y2: Int, rgb: IntArray) {
            val cmd = DrawCommand()
            for (i in 0..2) {
                val level = rgb[i] / maxV
                cmd.color1[i] = level; cmd.color2[i] = level
                cmd.color3[i] = level; cmd.color4[i] = level
            }
            cmd.x1 = -1f + 2f * x1 / barWidth
            cmd.y1 = 1f - 2f * y1 / barHeight
            cmd.x2 = -1f + 2f * x2 / barWidth
            cmd.y2 = 1f - 2f * y2 / barHeight
            commands.add(cmd)
        }

        val colors = if (limited) {
            arrayOf(
                intArrayOf(940, 940, 940), intArrayOf(940, 940, 64), intArrayOf(64, 940, 940),
                intArrayOf(64, 940, 64), intArrayOf(940, 64, 940), intArrayOf(940, 64, 64),
                intArrayOf(64, 64, 940), intArrayOf(572, 572, 572), intArrayOf(572, 572, 64),
                intArrayOf(64, 572, 572), intArrayOf(64, 572, 64), intArrayOf(572, 64, 572),
                intArrayOf(572, 64, 64), intArrayOf(64, 64, 572), intArrayOf(414, 414, 414)
            )
        } else {
            arrayOf(
                intArrayOf(1023, 1023, 1023), intArrayOf(1023, 1023, 0), intArrayOf(0, 1023, 1023),
                intArrayOf(0, 1023, 0), intArrayOf(1023, 0, 1023), intArrayOf(1023, 0, 0),
                intArrayOf(0, 0, 1023), intArrayOf(594, 594, 594), intArrayOf(594, 594, 0),
                intArrayOf(0, 594, 594), intArrayOf(0, 594, 0), intArrayOf(594, 0, 594),
                intArrayOf(594, 0, 0), intArrayOf(0, 0, 594), intArrayOf(390, 390, 390)
            )
        }

        var x = 0
        val h1 = bar('c')
        for (i in 0 until 7) {
            val w = bar('c')
            drawCoords(x, 0, x + w, h1, colors[i])
            x += w
        }

        x = 0
        val y2start = h1
        val h2 = bar('d')
        for (i in 7 until 15) {
            val w = bar('c')
            drawCoords(x, y2start, x + w, y2start + h2, colors[i])
            x += w
        }

        val rampColors = if (limited) {
            arrayOf(
                intArrayOf(568, 571, 381), intArrayOf(484, 566, 571), intArrayOf(474, 564, 368),
                intArrayOf(536, 361, 564), intArrayOf(530, 350, 256), intArrayOf(317, 236, 562)
            )
        } else {
            arrayOf(
                intArrayOf(589, 593, 370), intArrayOf(491, 586, 592), intArrayOf(479, 585, 355),
                intArrayOf(552, 348, 584), intArrayOf(545, 335, 225), intArrayOf(296, 201, 582)
            )
        }

        val y3start = y2start + h2
        val h3 = bar('e')
        x = 0
        for (i in 0 until 3) {
            val w = bar('c') / 3
            drawCoords(x, y3start, x + w, y3start + h3, rampColors[i])
            x += w
        }

        val grays = if (limited) {
            intArrayOf(64, 48, 64, 80, 64, 99, 64, 572, 64)
        } else {
            intArrayOf(0, 0, 0, 19, 0, 41, 0, 594, 0)
        }
        val widthChars = charArrayOf('f', 'g', 'h', 'g', 'h', 'g', 'i', 'j', 'k')
        for (i in grays.indices) {
            val w = bar(widthChars[i])
            val rgb = intArrayOf(grays[i], grays[i], grays[i])
            drawCoords(x, y3start, x + w, y3start + h3, rgb)
            x += w
        }

        for (i in 3 until 6) {
            val w = bar('c') / 3
            drawCoords(x, y3start, x + w, y3start + h3, rampColors[i])
            x += w
        }

        val y4start = y3start + h3
        val h4 = barHeight - y4start
        x = 0

        val levels = if (limited) {
            intArrayOf(572, 4, 64, 152, 239, 327, 414, 502, 590, 677, 765, 852, 940, 1019, 572)
        } else {
            intArrayOf(594, 0, 0, 102, 205, 307, 409, 512, 614, 716, 818, 921, 1023, 1023, 594)
        }

        for (i in levels.indices) {
            val w = if (i == 0 || i == levels.size - 1) bar('e') else bar('c') - bar('e') * 2 / (levels.size - 2)
            val rgb = intArrayOf(levels[i], levels[i], levels[i])
            drawCoords(x, y4start, x + w, y4start + h4, rgb)
            x += w
        }

        return commands
    }

    // ---- Window Pattern ----

    fun drawWindow(
        windowPercent: Float, r: Int, g: Int, b: Int, maxV: Float,
        bgR: Int = 0, bgG: Int = 0, bgB: Int = 0
    ): List<DrawCommand> {
        val commands = mutableListOf<DrawCommand>()

        if (bgR != 0 || bgG != 0 || bgB != 0) {
            commands.add(DrawCommand.fullFieldInt(bgR, bgG, bgB, maxV))
        }

        if (windowPercent < 100f) {
            commands.add(DrawCommand.windowPatternInt(windowPercent, r, g, b, maxV))
        } else {
            commands.add(DrawCommand.fullFieldInt(r, g, b, maxV))
        }

        return commands
    }

    // ---- Parse Commands ----

    fun parseDrawString(drawString: String, bitDepth: Int): List<DrawCommand>? {
        val maxV = ((1 shl bitDepth) - 1).toFloat()
        val commands = mutableListOf<DrawCommand>()

        if (drawString.isBlank()) return commands

        val parts = drawString.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val cmd = parseDrawCommand(trimmed, maxV) ?: return null
            commands.add(cmd)
        }
        return commands
    }

    private fun parseDrawCommand(commandStr: String, maxV: Float): DrawCommand? {
        val tokens = commandStr.trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return null

        return when (tokens[0]) {
            "window" -> parseWindowCommand(tokens, maxV)
            "draw" -> parseRawDrawCommand(tokens, maxV)
            else -> null
        }
    }

    private fun parseWindowCommand(tokens: List<String>, maxV: Float): DrawCommand? {
        if (tokens.size < 5) return null
        val windowSize = tokens[1].toFloatOrNull() ?: return null
        if (windowSize <= 0 || windowSize > 100) return null
        val r = tokens[2].toIntOrNull() ?: return null
        val g = tokens[3].toIntOrNull() ?: return null
        val b = tokens[4].toIntOrNull() ?: return null
        if (r < 0 || r > maxV || g < 0 || g > maxV || b < 0 || b > maxV) return null
        return DrawCommand.windowPatternInt(windowSize, r, g, b, maxV)
    }

    private fun parseRawDrawCommand(tokens: List<String>, maxV: Float): DrawCommand? {
        if (tokens.size < 8) return null
        val cmd = DrawCommand()
        cmd.x1 = tokens[1].toFloatOrNull() ?: return null
        cmd.y1 = tokens[2].toFloatOrNull() ?: return null
        cmd.x2 = tokens[3].toFloatOrNull() ?: return null
        cmd.y2 = tokens[4].toFloatOrNull() ?: return null

        if (tokens.size == 8) {
            val r = tokens[5].toIntOrNull() ?: return null
            val g = tokens[6].toIntOrNull() ?: return null
            val b = tokens[7].toIntOrNull() ?: return null
            cmd.setColorsFromRgb(intArrayOf(r, g, b), maxV)
        } else if (tokens.size == 18) {
            val values = mutableListOf<Int>()
            for (i in 5..17) {
                values.add(tokens[i].toIntOrNull() ?: return null)
            }
            for (i in 0..2) {
                cmd.color1[i] = values[0 * 3 + i] / maxV
                cmd.color2[i] = values[1 * 3 + i] / maxV
                cmd.color3[i] = values[2 * 3 + i] / maxV
                cmd.color4[i] = values[3 * 3 + i] / maxV
            }
            cmd.quant = values[12] / maxV
        } else {
            return null
        }

        return cmd
    }
}
