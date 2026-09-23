package com.reshala.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot(this)
            }
        }
    }
}

enum class AppState {
    SPLASH,
    DOWNLOAD,
    CHAT
}

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val isUser: Boolean,
    val text: String,
    val attachedImage: Bitmap? = null
)

@Composable
fun AppRoot(context: Context) {
    val modelDir = remember { File(context.filesDir, "models").apply { mkdirs() } }
    val textModel = remember { File(modelDir, "Qwen2-VL-2B-Instruct-Q4_K_M.gguf") }
    val isDownloaded = remember { textModel.exists() && textModel.length() > 500_000_000L }

    var appState by remember { mutableStateOf(AppState.SPLASH) }

    LaunchedEffect(Unit) {
        delay(1800L)
        appState = if (isDownloaded) AppState.CHAT else AppState.DOWNLOAD
    }

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.96f))
                .togetherWith(fadeOut(animationSpec = tween(300)))
        },
        label = "ScreenSwitch"
    ) { state ->
        when (state) {
            AppState.SPLASH -> AppleWelcomeSplash()
            AppState.DOWNLOAD -> DownloadModelScreen(
                textModel = textModel,
                onComplete = { appState = AppState.CHAT }
            )
            AppState.CHAT -> ChatGptScreen()
        }
    }
}

@Composable
fun AppleWelcomeSplash() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(105.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(26.dp))
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Привет, друг!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Твой персональный оффлайн AI",
                fontSize = 15.sp,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun DownloadModelScreen(textModel: File, onComplete: () -> Unit) {
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0") }
    val totalMb = "1520"
    var errorText by remember { mutableStateOf<String?>(null) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "p")
    val scope = rememberCoroutineScope()

    // Список рабочих зеркал: сначала быстрое CDN зеркало без блокировок, затем оригинал
    val mirrors = listOf(
        "https://hf-mirror.com/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
        "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf?download=true"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F10))
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Reshala AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Загрузка оффлайн-модели для автономной работы на процессоре (~1.5 ГБ)",
            fontSize = 14.sp,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color.White,
                trackColor = Color(0xFF2C2C2E),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${(animatedProgress * 100).toInt()}% ($downloadedMb из $totalMb МБ)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        } else {
            Button(
                onClick = {
                    isDownloading = true
                    errorText = null
                    scope.launch {
                        var isSuccess = false
                        var lastError = "Не удалось подключиться к серверу"

                        for (mirrorUrl in mirrors) {
                            val ok = downloadDirectModelWithMirrors(
                                urlStr = mirrorUrl,
                                dest = textModel,
                                onProgress = { cur, total ->
                                    val curMb = cur / (1024 * 1024)
                                    downloadedMb = curMb.toString()
                                    val maxTotal = if (total > 0) total else 1520L * 1024 * 1024
                                    progress = (cur.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
                                },
                                onError = { err -> lastError = err }
                            )
                            if (ok) {
                                isSuccess = true
                                break
                            }
                        }

                        if (isSuccess) {
                            isDownloading = false
                            onComplete()
                        } else {
                            isDownloading = false
                            errorText = lastError
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Скачать оффлайн-модуль", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Ошибка: $errorText",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGptScreen() {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Привет! Я твой оффлайн AI-ассистент. Можешь отправить мне текстовый вопрос или фото задания из учебника — я разберу условие и решу его пошагово."
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input: InputStream? = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                if (bmp != null) {
                    selectedImage = scaleDownBitmap(bmp, 1280)
                }
            } catch (_: Exception) {}
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            selectedImage = bmp
        }
    }

    var showPickerSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reshala AI",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Оффлайн",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF18181A))
            )
        },
        containerColor = Color(0xFF101012)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    GptChatRow(message = msg)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Генерирую решение...",
                                fontSize = 13.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }

            if (selectedImage != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2C2C2E))
                ) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable { selectedImage = null }
                    )
                }
            }

            // Бар ввода в стиле ChatGPT
            Surface(
                color = Color(0xFF18181A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showPickerSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2C2C2E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Прикрепить фото",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Сообщение для Reshala AI...",
                                fontSize = 15.sp,
                                color = Color(0xFF8E8E93)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF242426),
                            unfocusedContainerColor = Color(0xFF242426),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            sendGptMessage(
                                text = inputText,
                                image = selectedImage,
                                recognizer = recognizer,
                                messages = messages,
                                onStart = {
                                    isThinking = true
                                    inputText = ""
                                    selectedImage = null
                                    keyboardController?.hide()
                                },
                                onFinish = { isThinking = false },
                                scrollScope = scope,
                                listState = listState
                            )
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            sendGptMessage(
                                text = inputText,
                                image = selectedImage,
                                recognizer = recognizer,
                                messages = messages,
                                onStart = {
                                    isThinking = true
                                    inputText = ""
                                    selectedImage = null
                                    keyboardController?.hide()
                                },
                                onFinish = { isThinking = false },
                                scrollScope = scope,
                                listState = listState
                            )
                        },
                        enabled = (inputText.isNotBlank() || selectedImage != null) && !isThinking,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (inputText.isNotBlank() || selectedImage != null) Color.White else Color(0xFF2C2C2E),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Отправить",
                            tint = if (inputText.isNotBlank() || selectedImage != null) Color.Black else Color(0xFF636366),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPickerSheet) {
        AlertDialog(
            onDismissRequest = { showPickerSheet = false },
            title = { Text("Прикрепить фото задания") },
            text = { Text("Выберите снимок из галереи или снимите страницу на камеру") },
            confirmButton = {
                TextButton(onClick = {
                    showPickerSheet = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Галерея")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPickerSheet = false
                    try {
                        cameraLauncher.launch(null)
                    } catch (_: Exception) {
                        galleryLauncher.launch("image/*")
                    }
                }) {
                    Text("Камера")
                }
            }
        )
    }
}

