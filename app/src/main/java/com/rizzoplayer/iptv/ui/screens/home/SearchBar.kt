package com.rizzoplayer.iptv.ui.screens.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.ui.theme.*

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onVoiceResult: ((String) -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    downTarget: FocusRequester? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var focused by remember { mutableStateOf(false) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onVoiceResult?.invoke(spokenText)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .then(
                if (focused) Modifier.border(1.dp, AccentBorder, RoundedCornerShape(8.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .focusRequester(focusRequester)
            .then(
                if (downTarget != null) {
                    Modifier.focusProperties { this.down = downTarget }
                } else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", fontSize = 14.sp, color = if (focused) AccentBlue else TextMuted)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            cursorBrush = SolidColor(AccentBlue),
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            Text(
                "✕",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(4.dp)
            )
        }
        if (onVoiceResult != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "🎤",
                fontSize = 14.sp,
                color = if (focused) AccentBlue else TextMuted,
                modifier = Modifier
                    .clickable {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }
                        voiceLauncher.launch(intent)
                    }
                    .padding(4.dp)
            )
        }
    }
}
