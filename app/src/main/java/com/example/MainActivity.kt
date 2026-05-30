package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    KayrSpamDashboard(
                        modifier = Modifier.padding(innerPadding),
                        onOpenAccessibilitySettings = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                                Toast.makeText(
                                    this,
                                    "Lütfen yüklü servisler altından 'KayrSpam'ı seçip aktif edin.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, "Erişilebilirlik ayarları açılamadı.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenOverlaySettings = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    startActivity(fallbackIntent)
                                } catch (ex: Exception) {
                                    Toast.makeText(this, "Üstte çizim izin ekranı açılamadı.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// Model-state class for mock chat messages
data class ChatMessage(
    val id: Long,
    val sender: String,
    val text: String,
    val timestamp: String,
    val isUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KayrSpamDashboard(
    modifier: Modifier = Modifier,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(false) }
    
    // Sandbox chat state
    var testInputText by remember { mutableStateOf("") }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var chatMessageCounter by remember { mutableStateOf(1L) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Periodically re-check permissions state when screen resumes
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = KayrSpamAccessibilityService.isServiceConnected
            isOverlayEnabled = Settings.canDrawOverlays(context)
            delay(1000)
        }
    }

    // Capture standard text updates in the sandbox tester
    LaunchedEffect(testInputText) {
        if (testInputText.isNotEmpty()) {
            val trimmed = testInputText.trim()
            if (trimmed.length >= KayrSpamState.spamText.length) {
                // Auto list output to simulate recipient receiving the bomber spams
                chatMessages.add(
                    ChatMessage(
                        id = chatMessageCounter++,
                        sender = "KayrSpam",
                        text = trimmed,
                        timestamp = "Şimdi",
                        isUser = true
                    )
                )
                testInputText = "" // Auto empty text field for spam stream simulation
                coroutineScope.launch {
                    delay(100)
                    listState.animateScrollToItem(chatMessages.size)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Deep charcol ElegantBg
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App top header bar matching Simulated Header from HTML
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF4F378B), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardCommandKey,
                    contentDescription = null,
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KayrSpam",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Keyboard Automator v1.0.4",
                    color = Color(0xFFCAC4D0),
                    fontSize = 11.sp
                )
            }
            
            // Connected/Status active dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (isAccessibilityEnabled && isOverlayEnabled) Color(0xFFD0BCFF) else Color(0xFFFF3D00),
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // System Integration Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SİSTEM ENTEGRASYONU (SYSTEM INTEGRATION)",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Accessibility Service toggling
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccessibilitySettings() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Erişilebilirlik Servisi",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isAccessibilityEnabled) "Aktif ve Bağlı" else "Servis kapalı (Aktifleştirin)",
                                color = if (isAccessibilityEnabled) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    // Design matching toggle indicator
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(24.dp)
                            .background(
                                color = if (isAccessibilityEnabled) Color(0xFF381E72) else Color(0xFF49454F),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(2.dp),
                        contentAlignment = if (isAccessibilityEnabled) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = if (isAccessibilityEnabled) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)

                // Screen Overlay permission row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOverlaySettings() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Ekran Üstü Çizim İzni",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isOverlayEnabled) "İzin Verildi" else "Tetikleyici balon için gerekir",
                                color = if (isOverlayEnabled) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Design matching toggle indicator
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(24.dp)
                            .background(
                                color = if (isOverlayEnabled) Color(0xFF381E72) else Color(0xFF49454F),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(2.dp),
                        contentAlignment = if (isOverlayEnabled) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = if (isOverlayEnabled) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Spam Configuration Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SPAM YAPILANDIRMASI (SPAM CONFIGURATION)",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Outlined Text Field customized with theme
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Varsayılan Spam Metni (Payload Message)",
                        color = Color(0xFFCAC4D0),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    OutlinedTextField(
                        value = KayrSpamState.spamText,
                        onValueChange = { KayrSpamState.spamText = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color(0xFF49454F)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        singleLine = true
                    )
                }

                // Transmission Speed configuration Block
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Yazma Gecikmesi (Interval)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = "${KayrSpamState.intervalMs}ms",
                            color = Color(0xFFD0BCFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = KayrSpamState.intervalMs.toFloat(),
                        onValueChange = { KayrSpamState.intervalMs = it.toLong() },
                        valueRange = 50f..2000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD0BCFF),
                            activeTrackColor = Color(0xFFD0BCFF),
                            inactiveTrackColor = Color(0xFF49454F)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Mini help bar badge directly from design
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x234F378B), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x3B4F378B), RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cihazın SES+ (VOL+) tuşunu herhangi bir uygulamanın içindeyken spam tetikleyicisi olarak kullanabilirsiniz.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Sandbox Chat Playground Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KAYRSPAM TEST ALANI (SANDBOX)",
                color = Color(0xFFD0BCFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            IconButton(
                onClick = { chatMessages.clear() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Temizle",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Sandbox simulated chat interface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF151418), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Henüz test spami tetiklenmedi.",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Alttaki test alanını odakladıktan sonra yeşil/mor balon 'BAŞLAT' tuşuyla spam akışını test edin.",
                        color = Color.DarkGray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chatMessages) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF4F378B), Color(0xFF381E72))
                                        ),
                                        shape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = message.text,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = message.timestamp,
                                        color = Color(0xFFD0BCFF).copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ground input focus test block
        OutlinedTextField(
            value = testInputText,
            onValueChange = { testInputText = it },
            placeholder = { Text("Spamleri yakalamak için buraya dokunun...", color = Color.Gray, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF151418),
                unfocusedContainerColor = Color(0xFF151418),
                focusedBorderColor = Color(0xFFD0BCFF),
                unfocusedBorderColor = Color(0xFF49454F)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            trailingIcon = {
                if (testInputText.isNotEmpty()) {
                    IconButton(onClick = {
                        chatMessages.add(
                            ChatMessage(
                                id = chatMessageCounter++,
                                sender = "Siz",
                                text = testInputText,
                                timestamp = "Şimdi",
                                isUser = true
                            )
                        )
                        testInputText = ""
                        focusManager.clearFocus()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Gönder",
                            tint = Color(0xFFD0BCFF)
                        )
                    }
                }
            }
        )
    }
}
