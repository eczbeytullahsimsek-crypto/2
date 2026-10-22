package com.inknote.app.ink

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF

/**
 * ViewPort state manager that handles the single-page coordinate system.
 *
 * Stroke Coordinate System:
 * - Fixed Document space: width = [DOC_WIDTH] (1000f), height = [docHeight] (1000f / aspect).
 * - All vector strokes ([androidx.ink.strokes.Stroke]) are stored and constructed
 *   in Document space.
 *
 * Screen Coordinate System:
 * - Local view pixels: [viewWidth] x [viewHeight].
 *
 * Matrices:
 * - [strokeToViewMatrix]: Document -> View pixels. Scale = [viewWidth] / [docWidth].
 *   Used by [androidx.ink.rendering.android.canvas.CanvasStrokeRenderer] during draw passes.
 * - [viewToStrokeMatrix]: View pixels -> Document. Scale = [docWidth] / [viewWidth].
 *   Used by [androidx.ink.authoring.InProgressStrokesView] as motionEventToWorldTransform so stylus
 *   inputs map directly to document coordinates with ZERO offset at ANY zoom level.
 */
class ViewPort(
    val docWidth: Float = DOC_WIDTH,
    var aspect: Float = 1f / 1.4142f
) {
    val docHeight: Float
        get() = docWidth / aspect.coerceAtLeast(0.1f)

    var viewWidth: Float = 0f
        private set
    var viewHeight: Float = 0f
        private set

    val scale: Float
        get() = if (docWidth > 0f && viewWidth > 0f) viewWidth / docWidth else 1.0f

    // Dedicated transformation matrices
    val strokeToViewMatrix: Matrix = Matrix()
    val viewToStrokeMatrix: Matrix = Matrix()

    private val tempPts = FloatArray(2)

    var onTransformChanged: (() -> Unit)? = null

    /**
     * Updates viewport dimensions on layout or container size changes.
     */
    fun setViewSize(width: Float, height: Float, newAspect: Float = aspect): Boolean {
        if (width <= 0f || height <= 0f) return false
        var changed = false
        if (viewWidth != width || viewHeight != height) {
            viewWidth = width
            viewHeight = height
            changed = true
        }
        if (aspect != newAspect) {
            aspect = newAspect
            changed = true
        }
        if (changed) {
            updateMatrices()
            onTransformChanged?.invoke()
        }
        return changed
    }

    private fun updateMatrices() {
        if (viewWidth <= 0f || docWidth <= 0f) {
            strokeToViewMatrix.reset()
            viewToStrokeMatrix.reset()
            return
        }
        val s = viewWidth / docWidth
        strokeToViewMatrix.reset()
        strokeToViewMatrix.setScale(s, s)

        if (!strokeToViewMatrix.invert(viewToStrokeMatrix)) {
            viewToStrokeMatrix.reset()
        }
    }

    // Coordinate conversions between View pixels and Document space
    fun viewToDoc(viewX: Float, viewY: Float): PointF {
        tempPts[0] = viewX
        tempPts[1] = viewY
        viewToStrokeMatrix.mapPoints(tempPts)
        return PointF(tempPts[0], tempPts[1])
    }

    fun docToView(docX: Float, docY: Float): PointF {
        tempPts[0] = docX
        tempPts[1] = docY
        strokeToViewMatrix.mapPoints(tempPts)
        return PointF(tempPts[0], tempPts[1])
    }

    fun mapRectToView(docRect: RectF, out: RectF) {
        strokeToViewMatrix.mapRect(out, docRect)
    }

    fun mapRectToDoc(viewRect: RectF, out: RectF) {
        viewToStrokeMatrix.mapRect(out, viewRect)
    }

    companion object {
        const val DOC_WIDTH = 1000f
    }
}
