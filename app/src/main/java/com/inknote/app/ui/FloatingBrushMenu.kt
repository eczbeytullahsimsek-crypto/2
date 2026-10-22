package com.inknote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.AppViewModel
import com.inknote.app.ink.Tool
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

data class BrushTypeInfo(
    val tool: Tool,
    val icon: String,
    val title: String,
    val subtitle: String,
    val stockBrushFamily: String,
)

val BrushTypes = listOf(
    BrushTypeInfo(Tool.PEN, "ink_pen", "Dolma Kalem", "Basınçlı & Dinamik", "StockBrushes.pressurePen"),
    BrushTypeInfo(Tool.BALL, "edit", "Tükenmez Kalem", "Düzgün & Sabit Çizgi", "StockBrushes.marker"),
    BrushTypeInfo(Tool.BRUSH, "brush", "Sanat Fırçası", "Geniş Kaligrafi", "StockBrushes.pressurePen (Calligraphy)"),
    BrushTypeInfo(Tool.HIGHLIGHT, "highlight", "Fosforlu Kalem", "Yarı Saydam Vurgu", "StockBrushes.highlighter"),
    BrushTypeInfo(Tool.ERASER, "ink_eraser", "Silgi", "Hassas Vuruş Silme", "Vektör Silgi"),
    BrushTypeInfo(Tool.LASSO, "gesture", "Kement", "Seçim & Taşıma", "Geometrik Kement"),
)

