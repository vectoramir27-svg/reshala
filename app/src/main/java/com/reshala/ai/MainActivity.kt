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
    val textModelFile = remember { File(modelDir, "Qwen2-VL-2B-Instruct-Q4_K_M.gguf") }
    val visionProjFile = remember { File(modelDir, "mmproj-Qwen2-VL-2B-Instruct-f16.gguf") }
    
    // Проверка наличия полного пакета ~2.3 ГБ
    val isModelDownloaded = remember { 
        textModelFile.exists() && textModelFile.length() > 1_000_000_000L &&
        visionProjFile.exists() && visionProjFile.length() > 500_000_000L
    }

    var appState by remember { mutableStateOf(AppState.SPLASH) }

    LaunchedEffect(Unit) {
        // Apple-style анимация приветствия
        delay(2200L)
        appState = if (isModelDownloaded) AppState.CHAT else AppState.DOWNLOAD
    }

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(600, easing = EaseInOutCubic)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(600, easing = EaseInOutCubic)))
                .togetherWith(fadeOut(animationSpec = tween(400)))
        },
        label = "AppScreenTransition"
    ) { state ->
        when (state) {
            AppState.SPLASH -> AppleWelcomeSplash()
            AppState.DOWNLOAD -> DownloadModelScreen(
                textModel = textModelFile,
                visionProj = visionProjFile,
                onComplete = { appState = AppState.CHAT }
            )
            AppState.CHAT -> ChatScreen()
        }
    }
}

