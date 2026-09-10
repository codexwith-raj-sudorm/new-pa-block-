package com.jarvis.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

val Bg = Color(0xFF0D1117)
val Panel = Color(0xFF161B22)
val Accent = Color(0xFF58A6FF)
val UserBlue = Color(0xFF1F6FEB)
val BotGray = Color(0xFF21262D)
val Muted = Color(0xFF8B949E)
val Good = Color(0xFF3FB950)
val Warn = Color(0xFFD29922)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                JarvisScreen()
            }
        }
    }
}

@Composable
fun JarvisScreen() {
    val context = LocalContext.current
    val vm: JarvisViewModel = viewModel(factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return JarvisViewModel(context.applicationContext as Application) as T
            }
        }
    })

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(online = vm.brainOk, onSettings = { vm.showSettings = true })
        val listState = rememberLazyListState()
        LaunchedEffect(vm.messages.size, vm.busy) {
            if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages) { Bubble(it) }
            if (vm.busy) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "Jarvis is thinking…",
                            color = Muted,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BotGray)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
        InputRow(onSend = vm::send)
    }

    if (vm.showSettings) SettingsDialog(vm)
}

@Composable
fun TopBar(online: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(UserBlue),
            contentAlignment = Alignment.Center
        ) { Text("J", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("JARVIS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) Good else Warn))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (online) "brain connected" else "API key needed",
                    color = Muted, fontSize = 12.sp
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
fun Bubble(m: ChatMessage) {
    val isUser = m.role == "user"
    Box(Modifier.fillMaxWidth()) {
        SelectionContainer(
            Modifier.align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
        ) {
            Text(
                m.text,
                color = if (isUser) Color.White else Color(0xFFC9D1D9),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp, topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 14.dp
                        )
                    )
                    .background(if (isUser) UserBlue else BotGray)
                    .padding(12.dp)
                    .widthIn(max = 300.dp)
            )
        }
    }
}

@Composable
fun InputRow(onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (input.isBlank()) return
        onSend(input)
        input = ""
        keyboard?.hide()
    }
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Ask Jarvis anything…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { submit() },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

@Composable
fun SettingsDialog(vm: JarvisViewModel) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var model by remember { mutableStateOf(vm.model) }
    AlertDialog(
        onDismissRequest = { vm.showSettings = false },
        title = { Text("Jarvis Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gemini API key (free from aistudio.google.com):", fontSize = 13.sp, color = Muted)
                TextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    placeholder = { Text("AIza…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("Preferred model (auto-falls-back on quota):", fontSize = 13.sp, color = Muted)
                Models.FALLBACK.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(selected = model == m, onClick = { model = m })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = model == m, onClick = { model = m })
                        Text(m, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(key, model) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { vm.showSettings = false }) { Text("Cancel") }
        }
    )
}
