package com.inknote.app.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.inknote.app.data.Pt
import com.inknote.app.data.StrokeData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Tool { PEN, BALL, BRUSH, HIGHLIGHT, ERASER, LASSO, SHAPE, TEXT, STICKER }

/**
 * Tek bir sayfanın çizim yüzeyi. Canlı çizim Google Ink API'nin [InProgressStrokesView] (düşük gecikmeli
 * front-buffer render + motion prediction) bileşeniyle yapılır, biten çizgiler [CanvasStrokeRenderer] ile
 * çizilir. Veri sayfa genişliğine göre normalize edilmiş [StrokeData] olarak tutulur.
 */
class InkPageView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onChanged(before: List<StrokeData>, after: List<StrokeData>)
        fun onSelection(bounds: RectF?)
    }

    var listener: Listener? = null

    // ---- Dışarıdan ayarlanan araç durumu ----
    var tool: Tool = Tool.PEN
    var colorArgb: Int = Color.BLACK
    var widthDp: Float = 2.5f
    /** true: yalnızca kalem çizer, parmak sayfayı kaydırır (palm rejection). */
    var stylusOnly: Boolean = false

    // ---- Veri ----
    private var data: List<StrokeData> = emptyList()
    private var strokes: List<Stroke?> = emptyList()
    private var gestureActive = false
    private var selected: Set<Int> = emptySet()

    // ---- Ink motoru ----
    private val density = context.resources.displayMetrics.density
    private val renderer = CanvasStrokeRenderer.create()
    private val identity = Matrix()
    private val inProgress = InProgressStrokesView(context)
    private val predictor: MotionEventPredictor = MotionEventPredictor.newInstance(this)
    private val kinds = HashMap<InProgressStrokeId, String>()
    private var currentId: InProgressStrokeId? = null
    private var currentPointer = -1
    private var activeTool: Tool = Tool.PEN
    private var activeToolType = 0

    // eraser / lasso durumu
    private var eraseBefore: List<StrokeData>? = null
    private val lassoPath = Path()
    private val lassoPoints = ArrayList<Float>()

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = 0xFF363BAB.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x14363BAB }

    private val finishedLayer = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            for (s in strokes) {
                if (s != null) runCatching { renderer.draw(canvas, s, identity) }
            }
            if (!lassoPath.isEmpty) {
                canvas.drawPath(lassoPath, fillPaint)
                canvas.drawPath(lassoPath, dashPaint)
            }
            selectionBounds()?.let {
                val pad = 8f * density
                val r = RectF(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad)
                canvas.drawRoundRect(r, 12f * density, 12f * density, fillPaint)
                canvas.drawRoundRect(r, 12f * density, 12f * density, dashPaint)
            }
        }
    }

    init {
        setWillNotDraw(true)
        addView(finishedLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(inProgress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inProgress.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                handleFinished(strokes)
            }
        })
    }

    // ------------------------------------------------------------------ veri

    fun setData(list: List<StrokeData>) {
        if (list === data || gestureActive) return
        data = list
        selected = emptySet()
        rebuild()
        listener?.onSelection(null)
    }

    private fun rebuild() {
        val w = width.toFloat()
        strokes = if (w <= 0f) emptyList() else data.map { runCatching { toStroke(it, w) }.getOrNull() }
        finishedLayer.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) rebuild()
    }

    private fun MutableStrokeInputBatch.addOrIgnore(
        type: InputToolType,
        x: Float,
        y: Float,
        elapsedTimeMillis: Long,
        pressure: Float = 0f
    ) {
        try {
            add(type = type, x = x, y = y, elapsedTimeMillis = elapsedTimeMillis, pressure = pressure)
        } catch (_: Throwable) {
            // Ignore points that cannot be added to stroke batch
        }
    }

    private fun toStroke(d: StrokeData, w: Float): Stroke {
        val brush = Brush.createWithColorIntArgb(
            family = familyFor(d.kind),
            colorIntArgb = d.argb,
            size = max(0.5f, d.size * w),
            epsilon = 0.1f,
        )
        val type = when (d.tool) { 0 -> InputToolType.STYLUS; 1 -> InputToolType.TOUCH; else -> InputToolType.MOUSE }
        val batch = MutableStrokeInputBatch()
        var lastT = -1L
        for (p in d.pts) {
            val t = if (p.t <= lastT) lastT + 1 else p.t
            lastT = t
            batch.addOrIgnore(type, p.x * w, p.y * w, t, pressure = p.p)
        }
        return Stroke(brush, batch)
    }

    private fun familyFor(kind: String): BrushFamily = when (kind) {
        "ball" -> BALL
        "hl" -> HIGHLIGHTER
        else -> PRESSURE_PEN // pen, brush
    }

    private fun kindFor(t: Tool) = when (t) {
        Tool.BALL -> "ball"; Tool.BRUSH -> "brush"; Tool.HIGHLIGHT -> "hl"; else -> "pen"
    }

    private fun sizeMultiplier(t: Tool) = when (t) {
        Tool.BALL -> 0.8f; Tool.BRUSH -> 2.4f; Tool.HIGHLIGHT -> 7f; else -> 1f
    }

    // ------------------------------------------------------------------ dokunma

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tt = e.getToolType(0)
                val isPen = tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER
                if (stylusOnly && !isPen) return false          // parmak = kaydırma
                activeTool = if (tt == MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else tool
                if (activeTool in INACTIVE) return false
                activeToolType = when (tt) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> 0
                    MotionEvent.TOOL_TYPE_MOUSE -> 2
                    else -> 1
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureActive = true
                currentPointer = e.getPointerId(0)
                if (selected.isNotEmpty()) clearSelection()
                when (activeTool) {
                    Tool.ERASER -> { eraseBefore = data; eraseAt(e.x, e.y) }
                    Tool.LASSO -> { lassoPath.rewind(); lassoPoints.clear(); lassoPath.moveTo(e.x, e.y); lassoPoints.add(e.x); lassoPoints.add(e.y) }
                    else -> startInk(e)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureActive) return false
                val idx = e.findPointerIndex(currentPointer)
                if (idx < 0) return true
                when (activeTool) {
                    Tool.ERASER -> {
                        for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
                        eraseAt(e.getX(idx), e.getY(idx))
                    }
                    Tool.LASSO -> {
                        lassoPath.lineTo(e.getX(idx), e.getY(idx)); lassoPoints.add(e.getX(idx)); lassoPoints.add(e.getY(idx))
                        finishedLayer.invalidate()
                    }
                    else -> moveInk(e)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!gestureActive) return false
                when (activeTool) {
                    Tool.ERASER -> commitErase()
                    Tool.LASSO -> finishLasso()
                    else -> endInk(e)
                }
                gestureActive = false
                currentPointer = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!gestureActive) return false
                when (activeTool) {
                    Tool.ERASER -> commitErase()
                    Tool.LASSO -> { lassoPath.rewind(); finishedLayer.invalidate() }
                    else -> currentId?.let { runCatching { inProgress.cancelStroke(it, e) }; kinds.remove(it) }
                }
                currentId = null
                gestureActive = false
                currentPointer = -1
                return true
            }
        }
        return gestureActive
    }

    // ---- canlı çizim (Ink) ----

    private fun startInk(e: MotionEvent) {
        requestUnbufferedDispatch(e)
        predictor.record(e)
        val kind = kindFor(activeTool)
        val brush = Brush.createWithColorIntArgb(
            family = familyFor(kind),
            colorIntArgb = brushColor(kind),
            size = widthDp * density * sizeMultiplier(activeTool),
            epsilon = 0.1f,
        )
        val id = inProgress.startStroke(e, currentPointer, brush)
        currentId = id
        kinds[id] = kind
    }

    private fun moveInk(e: MotionEvent) {
        val id = currentId ?: return
        predictor.record(e)
        val prediction = predictor.predict()
        try {
            inProgress.addToStroke(e, currentPointer, id, prediction)
        } finally {
            prediction?.recycle()
        }
    }

    private fun endInk(e: MotionEvent) {
        val id = currentId ?: return
        predictor.record(e)
        inProgress.finishStroke(e, currentPointer, id)
        currentId = null
    }

    private fun brushColor(kind: String): Int =
        if (kind == "hl") Color.argb(0xA0, Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb)) else colorArgb

    private fun handleFinished(finished: Map<InProgressStrokeId, Stroke>) {
        val w = width.toFloat()
        if (w <= 0f || finished.isEmpty()) {
            inProgress.removeFinishedStrokes(finished.keys); return
        }
        val before = data
        val newData = ArrayList<StrokeData>()
        val newStrokes = ArrayList<Stroke?>()
        for ((id, stroke) in finished) {
            val kind = kinds.remove(id) ?: "pen"
            val n = stroke.inputs.size
            if (n == 0) continue
            val pts = ArrayList<Pt>(n)
            for (i in 0 until n) {
                val inp = stroke.inputs[i]
                pts.add(Pt(inp.x / w, inp.y / w, inp.elapsedTimeMillis, inp.pressure))
            }
            newData.add(StrokeData(kind, stroke.brush.colorIntArgb, stroke.brush.size / w, activeToolType, pts))
            newStrokes.add(stroke)
        }
        if (newData.isNotEmpty()) {
            data = before + newData
            strokes = strokes + newStrokes
            finishedLayer.invalidate()
            // önce biten çizgi bizim katmanda görünsün, sonra canlı katmandan sil (titreme olmasın)
            finishedLayer.post { inProgress.removeFinishedStrokes(finished.keys) }
            listener?.onChanged(before, data)
        } else {
            inProgress.removeFinishedStrokes(finished.keys)
        }
    }

    // ---- silgi ----

    private fun eraseAt(x: Float, y: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        val r = 12f * density
        var removed = false
        val nd = ArrayList<StrokeData>(data.size)
        val ns = ArrayList<Stroke?>(data.size)
        for (i in data.indices) {
            if (hits(data[i], x, y, r, w)) removed = true else { nd.add(data[i]); ns.add(strokes.getOrNull(i)) }
        }
        if (removed) { data = nd; strokes = ns; finishedLayer.invalidate() }
    }

    private fun hits(d: StrokeData, x: Float, y: Float, r: Float, w: Float): Boolean {
        val rr = r + d.size * w / 2f
        var px = d.pts[0].x * w
        var py = d.pts[0].y * w
        if (dist(px, py, x, y) <= rr) return true
        for (i in 1 until d.pts.size) {
            val cx = d.pts[i].x * w
            val cy = d.pts[i].y * w
            if (segDist(x, y, px, py, cx, cy) <= rr) return true
            px = cx; py = cy
        }
        return false
    }

    private fun commitErase() {
        val before = eraseBefore
        eraseBefore = null
        if (before != null && data !== before) {
            val after = data
            gestureActive = false
            listener?.onChanged(before, after)
        }
    }

    // ---- kement ----

    private fun finishLasso() {
        val w = width
        val h = height
        lassoPath.close()
        if (w <= 0 || h <= 0 || lassoPoints.size < 6) { lassoPath.rewind(); finishedLayer.invalidate(); return }
        val region = Region()
        region.setPath(lassoPath, Region(Rect(0, 0, w, h)))
        val sel = HashSet<Int>()
        val wf = w.toFloat()
        for (i in data.indices) {
            val pts = data[i].pts
            var inside = 0
            for (p in pts) if (region.contains((p.x * wf).toInt(), (p.y * wf).toInt())) inside++
            if (pts.isNotEmpty() && inside >= pts.size * 0.8f) sel.add(i)
        }
        lassoPath.rewind()
        selected = sel
        finishedLayer.invalidate()
        listener?.onSelection(selectionBounds())
    }

    private fun selectionBounds(): RectF? {
        if (selected.isEmpty()) return null
        val w = width.toFloat()
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in selected) {
            val d = data.getOrNull(i) ?: continue
            for (p in d.pts) {
                l = min(l, p.x * w); r = max(r, p.x * w); t = min(t, p.y * w); b = max(b, p.y * w)
            }
        }
        return if (l > r) null else RectF(l, t, r, b)
    }

    fun deleteSelection() {
        if (selected.isEmpty()) return
        val before = data
        val after = before.filterIndexed { i, _ -> i !in selected }
        selected = emptySet()
        data = after
        rebuild()
        listener?.onSelection(null)
        listener?.onChanged(before, after)
    }

    fun clearSelection() {
        selected = emptySet()
        finishedLayer.invalidate()
        listener?.onSelection(null)
    }

    // ------------------------------------------------------------------ yardımcılar

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return dist(px, py, ax, ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return dist(px, py, ax + t * dx, ay + t * dy)
    }

    companion object {
        private val INACTIVE = setOf(Tool.SHAPE, Tool.TEXT, Tool.STICKER)
        private val PRESSURE_PEN: BrushFamily by lazy { StockBrushes.pressurePen(version = StockBrushes.PressurePenVersion.V1) }
        private val BALL: BrushFamily by lazy { StockBrushes.marker(version = StockBrushes.MarkerVersion.V1) }
        private val HIGHLIGHTER: BrushFamily by lazy { StockBrushes.highlighter(version = StockBrushes.HighlighterVersion.V1) }
    }
}