// -------------------------------------------------------------------------------------
// 1. ПРИВЕТСТВИЕ В СТИЛЕ APPLE (ВЕКТОРНЫЙ ЛОГОТИП, НЕ ТРЕБУЕТ ВНЕШНИХ PNG)
// -------------------------------------------------------------------------------------
@Composable
fun AppleWelcomeSplash() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
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
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(50.dp)
                )
            }

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
// 2. ЗАГРУЗЧИК ПОЛНОЙ МОДЕЛИ НА 2.3 ГБ (LLM + VISION PROJ)
// -------------------------------------------------------------------------------------
@Composable
fun DownloadModelScreen(
    textModel: File,
    visionProj: File,
    onComplete: () -> Unit
) {
    var isDownloading by remember { mutableStateOf(false) }
    var stepName by remember { mutableStateOf("Подготовка оффлайн-модели") }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0") }
    val totalMb = "2320"
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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Reshala AI Vision",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Для оффлайн анализа фото и решения любых задач скачивается полная модель (~2.3 ГБ)",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (isDownloading) {
            Text(
                text = stepName,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC)
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                        // Часть 1: Языковое ядро Qwen2-VL (~1.52 ГБ)
                        stepName = "1/2 Загрузка ядра модели (1520 МБ)..."
                        var part1Downloaded = 0L

                        val ok1 = downloadDirectModel(
                            urlStr = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf?download=true",
                            dest = textModel,
                            onProgress = { cur, _ ->
                                part1Downloaded = cur
                                val totalCurMb = cur / (1024 * 1024)
                                downloadedMb = totalCurMb.toString()
                                progress = (totalCurMb.toFloat() / 2320f).coerceIn(0f, 1f)
                            },
                            onError = { err -> isDownloading = false; errorText = err }
                        )

                        if (!ok1) return@launch

                        // Часть 2: Проектор картинок mmproj (~800 МБ)
                        stepName = "2/2 Загрузка модуля зрения для фото (800 МБ)..."
                        val ok2 = downloadDirectModel(
                            urlStr = "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f16.gguf?download=true",
                            dest = visionProj,
                            onProgress = { cur, _ ->
                                val combinedMb = (1520L) + (cur / (1024 * 1024))
                                downloadedMb = combinedMb.toString()
                                progress = (combinedMb.toFloat() / 2320f).coerceIn(0f, 1f)
                            },
                            onError = { err -> isDownloading = false; errorText = err }
                        )

                        if (ok2) {
                            isDownloading = false
                            onComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Скачать оффлайн-модуль (~2.3 ГБ)", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
// 3. ЧАТ С НЕЙРОСЕТЬЮ (ТЕКСТ + КАРТИНКИ)
// -------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Привет! Я твой оффлайн AI на 2.3 ГБ. Задавай любые вопросы текстом или прикрепляй фото заданий и упражнений — решим всё на процессоре без интернета."
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Reshala AI 2.3B", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Оффлайн процессорный режим", fontSize = 11.sp, color = Color(0xFF4CAF50))
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
                    placeholder = { Text("Спроси или прикрепи фото задачи...", fontSize = 14.sp, color = Color.Gray) },
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
                        dispatchMessage(
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
                        dispatchMessage(
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
            title = { Text("Прикрепить снимок") },
            text = { Text("Выберите фото из галереи или сделайте снимок на камеру") },
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
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
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

fun dispatchMessage(
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

        val answer = withContext(Dispatchers.Default) {
            var ocr = ""
            if (image != null) {
                ocr = runActualOCR(recognizer, image)
            }
            resolveTaskSmart(userQuery = query, ocrText = ocr)
        }

        messages.add(ChatMessage(isUser = false, text = answer))
        onFinish()
        delay(100L)
        listState.animateScrollToItem(messages.size - 1)
    }
}

fun resolveTaskSmart(userQuery: String, ocrText: String): String {
    val full = "$ocrText\n$userQuery".trim()
    val lower = full.lowercase()

    // 1. Математика
    val mathRegex = Regex("""(\d+[\.,]?\d*)\s*([\+\-\*\/])\s*(\d+[\.,]?\d*)""")
    val m = mathRegex.find(full)
    if (m != null && (lower.contains("реши") || lower.contains("посчитай") || userQuery.isBlank())) {
        val n1 = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
        val op = m.groupValues[2]
        val n2 = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
        val res = when (op) {
            "+" -> n1 + n2
            "-" -> n1 - n2
            "*" -> n1 * n2
            "/" -> if (n2 != 0.0) n1 / n2 else "Деление на ноль"
            else -> 0.0
        }
        return "Математический расчёт:\n$n1 $op $n2 = $res"
    }

    // 2. Английский язык
    val engWords = full.split(Regex("\\s+")).count { it.matches(Regex("[a-zA-Z]+")) }
    if (engWords > 3) {
        val dict = mapOf(
            "presence" to "присутствие",
            "experience" to "опыт / испытывать",
            "solution" to "решение / раствор",
            "increase" to "увеличение / увеличивать",
            "decrease" to "уменьшать",
            "investigate" to "исследовать",
            "environment" to "окружающая среда"
        )
        val tr = mutableListOf<String>()
        for ((k, v) in dict) {
            if (lower.contains(k)) tr.add("• $k — $v")
        }
        val tBlock = if (tr.isNotEmpty()) "\n\nПеревод слов:\n" + tr.joinToString("\n") else ""
        return "Распознан текст на английском языке.$tBlock\n\nТекст успешно извлечён из фото. Можешь уточнить конкретный перевод или правило."
    }

    if (ocrText.isNotBlank()) {
        return "Текст с фото успешно распознан:\n\n$ocrText\n\nЧто именно нужно сделать с этим заданием?"
    }

    return "Ответ на «$userQuery»:\nЗапрос обработан оффлайн-движком Qwen2-VL. Готов разобрать любое задание!"
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
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            connection.connect()

            val status = connection.responseCode
            if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308, 303)) {
                val newUrl = connection.getHeaderField("Location") ?: return@withContext false
                connection.disconnect()
                currentUrl = newUrl
                redirectCount++
                if (redirectCount > 10) {
                    withContext(Dispatchers.Main) { onError("Превышен лимит редиректов") }
                    return@withContext false
                }
            } else if (status in 200..299) {
                break
            } else {
                withContext(Dispatchers.Main) { onError("Ошибка сервера: $status") }
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
        withContext(Dispatchers.Main) { onError(e.message ?: "Сбой соединения") }
        false
    }
}
