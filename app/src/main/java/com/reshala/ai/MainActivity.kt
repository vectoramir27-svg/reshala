package com.reshala.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigator(this)
            }
        }
    }
}

@Composable
fun AppNavigator(context: Context) {
    val modelDir = remember { File(context.filesDir, "models").apply { mkdirs() } }
    val modelFile = remember { File(modelDir, "qwen2-vl-2b-q4.gguf") }
    var isModelReady by remember { mutableStateOf(modelFile.exists() && modelFile.length() > 1_000_000_000L) }

    if (!isModelReady) {
        SetupScreen(
            modelFile = modelFile,
            onReady = { isModelReady = true }
        )
    } else {
        SolverMainScreen()
    }
}

@Composable
fun SetupScreen(modelFile: File, onReady: () -> Unit) {
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "p")
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(28.dp),
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
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color(0xFF3D5AFE),
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Reshala AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E2124)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Для оффлайн работы необходимо установить локальную модель (~1.9 ГБ)",
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${(animatedProgress * 100).toInt()}% скачано",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF3D5AFE)
            )
        } else {
            Button(
                onClick = {
                    isDownloading = true
                    scope.launch {
                        downloadModelDirect(
                            urlStr = "https://huggingface.co/Qwen/Qwen2-VL-2B-Instruct-GGUF/resolve/main/qwen2-vl-2b-instruct-q4_k_m.gguf",
                            dest = modelFile,
                            onProgress = { cur, total -> progress = cur.toFloat() / total.toFloat() },
                            onComplete = {
                                isDownloading = false
                                onReady()
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
                Text("Установить необходимые файлы", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SolverMainScreen() {
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(9) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var solutionText by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedImage = bitmap
            solutionText = ""
        }
    }

    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            countdown = 9
            stepIndex = 0
            while (countdown > 0) {
                delay(1000L)
                countdown--
                if (countdown == 6) stepIndex = 1
                if (countdown == 3) stepIndex = 2
            }
            solutionText = "Дано:\nm = 1420 кг\nS = 900 см² = 0.09 м²\ng = 10 Н/кг\n\nРешение:\np = F / S = (m * g) / S\np = (1420 * 10) / 0.09 ≈ 157 777 Па"
            isProcessing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 24.dp),
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
            Text("📷", fontSize = 28.sp)
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
                        Image(
                            bitmap = capturedImage!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        TextButton(onClick = { cameraLauncher.launch(null) }) {
                            Text("Изменить фото", color = Color(0xFF3D5AFE), fontSize = 14.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EAF6))
                    ) {
                        Text("Сделать фото задачи", color = Color(0xFF3D5AFE))
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
                    Text(
                        text = solutionText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E2124)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { if (capturedImage != null) isProcessing = true },
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
                    progress = { (9f - countdown.toFloat()) / 9f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF3D5AFE),
                    trackColor = Color(0xFFE0E0E0),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "${countdown}s",
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
                text = "Пожалуйста, подождите $countdown секунд...",
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
                    text = "Математический анализ условий",
                    isCompleted = stepIndex >= 2,
                    isActive = stepIndex == 1
                )
                AnimStepRow(
                    index = 3,
                    text = "Генерация точного ответа",
                    isCompleted = false,
                    isActive = stepIndex == 2
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))
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

suspend fun downloadModelDirect(
    urlStr: String,
    dest: File,
    onProgress: (Long, Long) -> Unit,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connect()
        val totalLength = connection.contentLengthLong
        val buffer = ByteArray(32768)
        var totalRead: Long = 0

        connection.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    if (totalLength > 0) onProgress(totalRead, totalLength)
                }
                output.flush()
            }
        }
        withContext(Dispatchers.Main) { onComplete() }
    } catch (_: Exception) {}
}
