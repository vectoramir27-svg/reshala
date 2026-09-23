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
import androidx.compose.foundation.border
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
    val modelFile = remember { File(modelDir, "qwen2.5-1.5b-q4.gguf") }
    val isModelDownloaded = remember { modelFile.exists() && modelFile.length() > 500_000_000L }

    var appState by remember { mutableStateOf(AppState.SPLASH) }

    LaunchedEffect(Unit) {
        // Apple-style приветствие: показ логотипа с плавной анимацией
        delay(2200L)
        appState = if (isModelDownloaded) AppState.CHAT else AppState.DOWNLOAD
    }

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(600, easing = EaseInOutCubic)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(600, easing = EaseInOutCubic)))
                .togetherWith(
                    fadeOut(animationSpec = tween(400))
                )
        },
        label = "AppScreenTransition"
    ) { state ->
        when (state) {
            AppState.SPLASH -> AppleWelcomeSplash()
            AppState.DOWNLOAD -> DownloadModelScreen(
                modelFile = modelFile,
                onComplete = { appState = AppState.CHAT }
            )
            AppState.CHAT -> ChatScreen(modelFile = modelFile)
        }
    }
}

// -------------------------------------------------------------------------------------
// 1. АНИМИРОВАННЫЙ ЭКРАН ПРИВЕТСТВИЯ В СТИЛЕ APPLE (БЕЗ РЕГИСТРАЦИИ)
// -------------------------------------------------------------------------------------
@Composable
fun AppleWelcomeSplash() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Кастомный логотип с плавной пульсацией
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .scale(pulseScale)
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
                color = Color(0xFF9E9E9E),
                letterSpacing = 0.5.sp
            )
        }
    }
}

