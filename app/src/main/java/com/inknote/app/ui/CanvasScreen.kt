package com.inknote.app.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inknote.app.AppViewModel
import com.inknote.app.Route
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PaperTones
import com.inknote.app.data.StrokeData
import com.inknote.app.ink.InkPageView
import com.inknote.app.ink.Tool
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private class ViewHolder { var v: InkPageView? = null }

@Composable
fun CanvasScreen(vm: AppViewModel, onPickPdf: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val nb = vm.current

    if (nb == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Sym("edit_note", 56.dp, cs.outline)
            Txt("Açık defter yok", TS.titleLg, cs.onSurface, Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Kitaplığa Git", cs.primary, cs.onPrimary, style = TS.labelLg, onClick = { vm.route = Route.LIBRARY })
                Pill("PDF İçe Aktar", cs.secondaryContainer, cs.onSecondaryContainer, style = TS.labelLg, icon = "picture_as_pdf", onClick = onPickPdf)
            }
        }
        return
    }

    val tone = PaperTones[nb.paperTone.coerceIn(0, PaperTones.lastIndex)]
    val pageCount = if (nb.kind == NotebookKind.PDF) (vm.pdf?.pageCount ?: nb.pageCount) else nb.pageCount
    val listState = rememberLazyListState()

    LaunchedEffect(listState, nb.id) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { vm.activePage = it }
    }
    LaunchedEffect(vm.scrollRequest, pageCount) {
        vm.scrollRequest?.let { if (it in 0 until pageCount) { listState.scrollToItem(it); vm.scrollRequest = null } }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        AppBar(vm, nb, pageCount)
        Box(Modifier.height(16.dp))
        Toolbar(vm, tone.third)
        Box(Modifier.height(16.dp))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ThumbStrip(vm, nb, pageCount, tone.second, tone.third)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (nb.kind == NotebookKind.PDF && vm.pdfAspects == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(), state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(pageCount, key = { "${nb.id}:$it" }) { i ->
                            val aspect = if (nb.kind == NotebookKind.PDF) vm.pdfAspects?.getOrNull(i) ?: (1f / 1.4142f) else nb.paperSize.ratio
                            PageItem(vm, nb, i, aspect, tone.second, tone.third)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ üst bar

@Composable
private fun AppBar(vm: AppViewModel, nb: Notebook, pageCount: Int) {
    val cs = MaterialTheme.colorScheme
    val saved = remember(nb.updatedAt) { SimpleDateFormat("HH:mm", Locale("tr")).format(Date(nb.updatedAt)) }
    Row(
        Modifier.fillMaxWidth().shadow(1.dp, rr(32)).background(cs.surfaceContainerLow, rr(32)).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBtn("arrow_back", icon = 22.dp) { vm.route = Route.LIBRARY }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(nb.title, TS.titleMd, cs.onSurface, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Sym("bookmark", 20.dp, cs.tertiary, true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Sym("auto_stories", 14.dp, cs.secondary)
                        Txt("Sayfa ${(vm.activePage + 1).coerceAtMost(pageCount)} / $pageCount", TS.labelSm, cs.secondary, weight = FontWeight.Medium)
                    }
                    Box(Modifier.size(4.dp).background(cs.outlineVariant, FullShape))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Sym("cloud_done", 13.dp, cs.primary)
                        Txt("Kaydedildi ($saved)", TS.labelSm, cs.secondary)
                    }
                }
            }
        }
        Row(
            Modifier.shadow(0.dp).background(cs.surfaceContainer, FullShape).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBtn("undo", box = 32.dp, tint = if (vm.canUndo) cs.onSurface else cs.onSurface.copy(alpha = .35f)) { vm.undo() }
            IconBtn("redo", box = 32.dp, tint = if (vm.canRedo) cs.onSurface else cs.onSurface.copy(alpha = .35f)) { vm.redo() }
            Box(Modifier.width(1.dp).height(20.dp).background(cs.outlineVariant))
            Row(Modifier.background(cs.surfaceContainerLowest, FullShape).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym("zoom_in", 16.dp, cs.secondary)
                Txt(" %100", TS.labelMd, cs.onSurface, weight = FontWeight.SemiBold)
            }
            IconBtn("fullscreen", box = 32.dp, icon = 18.dp) { vm.message = "Tam ekran için sistem gezinme çubuğunu gizleyin." }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.tap(cs.secondaryContainer, FullShape) { vm.stylusOnly = !vm.stylusOnly }.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sym("stylus", 16.dp, cs.onSecondaryContainer)
                    Txt("Ink", TS.labelSm, cs.onSecondaryContainer, weight = FontWeight.SemiBold)
                }
                Box(Modifier.width(4.dp).height(12.dp).alpha(.4f).background(cs.secondary, FullShape))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Sym("touch_app", 14.dp, cs.onSecondaryContainer)
                    Txt(if (vm.stylusOnly) "Palm Koruması" else "Parmakla Çiz", TS.labelSm, cs.onSecondaryContainer)
                }
            }
            IconBtn("grid_view", bg = cs.surfaceContainerHigh, tint = cs.onSurface) { vm.route = Route.PAGES }
            Row(
                Modifier.shadow(1.dp, FullShape).tap(cs.primary, FullShape) { vm.message = "Dışa aktarma bir sonraki sürümde." }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Sym("ios_share", 18.dp, cs.onPrimary)
                Txt("Dışa Aktar", TS.labelMd, cs.onPrimary)
            }
        }
    }
}

