package app.orbit.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/** The most dots shown at once: the one on screen and two either side, as on the stock home. */
internal const val DotsShown = 5

/** Names the dots, so a test can find them. */
internal const val HeroDotsTag = "orbit.hero.dots"

/** How long the strip takes to slide one place. */
private const val SlideMs = 300

/** Centre to centre. */
private val DotPitch = 14.dp

/** Diameter by distance from the one on screen: the middle biggest, the outer ones smallest. */
private val DotSizes = listOf(9.dp, 6.dp, 4.dp)

/**
 * How many dots sit left and right of the one on screen, which always stays in the middle.
 *
 * Up to [DotsShown], one per hero. An even count puts the spare dot on the right, the side the
 * cycle moves towards. Above that the strip is a window onto a longer list: the ambient pool
 * runs to [Backdrops.MAX_PICTURES], and sixty dots would say nothing a window does not.
 */
internal fun dotSpan(count: Int): Pair<Int, Int> {
    val shown = count.coerceAtMost(DotsShown)
    if (shown < 2) return 0 to 0
    val left = (shown - 1) / 2
    return left to shown - 1 - left
}

/**
 * Which way the strip slides from [from] to [to]: 1 onwards, -1 back, 0 for anything that is
 * not one step, such as a new list. With two entries both ways land on the same one, and
 * onwards wins, since that is the way the cycle runs by itself.
 */
internal fun slideOf(from: Int, to: Int, count: Int): Int = when {
    count < 2 || from == to -> 0
    stepTo(from, 1, count) == to -> 1
    stepTo(from, -1, count) == to -> -1
    else -> 0
}

/** A dot's diameter in dp at [distance] places from the middle, eased between the steps. */
internal fun dotDiameter(distance: Float): Float {
    val d = abs(distance).coerceAtMost((DotSizes.size - 1).toFloat())
    val i = d.toInt().coerceAtMost(DotSizes.size - 2)
    val f = d - i
    return DotSizes[i].value + (DotSizes[i + 1].value - DotSizes[i].value) * f
}

/** Fully there inside the span, fading over one place beyond it as the strip slides. */
internal fun dotAlpha(position: Float, left: Int, right: Int): Float = when {
    position < -left -> 1f - (-left - position)
    position > right -> 1f - (position - right)
    else -> 1f
}.coerceIn(0f, 1f)

/**
 * Where the hero is in what it steps through, under it, so a change of picture reads as one of
 * several rather than the screen changing its mind. Nothing for a single hero.
 */
@Composable
internal fun HeroDots(count: Int, at: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    val (left, right) = dotSpan(count)
    val shift = remember { Animatable(0f) }
    var from by remember { mutableIntStateOf(at) }
    var fromCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(at, count) {
        val slide = if (count == fromCount) slideOf(from, at, count) else 0
        from = at
        fromCount = count
        // Starts where the new one was, one place over, and slides it into the middle.
        shift.snapTo(slide.toFloat())
        shift.animateTo(0f, tween(SlideMs))
    }
    // Symmetric around the middle, with a place spare each side for the dot fading out.
    val reach = max(left, right) + 1
    Canvas(
        modifier
            .width(DotPitch * (2 * reach + 1))
            .height(DotSizes.first())
            .testTag(HeroDotsTag)
            .semantics { contentDescription = "${at + 1} of $count" }
    ) {
        val pitch = DotPitch.toPx()
        val middle = Offset(size.width / 2, size.height / 2)
        for (k in -left - 1..right + 1) {
            val position = k + shift.value
            val alpha = dotAlpha(position, left, right)
            if (alpha <= 0f) continue
            val distance = abs(position)
            // The one on screen in full white, the rest dimmer the further out they are.
            val bright = 1f - 0.45f * distance.coerceAtMost(1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha * bright),
                radius = dotDiameter(distance).dp.toPx() / 2,
                center = middle + Offset(position * pitch, 0f),
            )
        }
    }
}
