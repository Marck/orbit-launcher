package app.orbit.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.reflect.KProperty

/** The parts that sit around the screen: notices, confirmations and the safe area. */

/**
 * The confirmation in the corner. Every time it is set counts as new, even to the same words, so
 * that removing two favorites in a row keeps the second one up for its full time.
 */
internal class Note {
    private var text by mutableStateOf<String?>(null)

    var shown by mutableIntStateOf(0)
        private set

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String? = text

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        text = value
        if (value != null) shown++
    }
}

@Composable
internal fun Confirmation(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = SafeH, vertical = SafeV)) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC1E2838))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * What is running on this television: the version, whether it is a release build, and the commit
 * it was made from.
 *
 * The commit is the part that matters. Two builds of one version are told apart by nothing else,
 * which is the question whenever something has been merged and the set has not been updated.
 *
 * There is nothing here to act on, so any key closes it rather than only Back.
 */
@Composable
internal fun AboutCard(onDismiss: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.focusWhenReady() }
    // The press that opened this can arrive here as a release, which would shut it again before
    // it was read. Only a key that goes down while the card is up counts, as the menu does.
    var armed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .focusRequester(requester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) armed = true
                if (armed && event.type == KeyEventType.KeyUp) onDismiss()
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF21E2838))
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Orbit", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(BuildInfo.line(), color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
            Text(BuildInfo.source(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(BuildInfo.release(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(BuildInfo.built(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Text("Any key to close", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun Notice(
    title: String,
    detail: String,
    requester: FocusRequester,
    above: FocusRequester,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .focusProperties { up = above }
            // Only as wide as the part of the screen the artwork does not reach, so the two
            // never sit on top of each other.
            .fillMaxWidth(NoticeWidth)
            .padding(start = SafeH, end = 18.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B1017))
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        Pill("Details", onDetails, Modifier.focusRequester(requester))
        Spacer(Modifier.width(9.dp))
        Pill("Dismiss", onDismiss)
    }
}

@Composable
internal fun Pill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (focused) Color.Black else OnSurface, fontSize = 12.sp)
    }
}

@Composable
internal fun RowLabel(text: String) {
    Text(text, color = Color(0x59FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = SafeH))
    Spacer(Modifier.height(8.dp))
}

/**
 * How far a row scrolls to show the tile that takes focus: only as far as it takes to bring the
 * tile inside the safe area, [margin] in from either edge of the screen.
 *
 * Left to itself, Compose scrolls a focused tile only until it touches the edge of the screen, in
 * the strip a TV may crop off. On a TV it does something else again: it holds the focused tile 30%
 * of the way along the row, so the row slides under it on nearly every press.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class SafeAreaScroll(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val beforeStart = offset - margin
        val pastEnd = offset + size - (containerSize - margin)
        return when {
            beforeStart < 0 -> beforeStart
            pastEnd > 0 -> pastEnd
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SafeAreaScrolling(content: @Composable () -> Unit) {
    val spec = with(LocalDensity.current) { remember(this) { SafeAreaScroll(SafeH.toPx()) } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
