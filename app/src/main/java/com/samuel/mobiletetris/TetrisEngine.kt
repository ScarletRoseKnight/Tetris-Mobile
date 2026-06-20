package com.samuel.mobiletetris

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class TetrisEngine {

    private val cols = 10
    private val rows = 20
    private val grid = Array(rows) { IntArray(cols) { 0 } }

    // Fallout 4 Pip-Boy Green
    private val pipBoyGreen = Color.rgb(26, 255, 128)
    private val pipBoyGreenDark = Color.rgb(0, 51, 0)

    private val shapes = listOf(
        // I
        listOf(0 to 0, -1 to 0, 1 to 0, 2 to 0) to pipBoyGreen,
        // J
        listOf(0 to 0, -1 to 0, 1 to 0, 1 to 1) to pipBoyGreen,
        // L
        listOf(0 to 0, -1 to 0, 1 to 0, -1 to 1) to pipBoyGreen,
        // O
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1) to pipBoyGreen,
        // S
        listOf(0 to 0, 1 to 0, 0 to 1, -1 to 1) to pipBoyGreen,
        // T
        listOf(0 to 0, -1 to 0, 1 to 0, 0 to 1) to pipBoyGreen,
        // Z
        listOf(0 to 0, -1 to 0, 0 to 1, 1 to 1) to pipBoyGreen
    )

    private var blockX = cols / 2
    private var blockY = 0
    private var shape = randomShape()
    private var fallTimer = 0L
    private val fallSpeed = 500L
    var score = 0
        private set

    enum class GameState { START, PLAYING, GAME_OVER }
    var gameState = GameState.START

    fun update() {
        if (gameState != GameState.PLAYING) return

        val now = System.currentTimeMillis()
        if (now - fallTimer > fallSpeed) {
            fallTimer = now
            blockY++

            if (!valid(blockX, blockY, shape)) {
                blockY--
                lockPiece()
                clearLines()
                spawnNew()
            }
        }
    }

    private fun clearLines() {
        var y = rows - 1
        var linesCleared = 0
        while (y >= 0) {
            if (grid[y].all { it != 0 }) {
                for (moveY in y downTo 1) {
                    grid[moveY] = grid[moveY - 1].copyOf()
                }
                grid[0] = IntArray(cols) { 0 }
                linesCleared++
            } else {
                y--
            }
        }
        if (linesCleared > 0) {
            score += when (linesCleared) {
                1 -> 100
                2 -> 300
                3 -> 500
                4 -> 800
                else -> 0
            }
        }
    }

    fun draw(canvas: Canvas, screenW: Int, screenH: Int, yOffset: Float = 150f, scale: Float = 0.85f) {
        val availableWidth = screenW * scale
        val blockSize = (availableWidth / cols).toInt()
        val boardWidth = cols * blockSize
        val boardHeight = rows * blockSize
        val xOffset = (screenW - boardWidth) / 2f
        val paint = Paint()

        canvas.save()
        canvas.translate(xOffset, yOffset)

        // Draw grid lines (only within the board area)
        paint.color = Color.rgb(0, 100, 0)
        paint.strokeWidth = 2f
        for (x in 0..cols) {
            canvas.drawLine((x * blockSize).toFloat(), 0f, (x * blockSize).toFloat(), boardHeight.toFloat(), paint)
        }
        for (y in 0..rows) {
            canvas.drawLine(0f, (y * blockSize).toFloat(), boardWidth.toFloat(), (y * blockSize).toFloat(), paint)
        }

        // Draw placed blocks
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (grid[y][x] != 0) {
                    drawNeonBlock(canvas, x, y, blockSize, grid[y][x])
                }
            }
        }

        // Draw falling block
        for (pos in shape.blocks) {
            drawNeonBlock(canvas, blockX + pos.first, blockY + pos.second, blockSize, shape.color)
        }

        canvas.restore()
    }

    private fun drawNeonBlock(canvas: Canvas, x: Int, y: Int, size: Int, color: Int) {
        val left = x * size.toFloat()
        val top = y * size.toFloat()
        val padding = 3f

        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // CRT Glow effect
        val glow = Paint().apply {
            this.color = color
            maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(left + padding, top + padding, left + size - padding, top + size - padding, glow)

        // Block body (Hollow look for Pip-Boy style)
        canvas.drawRect(left + padding, top + padding, left + size - padding, top + size - padding, paint)
        
        // Inner detail
        paint.strokeWidth = 1f
        canvas.drawRect(left + padding + 10, top + padding + 10, left + size - padding - 10, top + size - padding - 10, paint)
    }

    private fun valid(x: Int, y: Int, s: Shape): Boolean {
        for (pos in s.blocks) {
            val nx = x + pos.first
            val ny = y + pos.second
            if (nx !in 0 until cols || ny >= rows) return false
            if (ny >= 0 && grid[ny][nx] != 0) return false
        }
        return true
    }

    private fun lockPiece() {
        for (pos in shape.blocks) {
            val nx = blockX + pos.first
            val ny = blockY + pos.second
            if (ny in 0 until rows && nx in 0 until cols) {
                grid[ny][nx] = shape.color
            }
        }
    }

    private fun spawnNew() {
        blockX = cols / 2
        blockY = 0
        shape = randomShape()
        if (!valid(blockX, blockY, shape)) {
            // Game Over logic
            gameState = GameState.GAME_OVER
        }
    }

    fun reset() {
        for (y in 0 until rows) grid[y].fill(0)
        score = 0
        blockX = cols / 2
        blockY = 0
        shape = randomShape()
        gameState = GameState.PLAYING
    }

    fun moveLeft() { if (valid(blockX - 1, blockY, shape)) blockX-- }
    fun moveRight() { if (valid(blockX + 1, blockY, shape)) blockX++ }
    fun softDrop() { if (valid(blockX, blockY + 1, shape)) blockY++ }

    fun rotate() {
        val rotatedBlocks = shape.blocks.map { -it.second to it.first }
        val rotatedShape = Shape(rotatedBlocks, shape.color)
        if (valid(blockX, blockY, rotatedShape)) {
            shape = rotatedShape
        }
    }

    private fun randomShape(): Shape {
        val (blocks, color) = shapes.random()
        return Shape(blocks, color)
    }

    data class Shape(val blocks: List<Pair<Int, Int>>, val color: Int)
}