// ------------------------------------------------------------------ araç çubuğu

private val toolList = listOf(
    Triple(Tool.PEN, "ink_pen", "Dolma Kalem"), Triple(Tool.BALL, "edit", "Tükenmez"), Triple(Tool.BRUSH, "brush", "Fırça"),
    Triple(Tool.HIGHLIGHT, "highlight", "Fosforlu"), Triple(Tool.ERASER, "ink_eraser", "Silgi"), Triple(Tool.LASSO, "gesture", "Kement"),
    Triple(Tool.SHAPE, "category", "Şekil"), Triple(Tool.TEXT, "title", "Metin"), Triple(Tool.STICKER, "sticky_note_2", "Çıkartma"),
)

@Composable
private fun Toolbar(vm: AppViewModel, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    var picker by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().shadow(3.dp, FullShape).background(cs.surfaceContainer, FullShape).padding(8.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            toolList.forEach { (t, icon, label) ->
                val active = vm.tool == t
                Box(
                    Modifier.size(if (active) 48.dp else 44.dp, 44.dp).tap(if (active) cs.secondaryContainer else Color.Transparent, FullShape) {
                        vm.tool = t
                        if (t == Tool.SHAPE || t == Tool.TEXT || t == Tool.STICKER) vm.message = "$label aracı bir sonraki sürümde."
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(icon, 22.dp, if (active) cs.onSecondaryContainer else cs.onSurfaceVariant, active)
                    if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(10.dp, 4.dp).background(cs.primary, FullShape))
                }
            }
        }
        Row(Modifier.background(cs.surfaceContainerLow, FullShape).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(6.dp, 10.dp, 16.dp).forEachIndexed { i, d ->
                val active = vm.widthIdx == i
                Box(Modifier.size(32.dp).tap(if (active) cs.surfaceContainerHighest else Color.Transparent, FullShape) { vm.widthIdx = i }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(d).background(if (active) cs.primary else cs.onSurface, FullShape))
                }
            }
        }
        Row(Modifier.background(cs.surfaceContainerLow, FullShape).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            vm.inkColors.forEachIndexed { i, c ->
                val shown = if (i == 0 && dark) 0xFFF4EFF7.toInt() else c
                val active = vm.colorIdx == i
                Box(
                    Modifier.size(if (active) 28.dp else 24.dp).scale(if (active) 1.1f else 1f)
                        .then(if (active) Modifier.border(BorderStroke(2.dp, cs.primary), FullShape).padding(3.dp) else Modifier)
                        .background(Color(shown), FullShape).clickable { vm.colorIdx = i },
                )
            }
            IconBtn("palette", box = 28.dp, icon = 20.dp) { picker = true }
        }
    }
    if (picker) ColorPickerDialog({ picker = false }) { c -> vm.inkColors[vm.colorIdx] = c; picker = false }
}

private val extraColors = listOf(
    0xFF000000, 0xFF424242, 0xFF9E9E9E, 0xFFFFFFFF, 0xFFD32F2F, 0xFFE64A19, 0xFFF57C00, 0xFFFBC02D,
    0xFF388E3C, 0xFF00796B, 0xFF0288D1, 0xFF1976D2, 0xFF303F9F, 0xFF7B1FA2, 0xFFC2185B, 0xFF795548,
).map { it.toInt() }

@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Renk Seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extraColors.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c -> Box(Modifier.size(32.dp).border(1.dp, Color(0x33000000), FullShape).background(Color(c), FullShape).clickable { onPick(c) }) }
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Kapat") } },
    )
}