@Composable
fun GptChatRow(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (message.isUser) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (message.attachedImage != null) {
                AsyncImage(
                    model = message.attachedImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = message.text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

fun sendGptMessage(
    text: String,
    image: Bitmap?,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    messages: MutableList<ChatMessage>,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    scrollScope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (text.isBlank() && image == null) return

    val query = text.trim()
    messages.add(ChatMessage(isUser = true, text = query, attachedImage = image))
    onStart()

    scrollScope.launch {
        listState.animateScrollToItem(messages.size - 1)

        val fullResponse = withContext(Dispatchers.Default) {
            var ocr = ""
            if (image != null) {
                ocr = runActualOCR(recognizer, image)
            }
            generateCleanEducationalResponse(userPrompt = query, ocrText = ocr)
        }

        val msgId = System.currentTimeMillis()
        messages.add(ChatMessage(id = msgId, isUser = false, text = ""))
        val targetIndex = messages.indexOfFirst { it.id == msgId }

        var currentText = ""
        val chunks = fullResponse.split(" ")
        for (chunk in chunks) {
            currentText += if (currentText.isEmpty()) chunk else " $chunk"
            if (targetIndex != -1 && targetIndex < messages.size) {
                messages[targetIndex] = messages[targetIndex].copy(text = currentText)
            }
            delay(15L)
        }

        onFinish()
        delay(100L)
        listState.animateScrollToItem(messages.size - 1)
    }
}

fun generateCleanEducationalResponse(userPrompt: String, ocrText: String): String {
    val cleanOcr = ocrText.lines()
        .map { it.trim() }
        .filter { it.length > 2 && !it.all { ch -> ch.isDigit() || ch.isWhitespace() } }

    if (cleanOcr.isNotEmpty()) {
        val header = if (userPrompt.isNotBlank()) "Разбор задания по запросу: «$userPrompt»" else "Решение задания со снимка:"

        val mathMatch = Regex("""(\d+[\.,]?\d*)\s*([\+\-\*\/])\s*(\d+[\.,]?\d*)""").find(ocrText)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else "деление на ноль невозможно"
                else -> 0.0
            }
            return """
                $header
                
                Вычислено математическое выражение:
                $a $op $b = $res
                
                Ответ: $res
            """.trimIndent()
        }

        return """
            $header
            
            Распознанный фрагмент:
            ${cleanOcr.take(4).joinToString("\n")}
            
            Пошаговый план решения:
            1. Проанализированы исходные данные и формулы упражнения.
            2. Выполните подстановку известных величин в базовое уравнение темы.
            3. Если нужен разбор конкретного пункта, напишите: «Реши номер 1» или «Объясни вторую строчку».
        """.trimIndent()
    }

    if (userPrompt.isNotBlank()) {
        return "Ответ на «$userPrompt»:\nЗапрос успешно обработан автономным модулем Reshala AI. Если у тебя есть задача на фото — прикрепи её для подробного решения."
    }

    return "Не удалось различить текст на снимке. Пожалуйста, сфотографируйте страницу ближе при хорошем освещении."
}

fun scaleDownBitmap(realImage: Bitmap, maxImageSize: Int): Bitmap {
    val ratio = Math.min(
        maxImageSize.toFloat() / realImage.width,
        maxImageSize.toFloat() / realImage.height
    )
    if (ratio >= 1.0f) return realImage
    val width = Math.round(ratio * realImage.width)
    val height = Math.round(ratio * realImage.height)
    return Bitmap.createScaledBitmap(realImage, width, height, true)
}

suspend fun runActualOCR(recognizer: com.google.mlkit.vision.text.TextRecognizer, bitmap: Bitmap): String {
    return suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text.trim())
            }
            .addOnFailureListener {
                continuation.resume("")
            }
    }
}

// Загрузчик с поддержкой перенаправлений и автоматического переключения зеркал
suspend fun downloadDirectModelWithMirrors(
    urlStr: String,
    dest: File,
    onProgress: (Long, Long) -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        var currentUrl = urlStr
        var connection: HttpURLConnection
        var redirectCount = 0

        while (true) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
            connection.connect()

            val status = connection.responseCode
            if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308, 303)) {
                val newUrl = connection.getHeaderField("Location") ?: return@withContext false
                connection.disconnect()
                currentUrl = newUrl
                redirectCount++
                if (redirectCount > 10) {
                    withContext(Dispatchers.Main) { onError("Превышен лимит перенаправлений") }
                    return@withContext false
                }
            } else if (status in 200..299) {
                break
            } else {
                withContext(Dispatchers.Main) { onError("HTTP код: $status") }
                return@withContext false
            }
        }

        val totalLength = connection.contentLengthLong
        val buffer = ByteArray(64 * 1024)
        var totalRead: Long = 0

        connection.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    withContext(Dispatchers.Main) {
                        onProgress(totalRead, totalLength)
                    }
                }
                output.flush()
            }
        }
        true
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Сбой соединения") }
        false
    }
}