@Composable
fun FloatingBrushMenu(
    vm: AppViewModel,
    dark: Boolean,
    onOpenColorPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current

    // Sürüklenebilir konum durumu
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isExpanded by remember { mutableStateOf(false) }

    val activeBrushInfo = remember(vm.tool) {
        BrushTypes.firstOrNull { it.tool == vm.tool } ?: BrushTypes[0]
    }

    val activeColor = if (vm.colorIdx == 0 && dark) 0xFFF4EFF7.toInt() else vm.inkColors[vm.colorIdx.coerceIn(0, vm.inkColors.lastIndex)]

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        if (!isExpanded) {
            // ---- KÜÇÜLTÜLMÜŞ YÜZEN KAPSÜL (COMPACT PILL) ----
            Row(
                Modifier
                    .shadow(6.dp, FullShape)
                    .clip(FullShape)
                    .background(cs.surfaceContainerHigh)
                    .border(BorderStroke(1.5.dp, cs.outlineVariant.copy(alpha = 0.6f)), FullShape)
                    .clickable { isExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Sym("drag_indicator", 16.dp, cs.outline)

                // Aktif araç ikonu ve rengi
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(FullShape)
                        .background(cs.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Sym(activeBrushInfo.icon, 18.dp, cs.onSecondaryContainer)
                }

                // Renk ve kalınlık özeti
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(FullShape)
                            .background(Color(activeColor))
                            .border(BorderStroke(1.dp, cs.outlineVariant), FullShape)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f dp", vm.strokeWidthDp),
                        style = TS.labelSm,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                }

                // Basınç hassasiyeti rozeti
                Box(
                    Modifier
                        .clip(FullShape)
                        .background(cs.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Basınç %${(vm.pressureSensitivity * 100).roundToInt()}",
                        style = TS.labelSm,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onPrimaryContainer
                    )
                }

                Sym("expand_more", 18.dp, cs.onSurfaceVariant)
            }
        } else {
            // ---- GENİŞLETİLMİŞ ZENGİN FIRÇA MENÜSÜ (FULL FLOATING PANEL) ----
            Column(
                Modifier
                    .widthIn(min = 340.dp, max = 390.dp)
                    .shadow(12.dp, rr(24))
                    .clip(rr(24))
                    .background(cs.surfaceContainerHigh)
                    .border(BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.35f)), rr(24))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Üst Başlık & Tutamaç (Drag Handle & Close)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Sym("drag_indicator", 20.dp, cs.outline)
                        Column {
                            Text(
                                text = "Mürekkep Araçları",
                                style = TS.titleSm,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Text(
                                text = "androidx.ink.brush V1",
                                style = TS.labelSm,
                                fontSize = 10.sp,
                                color = cs.primary
                            )
                        }
                    }

                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(FullShape)
                            .background(cs.surfaceContainerHighest)
                            .clickable { isExpanded = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Sym("close", 16.dp, cs.onSurfaceVariant)
                    }
                }

                // 2. Canlı Vuruş & Basınç Önizleme Kartı
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(rr(14))
                        .background(cs.surfaceContainerLowest)
                        .border(BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f)), rr(14))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiveBrushPressurePreview(
                        tool = vm.tool,
                        colorArgb = activeColor,
                        strokeWidthDp = vm.strokeWidthDp,
                        pressureSensitivity = vm.pressureSensitivity,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 3. Fırça Türleri (androidx.ink.brush Seçici)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FIRÇA TÜRÜ",
                        style = TS.labelSm,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BrushTypes.chunked(2).forEach { rowTools ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowTools.forEach { b ->
                                    val isCurrent = vm.tool == b.tool
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .clip(rr(12))
                                            .background(if (isCurrent) cs.primaryContainer else cs.surfaceContainer)
                                            .border(
                                                BorderStroke(
                                                    if (isCurrent) 1.5.dp else 1.dp,
                                                    if (isCurrent) cs.primary else Color.Transparent
                                                ),
                                                rr(12)
                                            )
                                            .clickable { vm.tool = b.tool }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(30.dp)
                                                .clip(FullShape)
                                                .background(if (isCurrent) cs.primary else cs.surfaceContainerHighest),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Sym(b.icon, 18.dp, if (isCurrent) cs.onPrimary else cs.onSurfaceVariant)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = b.title,
                                                style = TS.labelSm,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) cs.onPrimaryContainer else cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = b.subtitle,
                                                style = TS.labelSm,
                                                fontSize = 9.sp,
                                                color = if (isCurrent) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.outline,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Çizgi Kalınlığı (Boyut) Kontrolü
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KALINLIK (BOYUT)",
                            style = TS.labelSm,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f dp", vm.strokeWidthDp),
                            style = TS.labelSm,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }

                    // Hızlı hazır kalınlık butonları
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(0.8f, 1.4f, 2.4f, 4.0f, 6.0f, 10.0f)
                        presets.forEach { w ->
                            val active = kotlin.math.abs(vm.strokeWidthDp - w) < 0.25f
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(FullShape)
                                    .background(if (active) cs.primaryContainer else cs.surfaceContainer)
                                    .border(
                                        BorderStroke(if (active) 1.5.dp else 1.dp, if (active) cs.primary else Color.Transparent),
                                        FullShape
                                    )
                                    .clickable { vm.setStrokeWidth(w) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size((w * 1.6f).coerceIn(4f, 20f).dp)
                                        .clip(FullShape)
                                        .background(if (active) cs.primary else cs.onSurfaceVariant)
                                )
                            }
                        }
                    }

                    // Kalınlık kaydırıcısı
                    Slider(
                        value = vm.strokeWidthDp,
                        onValueChange = { vm.setStrokeWidth(it) },
                        valueRange = 0.5f..20f,
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }

                // 5. Basınç Ayarı (Basınç Hassasiyeti - Pressure Sensitivity)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Sym("speed", 16.dp, cs.primary)
                            Text(
                                text = "BASINÇ HASSASİYETİ",
                                style = TS.labelSm,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "%${(vm.pressureSensitivity * 100).roundToInt()}",
                            style = TS.labelSm,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }

                    // Durum açıklaması etiketi
                    val pressureLabel = when {
                        vm.pressureSensitivity < 0.85f -> "Sert (Güçlü Basınç İster)"
                        vm.pressureSensitivity > 1.25f -> "Yumuşak (Hafif Dokunuşa Duyarlı)"
                        else -> "Dengeli (Varsayılan)"
                    }
                    Text(
                        text = pressureLabel,
                        style = TS.labelSm,
                        fontSize = 10.sp,
                        color = cs.outline
                    )

                    // Basınç hazır modları
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val pressurePresets = listOf(
                            Triple("Sert (%60)", 0.6f, "Güçlü"),
                            Triple("Standart (%100)", 1.0f, "Dengeli"),
                            Triple("Duyarlı (%160)", 1.6f, "Hassas"),
                        )
                        pressurePresets.forEach { (label, value, tag) ->
                            val active = kotlin.math.abs(vm.pressureSensitivity - value) < 0.1f
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(rr(8))
                                    .background(if (active) cs.primaryContainer else cs.surfaceContainer)
                                    .border(BorderStroke(if (active) 1.5.dp else 1.dp, if (active) cs.primary else Color.Transparent), rr(8))
                                    .clickable { vm.setPressure(value) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = TS.labelSm,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) cs.onPrimaryContainer else cs.onSurface
                                )
                            }
                        }
                    }

                    // Basınç Kaydırıcısı (0.4x .. 2.0x)
                    Slider(
                        value = vm.pressureSensitivity,
                        onValueChange = { vm.setPressure(it) },
                        valueRange = 0.4f..2.0f,
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }

                // 6. Mürekkep Renk Paleti
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MÜREKKEP RENGİ",
                            style = TS.labelSm,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Box(
                            Modifier
                                .clip(FullShape)
                                .background(cs.surfaceContainerHighest)
                                .clickable { onOpenColorPicker() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Sym("palette", 13.dp, cs.primary)
                                Text("Tüm Renkler", style = TS.labelSm, fontSize = 10.sp, color = cs.primary)
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        vm.inkColors.forEachIndexed { i, c ->
                            val shown = if (i == 0 && dark) 0xFFF4EFF7.toInt() else c
                            val active = vm.colorIdx == i
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .scale(if (active) 1.15f else 1f)
                                    .clip(FullShape)
                                    .background(Color(shown))
                                    .border(
                                        BorderStroke(
                                            if (active) 2.5.dp else 1.dp,
                                            if (active) cs.primary else Color(0x33000000)
                                        ),
                                        FullShape
                                    )
                                    .clickable { vm.selectColor(c) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (active) {
                                    Sym("check", 16.dp, if (shown == 0xFFFFFFFF.toInt()) Color.Black else Color.White)
                                }
                            }
                        }
                    }
                }

                // 7. Stylus Palm Rejection (Yalnızca Kalem Modu)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(rr(12))
                        .background(if (vm.stylusOnly) cs.primaryContainer else cs.surfaceContainer)
                        .clickable { vm.stylusOnly = !vm.stylusOnly }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sym("stylus", 18.dp, if (vm.stylusOnly) cs.primary else cs.onSurfaceVariant)
                        Column {
                            Text(
                                text = "Yalnızca Kalemle Çiz",
                                style = TS.labelSm,
                                fontWeight = FontWeight.Bold,
                                color = if (vm.stylusOnly) cs.onPrimaryContainer else cs.onSurface
                            )
                            Text(
                                text = "Parmak yalnızca sayfayı kaydırır ve zoom yapar",
                                style = TS.labelSm,
                                fontSize = 10.sp,
                                color = if (vm.stylusOnly) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.outline
                            )
                        }
                    }

                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(FullShape)
                            .background(if (vm.stylusOnly) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.stylusOnly) Sym("check", 14.dp, cs.onPrimary)
                    }
                }
            }
        }
    }
}

