package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.kazumaproject.petagent.petpack.PetPack
import com.kazumaproject.petagent.petpack.SpriteAnimation
import java.io.IOException
import kotlin.math.min

class PetSpriteView @JvmOverloads constructor(
    context: Context,
    private val petPack: PetPack,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val bitmapCache = mutableMapOf<String, Bitmap?>()

    private var currentAnimation: SpriteAnimation? = null
    private var currentFrameIndex: Int = 0
    private var animationStartedAtNanos: Long = 0L
    private var completionSent = false

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = false
        contentDescription = petPack.manifest.displayName
    }

    fun setAnimation(
        animationKey: String,
        frameTimeNanos: Long,
        restart: Boolean = false,
    ) {
        val resolvedAnimation = petPack.resolveAnimation(animationKey)
        if (!restart && resolvedAnimation?.key == currentAnimation?.key) return

        currentAnimation = resolvedAnimation
        currentFrameIndex = 0
        animationStartedAtNanos = frameTimeNanos
        completionSent = false
        invalidate()
    }

    fun advance(frameTimeNanos: Long): AnimationAdvance {
        val animation = currentAnimation ?: return AnimationAdvance(animationKey = null, finished = false)
        val bitmap = bitmapFor(animation) ?: return AnimationAdvance(animationKey = animation.key, finished = true)
        val frameCount = safeFrameCount(animation, bitmap)
        val frameDurationMs = 1_000f / animation.fps.toFloat()
        val elapsedMs = ((frameTimeNanos - animationStartedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        val rawFrame = (elapsedMs / frameDurationMs).toInt()
        val finished = !animation.loop && rawFrame >= frameCount
        val nextFrameIndex = if (animation.loop) {
            rawFrame % frameCount
        } else {
            min(rawFrame, frameCount - 1)
        }

        if (nextFrameIndex != currentFrameIndex) {
            currentFrameIndex = nextFrameIndex
            invalidate()
        }

        return AnimationAdvance(
            animationKey = animation.key,
            finished = finished && !completionSent,
        ).also {
            if (it.finished) {
                completionSent = true
            }
        }
    }

    fun animationFps(animationKey: String): Int {
        return petPack.resolveAnimation(animationKey)?.fps ?: DEFAULT_FPS
    }

    fun unload() {
        bitmapCache.values.forEach { it?.recycle() }
        bitmapCache.clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val animation = currentAnimation ?: return
        val bitmap = bitmapFor(animation) ?: return
        val frameCount = safeFrameCount(animation, bitmap)
        val frameIndex = currentFrameIndex.coerceIn(0, frameCount - 1)
        val left = frameIndex * animation.frameWidth

        sourceRect.set(
            left,
            0,
            (left + animation.frameWidth).coerceAtMost(bitmap.width),
            animation.frameHeight.coerceAtMost(bitmap.height),
        )
        destinationRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, paint)
    }

    private fun safeFrameCount(animation: SpriteAnimation, bitmap: Bitmap): Int {
        val framesInBitmap = (bitmap.width / animation.frameWidth).coerceAtLeast(1)
        return min(animation.frameCount, framesInBitmap).coerceAtLeast(1)
    }

    private fun bitmapFor(animation: SpriteAnimation): Bitmap? {
        if (bitmapCache.containsKey(animation.key)) {
            return bitmapCache[animation.key]
        }

        val bitmap = try {
            context.assets.open("${petPack.basePath}/${animation.file}").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: IOException) {
            null
        }

        bitmapCache[animation.key] = bitmap
        return bitmap
    }

    data class AnimationAdvance(
        val animationKey: String?,
        val finished: Boolean,
    )

    private companion object {
        const val DEFAULT_FPS = 8
    }
}