// ------------------------------------------------------------------ sol küçük resim şeridi

@Composable
private fun ThumbStrip(vm: AppViewModel, nb: Notebook, pageCount: Int, toneArgb: Int, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(128.dp).fillMaxHeight().shadow(1.dp, rr(32)).background(cs.surfaceContainerLow, rr(32)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt("SAYFALAR", TS.labelSm, cs.onSurfaceVariant, weight = FontWeight.SemiBold)
            Sym("tune", 16.dp, cs.secondary)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(pageCount, key = { it }) { i ->
                val active = vm.activePage == i
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f / 4f).shadow(if (active) 3.dp else 1.dp, rr(6)).clip(rr(6)).background(Color(toneArgb))
                        .then(if (active) Modifier.border(2.dp, cs.primary, rr(6)) else Modifier)
                        .clickable { vm.scrollRequest = i },
                ) {
                    if (nb.kind == NotebookKind.PDF) {
                        val pdf = vm.pdf
                        val bmp by produceState<Bitmap?>(null, pdf, i) { value = pdf?.render(i, 240) }
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low) }
                    } else {
                        PaperCanvas(nb.templateOf(i), dark, Modifier.fillMaxSize())
                    }
                    Text("${i + 1}", Modifier.align(Alignment.BottomEnd).padding(5.dp), color = if (active) cs.primary else Color(0xFF767684),
                        style = TS.labelSm.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium))
                }
            }
        }
        if (nb.kind == NotebookKind.NOTEBOOK) {
            Row(
                Modifier.fillMaxWidth().tap(cs.surfaceContainer, rr(6)) { vm.addPage(); vm.scrollRequest = nb.pageCount }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("add", 18.dp, cs.onSurfaceVariant)
                Txt(" Ekle", TS.labelSm, cs.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ tek sayfa (kağıt / PDF + Ink katmanı)

@Composable
private fun PageItem(vm: AppViewModel, nb: Notebook, index: Int, aspect: Float, toneArgb: Int, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(nb.id, index) { vm.ensurePage(index) }
    val data = vm.pageData[index]
    val isPdf = nb.kind == NotebookKind.PDF
    val shape = if (isPdf) rr(6) else rr(32)

    BoxWithConstraints(Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.roundToInt()
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).shadow(4.dp, shape).clip(shape).background(Color(if (isPdf) 0xFFFFFFFF.toInt() else toneArgb))) {
            if (isPdf) {
                val pdf = vm.pdf
                val bmp by produceState<Bitmap?>(null, pdf, index, widthPx) { value = pdf?.render(index, (widthPx * 1.5f).roundToInt()) }
                val b = bmp
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                else CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
            } else {
                PaperCanvas(nb.templateOf(index), dark, Modifier.fillMaxSize())
            }
            if (data != null) InkLayer(vm, index, data, dark)
        }
    }
}

@Composable
private fun InkLayer(vm: AppViewModel, index: Int, data: List<StrokeData>, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    val holder = remember { ViewHolder() }
    var sel by remember { mutableStateOf<RectF?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> InkPageView(ctx).also { holder.v = it } },
            modifier = Modifier.fillMaxSize(),
            update = { v ->
                v.listener = object : InkPageView.Listener {
                    override fun onChanged(before: List<StrokeData>, after: List<StrokeData>) { vm.commit(index, before, after) }
                    override fun onSelection(bounds: RectF?) { sel = bounds?.let { RectF(it) } }
                }
                v.tool = vm.tool
                v.colorArgb = if (vm.colorIdx == 0 && dark) 0xFFF4EFF7.toInt() else vm.inkColors[vm.colorIdx.coerceIn(0, vm.inkColors.lastIndex)]
                v.widthDp = vm.widthsDp[vm.widthIdx.coerceIn(0, 2)]
                v.stylusOnly = vm.stylusOnly
                v.setData(data)
            },
        )
        sel?.let { r ->
            Row(
                Modifier.offset { IntOffset(r.left.roundToInt().coerceAtLeast(0), (r.top - 56.dp.toPx()).roundToInt().coerceAtLeast(0)) }
                    .shadow(4.dp, FullShape).background(cs.surfaceContainerHighest, FullShape).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sil", Modifier.clickable { holder.v?.deleteSelection() }, color = cs.error, style = TS.labelSm)
                Text("|", color = cs.outlineVariant, style = TS.labelSm)
                Text("Seçimi Kaldır", Modifier.clickable { holder.v?.clearSelection() }, color = cs.onSurface, style = TS.labelSm)
            }
        }
    }
}
