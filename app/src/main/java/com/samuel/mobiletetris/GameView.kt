package com.samuel.mobiletetris

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.content.SharedPreferences
import kotlin.concurrent.thread
import kotlin.math.abs

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val engine = TetrisEngine()
    private var gameThread: Thread? = null
    private var running = false

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var swipeStartY = 0f

    private val restartButtonRect = RectF()
    private val backButtonRect = RectF()
    private val helpButtonRect = RectF()
    private var isShowingHelp = false

    private val prefs: SharedPreferences = context.getSharedPreferences("TetrisPrefs", Context.MODE_PRIVATE)
    var highScore: Int = prefs.getInt("high_score", 0)
        private set

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        gameThread = thread(start = true) {
            while (running) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawGame(canvas)
                    holder.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(16) // ~60 FPS
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    private fun drawGame(canvas: Canvas) {
        engine.update()

        // Fallout Background (Dark Green Gradient / Scanlines feel)
        val bg = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.rgb(0, 20, 0),
            Color.rgb(0, 5, 0),
            Shader.TileMode.CLAMP
        )
        val paint = Paint()
        paint.shader = bg
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val pipBoyGreen = Color.rgb(26, 255, 128)

        when (engine.gameState) {
            TetrisEngine.GameState.START -> drawStartMenu(canvas, pipBoyGreen)
            TetrisEngine.GameState.PLAYING -> drawPlayingState(canvas, pipBoyGreen)
            TetrisEngine.GameState.GAME_OVER -> drawGameOver(canvas, pipBoyGreen)
        }

        // CRT Scanline effect simulation (Always on top)
        paint.shader = null
        paint.color = Color.argb(30, 0, 0, 0)
        paint.strokeWidth = 2f
        for (i in 0 until height step 8) {
            canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
        }
    }

    private fun drawStartMenu(canvas: Canvas, color: Int) {
        val centerH = height / 2f
        val centerW = width / 2f

        if (isShowingHelp) {
            drawHelpMenu(canvas, color)
            return
        }

        val textPaint = Paint().apply {
            this.color = color
            textSize = 100f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(20f, 0f, 0f, color)
        }

        canvas.drawText("VAULT-TEC", centerW, centerH - 200f, textPaint)
        
        textPaint.textSize = 60f
        canvas.drawText("TETRIS TERMINAL", centerW, centerH - 120f, textPaint)

        // Blinking Press to Start
        if ((System.currentTimeMillis() / 700) % 2 == 0L) {
            textPaint.textSize = 45f
            canvas.drawText(">> PRESS TO START <<", centerW, centerH + 200f, textPaint)
        }

        // Draw Help Button
        val btnW = 300f
        val btnH = 80f
        val btnX = centerW - btnW / 2
        val btnY = height - 250f
        helpButtonRect.set(btnX, btnY, btnX + btnW, btnY + btnH)

        val btnPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(helpButtonRect, 10f, 10f, btnPaint)

        val btnTextPaint = Paint().apply {
            this.color = color
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("[HOW TO PLAY]", centerW, btnY + 50f, btnTextPaint)

        // Decorative borders
        val borderPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(50f, 100f, width - 50f, height - 100f, borderPaint)
    }

    private fun drawHelpMenu(canvas: Canvas, color: Int) {
        val padding = 100f
        val textPaint = Paint().apply {
            this.color = color
            textSize = 45f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, color)
        }

        canvas.drawText("INSTRUCTIONS:", padding, 200f, textPaint)
        
        textPaint.textSize = 35f
        val instructions = listOf(
            "- TAP: ROTATE PIECE",
            "- SWIPE L/R: MOVE",
            "- SWIPE DOWN: SOFT DROP",
            "- SWIPE UP: QUICK ROTATE",
            "",
            "- CLEAR LINES TO SCORE",
            "- DON'T REACH THE TOP!"
        )

        instructions.forEachIndexed { index, text ->
            canvas.drawText(text, padding, 300f + (index * 80f), textPaint)
        }

        textPaint.textSize = 40f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("TAP ANYWHERE TO RETURN", width / 2f, height - 200f, textPaint)
    }

    private fun drawGameOver(canvas: Canvas, color: Int) {
        val centerH = height / 2f
        val centerW = width / 2f

        // Update high score
        if (engine.score > highScore) {
            highScore = engine.score
            prefs.edit().putInt("high_score", highScore).apply()
        }

        val textPaint = Paint().apply {
            this.color = color
            textSize = 120f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(25f, 0f, 0f, color)
        }

        canvas.drawText("FATAL ERROR", centerW, centerH - 100f, textPaint)
        
        textPaint.textSize = 50f
        canvas.drawText("SYSTEM OVERLOAD", centerW, centerH, textPaint)
        canvas.drawText("FINAL SCORE: ${engine.score}", centerW, centerH + 80f, textPaint)
        canvas.drawText("BEST RECORD: $highScore", centerW, centerH + 160f, textPaint)

        if ((System.currentTimeMillis() / 700) % 2 == 0L) {
            textPaint.textSize = 40f
            canvas.drawText("TAP TO REBOOT", centerW, centerH + 250f, textPaint)
        }
    }

    private fun drawPlayingState(canvas: Canvas, pipBoyGreen: Int) {
        // Enlarged Board layout
        val boardYOffset = 100f
        val boardScale = 0.92f // Increased from 0.8f to 92% of screen width
        
        // Draw Board
        engine.draw(canvas, width, height, boardYOffset, boardScale)

        // Calculate board height to position UI right below it
        val availableWidth = width * boardScale
        val blockSize = (availableWidth / 10).toInt() // 10 columns
        val boardHeight = 20 * blockSize // 20 rows
        val uiTop = boardYOffset + boardHeight + 20f // Tighter gap below the board

        // UI Constants
        val padding = 60f

        // Draw Score (Bottom Left of board)
        val textPaint = Paint().apply {
            color = pipBoyGreen
            textSize = 40f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            setShadowLayer(10f, 0f, 0f, pipBoyGreen)
        }
        canvas.drawText("SCORE: ${engine.score}", padding, uiTop + 45f, textPaint)
        canvas.drawText("BEST: $highScore", padding, uiTop + 95f, textPaint)

        // Draw Restart Button (Bottom Right of board)
        val btnW = 240f
        val btnH = 90f
        val btnX = width - btnW - padding
        val btnY = uiTop
        restartButtonRect.set(btnX, btnY, btnX + btnW, btnY + btnH)

        val btnPaint = Paint().apply {
            color = Color.argb(100, 0, 40, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(restartButtonRect, 5f, 5f, btnPaint)

        // Button Border
        btnPaint.apply {
            color = pipBoyGreen
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(restartButtonRect, 5f, 5f, btnPaint)

        val btnTextPaint = Paint().apply {
            color = pipBoyGreen
            textSize = 35f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("[RESTART]", btnX + btnW / 2f, btnY + 58f, btnTextPaint)

        // Draw Back Button (Next to Restart)
        val backBtnW = 280f
        val backBtnX = btnX - backBtnW - 20f
        backButtonRect.set(backBtnX, btnY, backBtnX + backBtnW, btnY + btnH)

        canvas.drawRoundRect(backButtonRect, 5f, 5f, btnPaint)
        canvas.drawText("RETURN TO MAIN MENU", backBtnX + backBtnW / 2f, btnY + 58f, btnTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                swipeStartY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - lastTouchX
                val dy = event.y - swipeStartY

                if (engine.gameState == TetrisEngine.GameState.START) {
                    if (isShowingHelp) {
                        isShowingHelp = false
                    } else if (helpButtonRect.contains(event.x, event.y)) {
                        isShowingHelp = true
                    } else {
                        engine.reset()
                    }
                } else if (engine.gameState == TetrisEngine.GameState.GAME_OVER) {
                    engine.reset()
                } else if (restartButtonRect.contains(event.x, event.y)) {
                    engine.reset()
                } else if (backButtonRect.contains(event.x, event.y)) {
                    engine.gameState = TetrisEngine.GameState.START
                } else if (abs(dx) < 20 && abs(dy) < 20) {
                    // Tap to rotate
                    engine.rotate()
                } else {
                    // Swipe logic
                    if (abs(dx) > 80) {
                        if (dx > 0) engine.moveRight()
                        else engine.moveLeft()
                    }

                    if (dy < -120) engine.rotate()
                    if (dy > 120) engine.softDrop()
                }
            }
        }
        return true
    }
}
