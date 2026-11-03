package app.orbit.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Where the owner's Home Assistant is, and the token to read it with, typed on the television.
 *
 * On the set rather than pushed in over adb, because adb is not something most owners of a
 * television have, and a setting only the author can reach is not a setting. Long tokens are
 * pasted from the phone app rather than typed out.
 *
 * The token is written and never read back: once it is saved the field shows that there is
 * one, not what it is. A credential in large type on a television is a credential in every
 * photograph of that television.
 */
class HassSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { HassSetupScreen(onDone = { finish() }) }
    }
}

@Composable
private fun HassSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val saved = remember(HomeAssistantStore.revision) { HomeAssistantStore.configured(context) }
    var url by remember { mutableStateOf(HomeAssistantStore.baseUrl(context).orEmpty()) }
    // Never seeded from what is stored. There is no accessor that would let it be.
    var token by remember { mutableStateOf("") }
    var complaint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = SafeH, vertical = SafeV)
    ) {
        Text("Home Assistant", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        Text(
            "The address of your Home Assistant and a long-lived access token. Both stay on " +
                "this television. Orbit only reads, and sends nothing anywhere else.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))

        Field(
            label = "Address",
            hint = "http://homeassistant.local:8123",
            value = url,
            onValue = { url = it; complaint = null },
            modifier = Modifier.focusRequester(first),
        )
        Spacer(Modifier.height(14.dp))
        Field(
            label = "Token",
            hint = if (saved) "Saved. Type a new one to replace it" else "Paste the long-lived token",
            value = token,
            onValue = { token = it; complaint = null },
            // Dots, not the token. The phone app is how a long one gets here, so nothing is
            // lost by never showing it back.
            masked = true,
        )

        complaint?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFF8A6B), fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Button("Save") {
                val wanted = url.trim()
                when {
                    !HomeAssistantStore.looksLikeUrl(wanted) ->
                        complaint = "The address needs to start with http:// or https://"
                    token.isBlank() && !saved ->
                        complaint = "A token is needed before Orbit can read anything"
                    else -> {
                        // An empty token with one already saved means "leave it alone", so a
                        // changed address does not cost the token that was typed on a remote.
                        HomeAssistantStore.save(
                            context,
                            wanted,
                            token.ifBlank { HomeAssistantStore.token(context).orEmpty() },
                        )
                        onDone()
                    }
                }
            }
            if (saved) {
                Spacer(Modifier.width(12.dp))
                Button("Forget") {
                    HomeAssistantStore.clear(context)
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
) {
    val accent = Accent
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (masked) KeyboardType.Password else KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            modifier = modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1E2838))
                .border(2.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(6.dp))
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(hint, color = Muted, fontSize = 15.sp)
                    // Dots while typing too: someone reading over a shoulder is the same
                    // problem as a photograph.
                    if (masked && value.isNotEmpty()) {
                        Text("*".repeat(value.length.coerceAtMost(24)), color = Color.White, fontSize = 16.sp)
                    } else {
                        field()
                    }
                }
            },
        )
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (focused) Color.Black else Color.White,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Accent else Color(0xFF2A3444))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
