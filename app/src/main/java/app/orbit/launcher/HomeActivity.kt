package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sizes come straight from section B of the requirements.
private val SafeH = 48.dp
private val SafeV = 27.dp
private val TileW = 111.dp
private val TileH = 66.dp
// Narrow enough that a typical set's inputs, plus the two fixed tiles, fit the safe area
// without the row scrolling. A scrolling row slides its first tile out of line with the apps
// above it, and slides its last tile up against the fixed tiles.
private val InputW = 100.dp
private val SquareW = 75.dp

/** Keeps a focused input tile clear of the fixed tiles even when the row does scroll. */
private val RowGap = 24.dp
private val AppsRowHeight = 86.dp

private val Surface = Color(0xFF14181F)
private val OnSurface = Color(0xFFDDE5F0)
private val Muted = Color(0xFF8B93A4)
private val LastInput = Color(0xFF1AA3C4)

/**
 * Counts returns to the launcher.
 *
 * Compose does not restore focus across an activity switch, so coming back from an app or from
 * the picker leaves nothing focused and the remote does nothing at all. The apps row watches this
 * and takes focus back.
 */
private object Returns {
    var count by mutableIntStateOf(0)
}

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomeScreen() }
    }

    override fun onResume() {
        super.onResume()
        Returns.count++
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    // Off the main thread, all of it. produceState runs its producer on the composition's
    // context, so reading every launcher activity and decoding its artwork there holds up the
    // first frame and the screen stays black until it finishes.
    val apps by produceState(initialValue = AppRepository.cached()) {
        value = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    val inputs by produceState(initialValue = emptyList<TvInput>()) {
        value = withContext(Dispatchers.IO) { InputRepository.load(context) }
    }
    val isDefaultHome by produceState(initialValue = true) {
        value = withContext(Dispatchers.IO) { AppRepository.isDefaultHome(context) }
    }
    var noticeDismissed by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(Modifier.padding(horizontal = SafeH, vertical = SafeV)) {
            TopBar()
            Spacer(Modifier.height(10.dp))
            Hero()
        }
        Spacer(Modifier.weight(1f))
        if (!isDefaultHome && !noticeDismissed) {
            Notice(
                title = "Orbit is not the home app yet",
                detail = "Set it as default so the home button opens Orbit",
                onDetails = { context.startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS)) },
                onDismiss = { noticeDismissed = true },
            )
            Spacer(Modifier.height(14.dp))
        }
        RowLabel("APPS")
        AppsRow(apps)
        Spacer(Modifier.height(20.dp))
        RowLabel("INPUTS")
        InputsRow(inputs)
    }
}

@Composable
private fun AppsRow(apps: List<LaunchableApp>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    // The tile to come back to. Focus is only ever requested from the effect below, never from a
    // tile's own composition, which is what used to yank focus back while arrowing left.
    var lastFocused by remember { mutableStateOf<String?>(null) }
    val firstPackage = apps.firstOrNull()?.packageName

    LaunchedEffect(firstPackage, Returns.count) {
        if (firstPackage == null) return@LaunchedEffect
        val target = lastFocused?.takeIf { wanted -> apps.any { it.packageName == wanted } }
            ?: firstPackage
        val index = apps.indexOfFirst { it.packageName == target }.coerceAtLeast(0)
        // The row only composes what is on screen, so the tile has to be scrolled into view
        // before its requester exists at all.
        runCatching { listState.scrollToItem(index) }
        // Items are placed during layout, so the requester is not attached on the frame the
        // list arrives. Keep asking until it takes.
        repeat(30) {
            if (runCatching { requesters[target]?.requestFocus() != null }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    LazyRow(
        // Fixed so the rows below hold still while the list is still loading.
        modifier = Modifier.height(AppsRowHeight),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = SafeH),
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppTile(
                app = app,
                requester = requesters.getOrPut(app.packageName) { FocusRequester() },
                onFocused = { lastFocused = app.packageName },
                onClick = {
                    AppRepository.launchIntent(context, app.packageName)
                        ?.let { context.startActivitySafely(it) }
                },
            )
        }
        if (apps.isNotEmpty()) item(key = "add-favourite") { PlusTile() }
    }
}

@Composable
private fun InputsRow(inputs: List<TvInput>) {
    val context = LocalContext.current
    val lastInput = remember(inputs) { InputRepository.lastUsedInput(context) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(start = SafeH),
        ) {
            itemsIndexed(inputs, key = { _, input -> input.id }) { _, input ->
                InputTile(
                    label = input.label,
                    sub = input.sub,
                    highlighted = input.id == lastInput,
                    dimmed = !input.connected,
                ) {
                    InputRepository.rememberUse(context, input.id)
                    context.startActivitySafely(InputRepository.switchIntent(input.id))
                }
            }
            item(key = "all-inputs") {
                InputTile(label = "All inputs", sub = null, highlighted = false, dimmed = false) {
                    InputRepository.allInputsIntent(context)?.let { context.startActivitySafely(it) }
                }
            }
        }
        Spacer(Modifier.width(RowGap))
        val storeIntent = remember { InputRepository.storeIntent(context) }
        val settingsIntent = remember { InputRepository.settingsIntent() }
        SquareTile(
            icon = remember { InputRepository.iconFor(context, storeIntent) },
            fallback = "Store",
        ) { storeIntent?.let { context.startActivitySafely(it) } }
        Spacer(Modifier.width(9.dp))
        SquareTile(
            icon = remember { InputRepository.iconFor(context, settingsIntent) },
            fallback = "Settings",
        ) { context.startActivitySafely(settingsIntent) }
        Spacer(Modifier.width(SafeH))
    }
}