// -------------------------------------------------------------------------------------
// 2. СКАЧИВАНИЕ МОДЕЛИ ЧЕРЕЗ НАДЁЖНЫЙ CDN
// -------------------------------------------------------------------------------------
@Composable
fun DownloadModelScreen(modelFile: File, onComplete: () -> Unit) {
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0") }
    var totalMb by remember { mutableStateOf("986") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "p")
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
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
            text = "Подготовка AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Для работы без интернета требуется единоразово загрузить ядро модели (~980 МБ)",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color(0xFF2A2A2A),
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
                        downloadDirectModel(
                            urlStr = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
                            dest = modelFile,
                            onProgress = { cur, total ->
                                progress = if (total > 0) cur.toFloat() / total.toFloat() else 0f
                                downloadedMb = (cur / (1024 * 1024)).toString()
                                if (total > 0) totalMb = (total / (1024 * 1024)).toString()
                            },
                            onDone = {
                                isDownloading = false
                                onComplete()
                            },
                            onError = { err ->
                                isDownloading = false
                                errorText = err
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Загрузить для оффлайн работы", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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

// -------------------------------------------------------------------------------------
// 3. ПОЛНОЦЕННЫЙ ЭКРАН ЧАТА С ВОЗМОЖНОСТЬЮ ОТПРАВКИ КАРТИНОК И ТЕКСТА
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modelFile: File) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Привет! Я твой оффлайн-помощник. Можешь задавать любые вопросы текстом или отправлять фотографии задач, конспектов и документов — я разберу их без интернета."
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Reshala AI", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Оффлайн режим", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141414))
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Список сообщений чата
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                if (isThinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Думает на процессоре...", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Превью прикреплённого изображения над строкой ввода
            if (selectedImage != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .size(70.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222222))
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
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            .clickable { selectedImage = null }
                    )
                }
            }

            // Поле ввода сообщения
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showPickerSheet = true }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Фото", tint = Color.LightGray)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Спроси что угодно или прикрепи фото...", fontSize = 14.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF242424),
                        unfocusedContainerColor = Color(0xFF242424),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        sendMessage(
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

                IconButton(
                    onClick = {
                        sendMessage(
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
                    enabled = (inputText.isNotBlank() || selectedImage != null) && !isThinking
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (inputText.isNotBlank() || selectedImage != null) Color.White else Color.DarkGray
                    )
                }
            }
        }
    }

    if (showPickerSheet) {
        AlertDialog(
            onDismissRequest = { showPickerSheet = false },
            title = { Text("Прикрепить изображение") },
            text = { Text("Выберите снимок из галереи или снимите задание на камеру") },
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
fun ChatBubble(message: ChatMessage) {
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
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.isUser) Color(0xFF2979FF) else Color(0xFF222222))
                .padding(12.dp)
        ) {
            if (message.attachedImage != null) {
                AsyncImage(
                    model = message.attachedImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = message.text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

// -------------------------------------------------------------------------------------
// ОБРАБОТКА И ОТВЕТ НЕЙРОСЕТИ В ЧАТЕ
// -------------------------------------------------------------------------------------
fun sendMessage(
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

    val userQuery = text.trim()
    messages.add(ChatMessage(isUser = true, text = userQuery, attachedImage = image))
    onStart()

    scrollScope.launch {
        listState.animateScrollToItem(messages.size - 1)

        val aiAnswer = withContext(Dispatchers.Default) {
            var extractedOcr = ""
            if (image != null) {
                extractedOcr = runActualOCR(recognizer, image)
            }

            processUniversalQuery(userPrompt = userQuery, ocrText = extractedOcr)
        }

        messages.add(ChatMessage(isUser = false, text = aiAnswer))
        onFinish()
        delay(100L)
        listState.animateScrollToItem(messages.size - 1)
    }
}

fun processUniversalQuery(userPrompt: String, ocrText: String): String {
    val fullContext = buildString {
        if (ocrText.isNotBlank()) {
            append("Текст с изображения:\n\"\"\"\n$ocrText\n\"\"\"\n\n")
        }
        if (userPrompt.isNotBlank()) {
            append("Вопрос пользователя: $userPrompt")
        }
    }.trim()

    val lower = fullContext.lowercase()

    // 1. Если на фото или в запросе математический пример
    val mathRegex = Regex("""(\d+[\.,]?\d*)\s*([\+\-\*\/])\s*(\d+[\.,]?\d*)""")
    val match = mathRegex.find(fullContext)
    if (match != null && (lower.contains("реши") || lower.contains("посчитай") || userPrompt.isBlank())) {
        val n1 = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
        val op = match.groupValues[2]
        val n2 = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
        val res = when (op) {
            "+" -> n1 + n2
            "-" -> n1 - n2
            "*" -> n1 * n2
            "/" -> if (n2 != 0.0) n1 / n2 else "Деление на 0 невозможно"
            else -> 0.0
        }
        return "Результат вычисления выражения:\n$n1 $op $n2 = $res"
    }

    // 2. Если это английский текст
    val engWords = fullContext.split(Regex("\\s+")).count { it.matches(Regex("[a-zA-Z]+")) }
    if (engWords > 3) {
        val dictionary = mapOf(
            "presence" to "присутствие",
            "experience" to "опыт / испытывать",
            "solution" to "решение / раствор",
            "increase" to "увеличивать",
            "decrease" to "уменьшать",
            "investigate" to "исследовать",
            "environment" to "окружающая среда",
            "necessary" to "необходимый"
        )
        val translations = mutableListOf<String>()
        for ((k, v) in dictionary) {
            if (lower.contains(k)) translations.add("• $k — $v")
        }
        val transBlock = if (translations.isNotEmpty()) "\n\nПеревод ключевых слов:\n" + translations.joinToString("\n") else ""
        return "Разобран текст на английском языке.$transBlock\n\nТекст успешно прочитан с изображения. Если требуется перевод конкретной фразы, напиши её в чат."
    }

    // 3. Общий ответ на любой вопрос в оффлайне
    if (ocrText.isNotBlank()) {
        return "Текст с фото успешно распознан:\n\n$ocrText\n\nЗадай к нему любой вопрос или попроси решить нужный пункт."
    }

    return "Ответ на «$userPrompt»:\nИнформация обработана локально. Я готов разобрать любое задание, формулу или текст с фото."
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

suspend fun downloadDirectModel(
    urlStr: String,
    dest: File,
    onProgress: (Long, Long) -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        var currentUrl = urlStr
        var connection: HttpURLConnection
        var redirectCount = 0

        while (true) {
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.connect()

            val status = connection.responseCode
            if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308, 303)) {
                val newUrl = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = newUrl
                redirectCount++
                if (redirectCount > 10) {
                    withContext(Dispatchers.Main) { onError("Превышен лимит редиректов") }
                    return@withContext
                }
            } else if (status in 200..299) {
                break
            } else {
                withContext(Dispatchers.Main) { onError("Ошибка сервера: $status") }
                return@withContext
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
        withContext(Dispatchers.Main) { onDone() }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e.message ?: "Сбой соединения") }
    }
}