/**
 * Dinamik Basınç & Kalınlık Canlı Önizleme Çizimi.
 * Kullanıcı basınç hassasiyetini veya fırça türünü değiştirdiğinde, vuruşun
 * hafif dokunuştan güçlü dokunuşa nasıl kalınlaştığını görsel olarak gösterir.
 */
@Composable
private fun LiveBrushPressurePreview(
    tool: Tool,
    colorArgb: Int,
    strokeWidthDp: Float,
    pressureSensitivity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val baseWidthPx = strokeWidthDp * density
        val alpha = if (tool == Tool.HIGHLIGHT) 0.45f else 1.0f
        val color = Color(colorArgb).copy(alpha = alpha)

        val segments = 40
        var prevX = w * 0.08f
        var prevY = h * 0.5f

        for (i in 1..segments) {
            val frac = i.toFloat() / segments
            val currX = w * 0.08f + (w * 0.84f) * frac
            // S-curve şekli
            val wave = kotlin.math.sin(frac * Math.PI.toFloat() * 1.5f)
            val currY = h * 0.5f + wave * (h * 0.28f)

            // Simüle edilen basınç eğrisi (hafiften güçlüye doğru artan ve hassasiyet çarpanı ile)
            val simulatedPressure = frac
            val pPower = 1.0 / pressureSensitivity.toDouble().coerceIn(0.2, 3.0)
            val dynamicFactor = if (tool == Tool.PEN || tool == Tool.BRUSH) {
                simulatedPressure.toDouble().pow(pPower).toFloat().coerceIn(0.2f, 1.6f)
            } else if (tool == Tool.HIGHLIGHT) {
                2.8f
            } else {
                1.0f // Marker: sabit kalınlık
            }

            val currentStrokeWidth = (baseWidthPx * dynamicFactor).coerceAtLeast(1.5f)

            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(prevX, prevY),
                end = androidx.compose.ui.geometry.Offset(currX, currY),
                strokeWidth = currentStrokeWidth,
                cap = StrokeCap.Round
            )

            prevX = currX
            prevY = currY
        }
    }
}