@Composable
private fun TopBar() {
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            delay(10_000)
        }
    }
    val date = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(date.format(now), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Avatar()
        Spacer(Modifier.width(9.dp))
        Text(time.format(now), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Avatar() {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    // Keyed on the revision so a picture chosen in the picker shows on return.
    val picture by produceState<android.graphics.Bitmap?>(initialValue = null, Profile.revision) {
        value = withContext(Dispatchers.IO) { Profile.avatar(context) }
    }
    Box(
        Modifier
            .size(21.dp)
            .scale(if (focused) 1.15f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { context.startActivitySafely(Intent(context, AvatarPickerActivity::class.java)) }
            .clip(CircleShape)
            .background(Color(0xFF2B3242))
            // A faint ring always, so a dark picture still reads as a disc rather than a hole.
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x3DFFFFFF),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = picture
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Profile",
                tint = Color(0xFFBFC9DA),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Hero() {
    Column {
        Text("CONTINUE WATCHING", color = Color(0xFF7FD4FF), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("Night Watch", color = Color.White, fontSize = 39.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            "A maintenance crew on the late shift finds that the building keeps rearranging its own floors.",
            color = OnSurface,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("S2 E5 - THE SUB-LEVEL - 33 MIN LEFT", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(225.dp).height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.width(60.dp).height(2.dp).background(Color.White))
        }
    }
}

@Composable
private fun Notice(title: String, detail: String, onDetails: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xA60B1017))
            .padding(horizontal = SafeH, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        Pill("Details", onDetails)
        Spacer(Modifier.width(9.dp))
        Pill("Dismiss", onDismiss)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (focused) Color.Black else OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(text, color = Color(0x59FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = SafeH))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (focused) 1.06f else 1f)
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .focusable()
                .clickable(onClick = onClick)
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(app.packageName) {
                runCatching { app.art?.toBitmap()?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    contentScale = if (app.isBanner) ContentScale.Fit else ContentScale.Inside,
                    modifier = Modifier.fillMaxSize().padding(if (app.isBanner) 0.dp else 12.dp),
                )
            } else {
                Text(app.label, color = OnSurface, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (focused) app.label else "",
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlusTile() {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TileW, TileH)
                .scale(if (focused) 1.06f else 1f)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2838))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = Color(0xFF9FC3FF), fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(6.dp))
        Text(if (focused) "Add" else "", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun InputTile(
    label: String,
    sub: String?,
    highlighted: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(InputW, TileH)
            .scale(if (focused) 1.06f else 1f)
            .alpha(if (dimmed) 0.45f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) LastInput else Color(0xFF2A3444))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = if (sub == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            sub?.let {
                Text(
                    it,
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SquareTile(icon: Drawable?, fallback: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(SquareW, TileH)
            .scale(if (focused) 1.06f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A3444))
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = remember(icon) { runCatching { icon?.toBitmap()?.asImageBitmap() }.getOrNull() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = fallback,
                contentScale = ContentScale.Inside,
                modifier = Modifier.fillMaxSize().padding(14.dp),
            )
        } else {
            Text(fallback, color = Color.White, fontSize = 12.sp)
        }
    }
}

private fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
