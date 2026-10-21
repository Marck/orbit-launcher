package app.orbit.launcher

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SafeH = 48.dp
private val SafeV = 27.dp
private val Thumb = 96.dp

/** Saved big enough that the top bar stays sharp; drawn small for the grid. */
private const val SavedPixels = 256
private const val ThumbPixels = 192

private val OnSurface = Color(0xFFDDE5F0)
private val Muted = Color(0xFF8B93A4)

class AvatarPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PickerScreen(onDone = { finish() }) }
    }
}

@Composable
private fun PickerScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AvatarPicker.hasPermission(context)) }
    var rescan by remember { mutableStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        rescan++
    }

    val images by produceState(initialValue = emptyList<PickerImage>(), granted, rescan) {
        value = if (granted) withContext(Dispatchers.IO) { AvatarPicker.scan(context) } else emptyList()
    }
    val hasAvatar by produceState(initialValue = false, Profile.revision) {
        value = withContext(Dispatchers.IO) { Profile.hasAvatar(context) }
    }

    val firstTile = remember { FocusRequester() }
    var focusClaimed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(40) {
            if (runCatching { firstTile.requestFocus() }.isSuccess) {
                focusClaimed = true
                return@LaunchedEffect
            }
            delay(16)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("Profile picture", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick one of Orbit's own, or one of your pictures. It is copied to the TV and stays there.",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(Thumb + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = SafeV),
            modifier = Modifier.fillMaxWidth(),
        ) {
            header("ORBIT'S OWN")

            itemsIndexed(BuiltInAvatars.all, key = { _, it -> "built-${it.id}" }) { index, avatar ->
                BuiltInTile(
                    avatar = avatar,
                    requester = if (index == 0) firstTile else null,
                ) {
                    if (Profile.save(context, BuiltInAvatars.render(avatar, SavedPixels))) onDone()
                }
            }

            header("YOUR PICTURES")

            when {
                !granted -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "Orbit needs access to the TV's pictures to list them.",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        ActionButton("Allow access", autoFocus = false) {
                            request.launch(AvatarPicker.permission())
                        }
                    }
                }

                images.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { NoPicturesHelp() }

                else -> items(images, key = { "file-${it.name}" }) { image ->
                    Tile(label = image.name, requester = null, image = image) {
                        if (Profile.save(context, image.uri)) onDone()
                    }
                }
            }

            if (hasAvatar) {
                header("CURRENT")
                item(key = "none") {
                    Tile(label = "No picture", requester = null) {
                        Profile.clear(context)
                        onDone()
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Spacer(Modifier.height(4.dp))
            Text(text, color = Color(0x59FFFFFF), fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun NoPicturesHelp() {
    Column {
        Text("No pictures on this TV yet. Add one by:", color = OnSurface, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        listOf(
            "downloading a picture with the TV's web browser,",
            "plugging in a USB stick that has pictures on it,",
            "or copying one over adb into the Download folder.",
        ).forEach {
            Text("-  $it", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun BuiltInTile(
    avatar: BuiltInAvatars.BuiltInAvatar,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, avatar.id) {
        value = withContext(Dispatchers.IO) { BuiltInAvatars.render(avatar, ThumbPixels) }
    }
    TileBody(label = avatar.name, requester = requester, bitmap = bitmap, placeholder = null, onClick = onClick)
}

@Composable
private fun Tile(
    label: String,
    requester: FocusRequester?,
    image: PickerImage? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, image) {
        value = image?.let { withContext(Dispatchers.IO) { AvatarPicker.thumbnail(context, it) } }
    }
    TileBody(
        label = label,
        requester = requester,
        bitmap = bitmap,
        placeholder = if (image == null) "None" else null,
        onClick = onClick,
    )
}

@Composable
private fun TileBody(
    label: String,
    requester: FocusRequester?,
    bitmap: Bitmap?,
    placeholder: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(Thumb)
                .scale(if (focused) 1.06f else 1f)
                .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
                .clip(CircleShape)
                .background(Color(0xFF1E2838))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val shown = bitmap
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else if (placeholder != null) {
                Text(placeholder, color = OnSurface, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (focused) Color.White else Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionButton(label: String, autoFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        repeat(30) {
            if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(16)
        }
    }
    Box(
        Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (focused) Color.Black else OnSurface, fontSize = 14.sp)
    }
}
