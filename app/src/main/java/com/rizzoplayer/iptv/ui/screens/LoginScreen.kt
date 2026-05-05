package com.rizzoplayer.iptv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.R
import com.rizzoplayer.iptv.ui.theme.*
import com.rizzoplayer.iptv.ui.viewmodel.LoginUiState
import com.rizzoplayer.iptv.ui.viewmodel.LoginViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var url      by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoginSuccess()
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(500.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App logo
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Rizzo Player",
                modifier = Modifier.size(160.dp)
            )

            Text(
                text = "Connect to your IPTV server",
                fontSize = 16.sp,
                color = TextMuted
            )

            Spacer(Modifier.height(12.dp))

            // Server URL
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; viewModel.resetError() },
                label = { Text("Server URL") },
                placeholder = { Text("http://line.example.com") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() })
            )

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; viewModel.resetError() },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(userFocus)
                    .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() })
            )

            // Password (visible)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; viewModel.resetError() },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passFocus)
                    .onFocusChanged { if (it.isFocused) keyboardController?.hide() },
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.login(url, username, password)
                })
            )

            // Error message
            if (state is LoginUiState.Error) {
                Text(
                    text = (state as LoginUiState.Error).message,
                    color = RedColor,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { viewModel.login(url, username, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = state !is LoginUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                if (state is LoginUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect", fontSize = 18.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor      = TextPrimary,
    unfocusedTextColor    = TextPrimary,
    focusedBorderColor    = AccentBlue,
    unfocusedBorderColor  = BorderColor,
    focusedLabelColor     = AccentBlue,
    unfocusedLabelColor   = TextMuted,
    cursorColor           = AccentBlue,
    focusedPlaceholderColor   = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
)
