package com.screenusdeletus.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ui.theme.Brand

/**
 * The brand mark: a phone outline struck through by a single deliberate line, inside a soft
 * rounded square.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    // Fixed brand colours (same as the launcher icon) so the mark looks identical in both themes.
    container: Color = Color(0xFF1F5C4D),
    ink: Color = Color(0xFFF2FBF6),
    accent: Color = Color(0xFFFFC98A),
) {
    Canvas(modifier.size(size).semantics { contentDescription = "Screenus Deletus logo" }) {
        val s = this.size.minDimension
        drawRoundRect(container, cornerRadius = CornerRadius(s * 0.28f))
        val w = s * 0.34f
        val h = s * 0.56f
        val left = (s - w) / 2
        val top = (s - h) / 2
        val stroke = s * 0.06f
        drawRoundRect(ink, Offset(left, top), Size(w, h), CornerRadius(s * 0.07f), style = Stroke(stroke))
        drawLine(ink, Offset(s / 2 - s * 0.04f, top + h - s * 0.09f), Offset(s / 2 + s * 0.04f, top + h - s * 0.09f), stroke * 0.8f, StrokeCap.Round)
        drawLine(accent, Offset(s * 0.22f, s * 0.78f), Offset(s * 0.78f, s * 0.22f), stroke * 1.4f, StrokeCap.Round)
    }
}

/** Gradient hero panel used at the top of the dashboard and in onboarding. */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val extra = Brand.extra
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Small uppercase label above a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/** Screen title with an optional subtitle. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, eyebrow: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A compact metric with an icon in a tonal circle. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pill-shaped badge. */
@Composable
fun Badge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Thick rounded progress bar; turns red when over. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, over: Boolean = fraction >= 1f, height: Dp = 10.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "meter")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (animated > 0f) drawRoundRect(color, size = Size(size.width * animated, size.height), cornerRadius = r)
    }
}

/** Row of dots showing progress through a multi-step flow. */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateFloatAsState(if (i == current) 22f else 8f, label = "dot")
            Box(
                Modifier.height(8.dp).width(w.dp).clip(CircleShape).background(
                    if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

/**
 * Slow breathing guide (4 s in, 4 s out). Shown on block screens: the most useful thing to do
 * in the moment you reach for a blocked app is to pause.
 */
@Composable
fun BreathingCircle(modifier: Modifier = Modifier, color: Color, size: Dp = 220.dp, center: @Composable () -> Unit = {}) {
    val t = rememberInfiniteTransition(label = "breath")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    // 0..0.5 inhale, 0.5..1 exhale, eased
    val p = if (phase < 0.5f) phase * 2 else (1 - phase) * 2
    val eased = FastOutSlowInEasing.transform(p)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val maxR = this.size.minDimension / 2
                drawCircle(color.copy(alpha = 0.08f), radius = maxR)
                drawCircle(color.copy(alpha = 0.14f), radius = maxR * (0.55f + 0.45f * eased))
                drawCircle(color.copy(alpha = 0.22f), radius = maxR * (0.42f + 0.33f * eased))
                drawCircle(color.copy(alpha = 0.9f), radius = maxR * 0.36f, style = Stroke(3.dp.toPx()))
            }
            center()
        }
        Text(
            if (phase < 0.5f) "Breathe in…" else "Breathe out…",
            style = MaterialTheme.typography.titleMedium,
            color = color,
        )
    }
}

/** PIN entry as dots plus a large numeric keypad. */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = com.screenusdeletus.security.AuthManager.MAX_PIN_LENGTH,
    error: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // One dot per digit typed — no placeholders, so the dots never hint at a PIN length.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(16.dp).semantics { contentDescription = "${pin.length} digits entered" },
        ) {
            repeat(pin.length) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (k.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .let { m ->
                                if (k.isEmpty()) m else m.clickable {
                                    when (k) {
                                        "⌫" -> onPinChange(pin.dropLast(1))
                                        else -> if (pin.length < maxLength) onPinChange(pin + k)
                                    }
                                }
                            }
                            .semantics { if (k == "⌫") contentDescription = "Delete" },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                            "" -> Unit
                            else -> Text(k, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** Square wrapper keeping charts proportionate. */
fun Modifier.square() = this.aspectRatio(1f)
