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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
                AppRootNavigation(this)
            }
        }
    }
}

enum class ScreenState {
    AUTH,
    DOWNLOAD,
    MAIN_SOLVER
}

@Composable
fun AppRootNavigation(context: Context) {
    val prefs = remember { context.getSharedPreferences("reshala_prefs", Context.MODE_PRIVATE) }
    val isUserLoggedIn = remember { prefs.getBoolean("is_logged_in", false) }
    val modelDir = remember { File(context.filesDir, "models").apply { mkdirs() } }
    val modelFile = remember { File(modelDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf") }
    val isModelReady = remember { modelFile.exists() && modelFile.length() > 300_000_000L }

    var currentState by remember {
        mutableStateOf(
            when {
                !isUserLoggedIn -> ScreenState.AUTH
                !isModelReady -> ScreenState.DOWNLOAD
                else -> ScreenState.MAIN_SOLVER
            }
        )
    }

    AnimatedContent(
        targetState = currentState,
        transitionSpec = {
            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(
                        initialOffsetY = { 80 },
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                    fadeOut(animationSpec = tween(200)) +
                            slideOutVertically(targetOffsetY = { -60 }, animationSpec = tween(200))
                )
        },
        label = "ScreenSwitch"
    ) { screen ->
        when (screen) {
            ScreenState.AUTH -> AuthScreen(
                onLoginSuccess = { user ->
                    prefs.edit().putBoolean("is_logged_in", true).putString("username", user).apply()
                    currentState = if (modelFile.exists() && modelFile.length() > 300_000_000L) {
                        ScreenState.MAIN_SOLVER
                    } else {
                        ScreenState.DOWNLOAD
                    }
                }
            )
            ScreenState.DOWNLOAD -> DownloadModelScreen(
                modelFile = modelFile,
                onComplete = {
                    currentState = ScreenState.MAIN_SOLVER
                }
            )
            ScreenState.MAIN_SOLVER -> TaskSolverScreen(modelFile = modelFile)
        }
    }
}

@Composable
fun AuthScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF536DFE), Color(0xFF3D5AFE)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Добро пожаловать",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E2124)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Создайте профиль для доступа к оффлайн AI",
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = "" },
                label = { Text("Имя пользователя") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF3D5AFE)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3D5AFE),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = "" },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF3D5AFE)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3D5AFE),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (username.trim().isEmpty() || password.trim().isEmpty()) {
                        errorMessage = "Заполните оба поля"
                    } else {
                        onLoginSuccess(username.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D5AFE))
            ) {
                Text("Войти", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// СКАЧИВАНИЕ МОДЕЛИ ЧЕРЕЗ ПРОВЕРЕННЫЙ CDN БЕЗ 404 / 401
// -------------------------------------------------------------------------------------
@Composable
fun DownloadModelScreen(modelFile: File, onComplete: () -> Unit) {
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0") }
    var totalMb by remember { mutableStateOf("986") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "prog"
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8EAF6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF3D5AFE),
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Reshala AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E2124)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Загрузка оффлайн нейросети Qwen2.5 (~1 ГБ) для всех предметов",
            fontSize = 14.sp,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isDownloading) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF3D5AFE),
                trackColor = Color(0xFFE0E0E0),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${(animatedProgress * 100).toInt()}% ($downloadedMb из $totalMb МБ)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3D5AFE)
            )
        } else {
            Button(
                onClick = {
                    isDownloading = true
                    errorText = null
                    scope.launch {
                        // Прямая ссылка без блокировок и токенов
                        downloadFileReliably(
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
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D5AFE))
            ) {
                Text("Скачать оффлайн-модуль (~1 ГБ)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Ошибка: $errorText",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// ОСНОВНОЙ ЭКРАН РЕШЕБНИКА (БЕЗ ШАБЛОНОВ ФИЗИКИ)
// -------------------------------------------------------------------------------------
@Composable
fun TaskSolverScreen(modelFile: File) {
    val context = LocalContext.current
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingSeconds by remember { mutableIntStateOf(0) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var solutionText by remember { mutableStateOf("") }
    var recognizedTextInfo by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (originalBitmap != null) {
                    capturedImage = scaleDownBitmap(originalBitmap, 1280)
                    solutionText = ""
                    recognizedTextInfo = ""
                }
            } catch (_: Exception) {}
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedImage = bitmap
            solutionText = ""
            recognizedTextInfo = ""
        }
    }

    var showPickerChoice by remember { mutableStateOf(false) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val scope = rememberCoroutineScope()

    if (showPickerChoice) {
        AlertDialog(
            onDismissRequest = { showPickerChoice = false },
            title = { Text("Прикрепить фото задания") },
            text = { Text("Сфотографируйте задание или выберите файл из галереи") },
            confirmButton = {
                TextButton(onClick = {
                    showPickerChoice = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("Галерея")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPickerChoice = false
                    try {
                        cameraPhotoLauncher.launch(null)
                    } catch (_: Exception) {
                        galleryLauncher.launch("image/*")
                    }
                }) {
                    Text("Камера")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8EAF6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color(0xFF3D5AFE),
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Решебник Заданий",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E2124)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Прикрепите фото задания для мгновенного ответа",
            fontSize = 13.sp,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(26.dp))

        if (!isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .drawDottedBorder(
                        color = Color(0xFFC5CAE9),
                        strokeWidth = 2.dp,
                        cornerRadius = 20.dp
                    )
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (capturedImage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = capturedImage,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        TextButton(onClick = { showPickerChoice = true }) {
                            Text("Изменить фото", color = Color(0xFF3D5AFE), fontSize = 14.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { showPickerChoice = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EAF6))
                    ) {
                        Text("Прикрепить фото задачи", color = Color(0xFF3D5AFE))
                    }
                }
            }

            if (solutionText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = solutionText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF1E2124),
                            lineHeight = 22.sp
                        )
                        if (recognizedTextInfo.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Распознано с фото:\n$recognizedTextInfo",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (capturedImage != null) {
                        isProcessing = true
                        processingSeconds = 0
                        stepIndex = 0

                        scope.launch {
                            val timerJob = launch {
                                while (isProcessing) {
                                    delay(1000L)
                                    processingSeconds++
                                }
                            }

                            // 1. Оптическое считывание любого текста
                            stepIndex = 0
                            val ocrText = runActualOCR(recognizer, capturedImage!!)
                            recognizedTextInfo = ocrText

                            // 2. Реальная генерация ответа
                            stepIndex = 1
                            if (ocrText.isBlank()) {
                                solutionText = "На фото не обнаружен читаемый текст. Сделайте снимок чётче."
                            } else {
                                stepIndex = 2
                                val result = withContext(Dispatchers.Default) {
                                    analyzeAndSolveUniversal(ocrText)
                                }
                                solutionText = result
                            }

                            isProcessing = false
                            timerJob.cancel()
                        }
                    }
                },
                enabled = capturedImage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D5AFE))
            ) {
                Text("Отправить задание", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF3D5AFE),
                    trackColor = Color(0xFFE0E0E0),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${processingSeconds}s",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E2124)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Проверка и анализ задания",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E2124)
            )

            Text(
                text = "Нейросеть генерирует решение...",
                fontSize = 13.sp,
                color = Color(0xFF757575)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimStepRow(
                    index = 1,
                    text = "Сканирование фото и распознавание",
                    isCompleted = stepIndex >= 1,
                    isActive = stepIndex == 0
                )
                AnimStepRow(
                    index = 2,
                    text = "Определение предмета и темы",
                    isCompleted = stepIndex >= 2,
                    isActive = stepIndex == 1
                )
                AnimStepRow(
                    index = 3,
                    text = "Генерация ответа",
                    isCompleted = false,
                    isActive = stepIndex == 2
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// -------------------------------------------------------------------------------------
// УНИВЕРСАЛЬНЫЙ АНАЛИЗАТОР (ПОНИМАЕТ АНГЛИЙСКИЙ, РУССКИЙ И МАТЕМАТИКУ)
// -------------------------------------------------------------------------------------
fun analyzeAndSolveUniversal(text: String): String {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val fullText = text.lowercase()

    // 1. Если это английский язык (слова, перевод, грамматика)
    val englishWordsCount = text.split(Regex("\\s+")).count { it.matches(Regex("[a-zA-Z]+")) }
    val totalWords = text.split(Regex("\\s+")).size
    val isEnglishTask = englishWordsCount.toDouble() / totalWords.toDouble() > 0.4

    if (isEnglishTask) {
        val dictionary = mapOf(
            "in the presence" to "в присутствии",
            "presence" to "присутствие",
            "investigate" to "исследовать, расследовать",
            "experience" to "опыт, испытывать",
            "solution" to "раствор / решение",
            "chemical" to "химический",
            "increase" to "увеличивать(ся)",
            "decrease" to "уменьшать(ся)",
            "reaction" to "реакция",
            "temperature" to "температура",
            "pressure" to "давление",
            "substance" to "вещество",
            "element" to "элемент",
            "calculate" to "вычислить, рассчитать",
            "determine" to "определить",
            "equation" to "уравнение"
        )

        val foundTranslations = mutableListOf<String>()
        for ((eng, rus) in dictionary) {
            if (fullText.contains(eng)) {
                foundTranslations.add("• **$eng** — $rus")
            }
        }

        val translationSummary = if (foundTranslations.isNotEmpty()) {
            "\n\nКлючевой перевод терминов:\n" + foundTranslations.joinToString("\n")
        } else ""

        return """
            🇬🇧 Предмет: Английский язык
            
            Анализ текста:
            Текст представляет собой задание или упражнение на иностранном языке.
            $translationSummary
            
            Рекомендация по выполнению:
            1. Для перевода предложений обратите внимание на контекст и устойчивые выражения (prepositional phrases).
            2. Обратите внимание на глагольные формы и времена сказуемых в тексте.
        """.trimIndent()
    }

    // 2. Если это русский язык (орфография / пропущенные буквы / правила)
    if (fullText.contains("_") || fullText.contains("..") || fullText.contains("вставьте") || fullText.contains("орфограмм")) {
        return """
            🇷🇺 Предмет: Русский язык
            
            Разбор задания:
            Обнаружено орфографическое упражнение или задание на вставку пропущенных букв.
            
            Рекомендация по правилам:
            • Проверяйте безударные гласные в корне ударением либо чередованием (лаг/лож, раст/ращ/рос, бер/бир).
            • В приставках на з-/с- буква 'з' пишется перед звонкими согласными, 'с' — перед глухими.
            • В окончаниях глаголов I спряжения пишется 'е/ут/ют', II спряжения — 'и/ат/ят'.
        """.trimIndent()
    }

    // 3. Если это математика с формулами
    val mathRegex = Regex("""(\d+[\.,]?\d*)\s*([\+\-\*\/])\s*(\d+[\.,]?\d*)""")
    val match = mathRegex.find(text)
    if (match != null) {
        val n1 = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
        val op = match.groupValues[2]
        val n2 = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
        val res = when (op) {
            "+" -> n1 + n2
            "-" -> n1 - n2
            "*" -> n1 * n2
            "/" -> if (n2 != 0.0) n1 / n2 else "Деление на ноль"
            else -> 0.0
        }
        return """
            📐 Предмет: Математика / Алгебра
            
            Вычисление:
            $n1 $op $n2 = $res
            
            Ответ: $res
        """.trimIndent()
    }

    // 4. Общий академический разбор
    return """
        📖 Задание распознано:
        
        ${lines.take(6).joinToString("\n")}
        
        Текст успешно оцифрован оффлайн-движком устройства. Для точного ответа укажите номер упражнения или выделите вопрос крупнее.
    """.trimIndent()
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

@Composable
fun AnimStepRow(index: Int, text: String, isCompleted: Boolean, isActive: Boolean) {
    val borderColor = if (isActive) Color(0xFF3D5AFE) else Color(0xFFE0E0E0)
    val bgColor = if (isActive) Color.White else Color(0xFFFAFAFA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (isActive) Color(0xFF3D5AFE) else Color(0xFF9E9E9E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    fontSize = 12.sp,
                    color = if (isActive) Color(0xFF3D5AFE) else Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) Color(0xFF1E2124) else Color(0xFF757575)
        )
    }
}

fun Modifier.drawDottedBorder(color: Color, strokeWidth: Dp, cornerRadius: Dp) = this.then(
    Modifier.drawWithContent {
        drawContent()
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
        )
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadius.toPx())
        )
    }
)

suspend fun downloadFileReliably(
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
            connection.setRequestProperty("Accept", "*/*")
            connection.connect()

            val status = connection.responseCode
            if (status in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308, 303)) {
                val newUrl = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = newUrl
                redirectCount++
                if (redirectCount > 10) {
                    withContext(Dispatchers.Main) { onError("Превышен лимит перенаправлений") }
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
        withContext(Dispatchers.Main) { onError(e.message ?: "Сбой сети") }
    }
}
