package com.example.receiptmanager

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.receiptmanager.ui.theme.ReceiptManagerTheme
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

data class ChatMessage(
    val text: String,
    val translatedText: String,
    val isUser: Boolean
)

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val sha256: String
)

enum class ScanMode(val label: String) {
    SAFE("금고보관"), DISPOSAL("폐기"), DAMAGE("파손");

    fun next(): ScanMode = when (this) {
        SAFE -> DISPOSAL
        DISPOSAL -> DAMAGE
        DAMAGE -> SAFE
    }
}

data class DisposalItem(val name: String, val quantity: Int)

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            ReceiptManagerTheme {
                ReceiptApp()
            }
        }
    }
}

// 앱의 루트 Composable - 모든 화면 상태와 네비게이션을 관리
@Composable
fun ReceiptApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    
    // Preferences
    var isAlarmEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("alarm_enabled", false)) }
    var alarmStartHour by remember { mutableStateOf(sharedPrefs.getInt("alarm_start", 14)) } 
    var alarmEndHour by remember { mutableStateOf(sharedPrefs.getInt("alarm_end", 22)) }
    var autoCameraLaunch by remember { mutableStateOf(sharedPrefs.getBoolean("auto_camera_launch", true)) }
    var saveToGallery by remember { mutableStateOf(sharedPrefs.getBoolean("save_to_gallery", false)) }
    var useTenThousandUnit by remember { mutableStateOf(sharedPrefs.getBoolean("use_ten_thousand_unit", true)) }

    // State
    var capturedText by remember { mutableStateOf("") }
    var memoContent by remember { mutableStateOf(sharedPrefs.getString("memo_content", "") ?: "") }
    var showMemo by remember { mutableStateOf(false) }
    
    var showCamera by remember { mutableStateOf(autoCameraLaunch) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var scanMode by remember { mutableStateOf(ScanMode.SAFE) }
    var disposalBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var showAlarmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTranslatorDialog by remember { mutableStateOf(false) }
    var showGalleryCamera by remember { mutableStateOf(false) }

    // Tutorial State
    var isTutorialFinished by remember { mutableStateOf(sharedPrefs.getBoolean("tutorial_finished", false)) }
    var tutorialStep by remember { mutableStateOf(if (isTutorialFinished) -1 else 0) }
    var showAppGallery by remember { mutableStateOf(false) }

    // UI Bounds
    var imageBoxBounds by remember { mutableStateOf<Rect?>(null) }
    var resultFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var shareButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var alarmRowBounds by remember { mutableStateOf<Rect?>(null) }

    // Update check
    val coroutineScope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { updateInfo = checkForUpdate(context) } catch (e: Exception) { }
        withContext(Dispatchers.IO) {
            val yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            listOf(File(context.cacheDir, "images")).forEach { dir ->
                dir.listFiles()?.forEach { if (it.lastModified() < yesterday) it.delete() }
            }
        }
        autoDownloadMandatoryModels(context)
    }

    // Helper to process image
    fun processCurrentBitmap(bitmap: Bitmap?) {
        bitmap?.let {
            processImage(it, true, true, useTenThousandUnit) { result ->
                capturedText = result
            }
        }
    }

    // 파손 보고: 사진 한 장에서 상품명·수량 1건을 인식해 "OO 3개 파손입니다" 형식으로 반환
    fun processDamageBitmap(bitmap: Bitmap?) {
        bitmap?.let {
            coroutineScope.launch {
                capturedText = try {
                    val text = recognizeTextFromBitmap(it)
                    val item = parseDisposalLabel(text).firstOrNull()
                    item?.let { found -> formatDamageReport(found) } ?: "인식 실패: 라벨을 다시 촬영해주세요"
                } catch (e: Exception) {
                    "인식 실패: ${e.message}"
                }
            }
        }
    }

    // 폐기 목록: 갤러리에서 선택한 사진 여러 장을 각각 인식해 하나의 목록으로 합침
    fun processDisposalBitmaps(bitmaps: List<Bitmap>) {
        coroutineScope.launch {
            val items = mutableListOf<DisposalItem>()
            for (bmp in bitmaps) {
                try {
                    items.addAll(parseDisposalLabel(recognizeTextFromBitmap(bmp)))
                } catch (e: Exception) { }
            }
            capturedText = formatDisposalList(items)
        }
    }

    fun resetScanState() {
        capturedText = ""
        currentBitmap = null
        disposalBitmaps = emptyList()
    }

    // Alarm Logic Helper
    fun updateAlarms(enabled: Boolean, start: Int, end: Int) {
        if (enabled) {
            scheduleAlarms(context, start, end)
            Toast.makeText(context, "${start}시~${end}시 사이 짝수 시간 5분 전에 알림이 설정되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            cancelAlarms(context)
            Toast.makeText(context, "알림이 해제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // Contact Developer
    fun contactDeveloper() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("")) 
            putExtra(Intent.EXTRA_SUBJECT, "[금고보관 앱 문의]")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "이메일 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Smart BackHandler
    BackHandler(enabled = true) {
        if (showSettingsDialog) { showSettingsDialog = false }
        else if (showAlarmDialog) { showAlarmDialog = false }
        else if (showTranslatorDialog) { showTranslatorDialog = false }
        else if (showGalleryCamera) { showGalleryCamera = false }
        else if (showCamera) {
             if (currentBitmap != null) { showCamera = false }
             else { activity?.finish() }
        } else {
             activity?.finish()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            updateAlarms(true, alarmStartHour, alarmEndHour)
        } else {
            Toast.makeText(context, "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            isAlarmEnabled = false
            sharedPrefs.edit().putBoolean("alarm_enabled", false).apply()
        }
    }

    // 폰 갤러리 → 앱 내부 갤러리 저장 전용 런처
    val galleryToAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                saveToAppGallery(context, loadBitmapFromUri(context, uri))
                Toast.makeText(context, "갤러리에 저장되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "이미지 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // OCR용 런처 - 선택한 사진을 분석해 결과 화면에 표시
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = loadBitmapFromUri(context, uri)
                currentBitmap = bitmap
                processCurrentBitmap(bitmap)
                showCamera = false
            } catch (e: Exception) {
                Toast.makeText(context, "이미지 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 폐기 목록용 - 갤러리에서 여러 장 선택
    val disposalGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.mapNotNull { uri ->
                try { loadBitmapFromUri(context, uri) } catch (e: Exception) { null }
            }
            disposalBitmaps = bitmaps
            processDisposalBitmaps(bitmaps)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showGalleryCamera) {
                CameraView(
                    saveToGallery = false,
                    showGalleryButton = true,
                    onImageCaptured = { bitmap ->
                        saveToAppGallery(context, bitmap)
                        Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { exc ->
                        Toast.makeText(context, "캡처 실패: ${exc.message}", Toast.LENGTH_SHORT).show()
                    },
                    onGalleryClick = { galleryToAppLauncher.launch("image/*") },
                    onClose = { showGalleryCamera = false }
                )
            } else if (showCamera) {
                CameraView(
                    saveToGallery = saveToGallery,
                    onImageCaptured = { bitmap ->
                        showCamera = false
                        currentBitmap = bitmap
                        when (scanMode) {
                            ScanMode.SAFE -> processCurrentBitmap(bitmap)
                            ScanMode.DAMAGE -> processDamageBitmap(bitmap)
                            ScanMode.DISPOSAL -> processCurrentBitmap(bitmap)
                        }
                        saveToAppGallery(context, bitmap)
                    },
                    onError = { exc ->
                        Toast.makeText(context, "캡처 실패: ${exc.message}", Toast.LENGTH_SHORT).show()
                        showCamera = false
                    },
                    onGalleryClick = {
                        galleryLauncher.launch("image/*")
                    },
                    onClose = {
                        showCamera = false
                    }
                )
            } else {
                if (showAlarmDialog) {
                    AlarmConfigDialog(
                        initialStart = alarmStartHour,
                        initialEnd = alarmEndHour,
                        onDismiss = { showAlarmDialog = false },
                        onConfirm = { start, end ->
                            alarmStartHour = start
                            alarmEndHour = end
                            sharedPrefs.edit().putInt("alarm_start", start).putInt("alarm_end", end).apply()
                            
                            if (isAlarmEnabled) {
                                updateAlarms(true, start, end)
                            }
                            showAlarmDialog = false
                        }
                    )
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        autoLaunch = autoCameraLaunch,
                        saveGallery = saveToGallery,
                        useTenThousandUnit = useTenThousandUnit,
                        initialAlarmStart = alarmStartHour,
                        initialAlarmEnd = alarmEndHour,
                        onDismiss = { showSettingsDialog = false },
                        onContactDeveloper = { contactDeveloper() },
                        onUpdatePrefs = { auto, save, tenThousand ->
                            autoCameraLaunch = auto
                            saveToGallery = save
                            useTenThousandUnit = tenThousand
                            sharedPrefs.edit()
                                .putBoolean("auto_camera_launch", auto)
                                .putBoolean("save_to_gallery", save)
                                .putBoolean("use_ten_thousand_unit", tenThousand)
                                .apply()
                            
                            if (currentBitmap != null) {
                                processCurrentBitmap(currentBitmap)
                            }
                        },
                        onAlarmConfigConfirm = { start, end ->
                            alarmStartHour = start
                            alarmEndHour = end
                            sharedPrefs.edit().putInt("alarm_start", start).putInt("alarm_end", end).apply()
                            if (isAlarmEnabled) {
                                updateAlarms(true, start, end)
                            }
                        }
                    )
                }
                
                if (showTranslatorDialog) {
                    TranslatorView(onDismiss = { showTranslatorDialog = false })
                }

                if (showAppGallery) {
                    AppGalleryView(onDismiss = { showAppGallery = false })
                }

                ResultView(
                    initialText = capturedText,
                    currentBitmap = currentBitmap,
                    scanMode = scanMode,
                    disposalBitmaps = disposalBitmaps,
                    onModeToggle = {
                        scanMode = scanMode.next()
                        resetScanState()
                    },
                    isAlarmEnabled = isAlarmEnabled,
                    memoContent = memoContent,
                    showMemo = showMemo,
                    onMemoChange = {
                        memoContent = it
                        sharedPrefs.edit().putString("memo_content", it).apply()
                    },
                    onMemoToggle = { showMemo = !showMemo },
                    onTranslatorClick = { showTranslatorDialog = true },
                    onGalleryClick = { showAppGallery = true },
                    onGalleryCameraClick = { showGalleryCamera = true },
                    onAlarmToggle = { enabled ->
                        isAlarmEnabled = enabled
                        sharedPrefs.edit().putBoolean("alarm_enabled", enabled).apply()

                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    updateAlarms(true, alarmStartHour, alarmEndHour)
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                updateAlarms(true, alarmStartHour, alarmEndHour)
                            }
                        } else {
                            updateAlarms(false, alarmStartHour, alarmEndHour)
                        }
                    },
                    onGeneralSettingsClick = { showSettingsDialog = true },
                    onReplayTutorial = {
                        isTutorialFinished = false
                        tutorialStep = 0
                        sharedPrefs.edit().putBoolean("tutorial_finished", false).apply()
                    },
                    onImageClick = {
                        when (scanMode) {
                            ScanMode.SAFE, ScanMode.DAMAGE -> showCamera = true
                            ScanMode.DISPOSAL -> disposalGalleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onReportBounds = { key, rect ->
                        when(key) {
                            "imageBox" -> imageBoxBounds = rect
                            "resultField" -> resultFieldBounds = rect
                            "shareButton" -> shareButtonBounds = rect
                            "alarmRow" -> alarmRowBounds = rect
                        }
                    }
                )

                if (!isTutorialFinished && tutorialStep >= 0) {
                    TutorialOverlay(
                        step = tutorialStep,
                        imageBounds = imageBoxBounds,
                        resultBounds = resultFieldBounds,
                        shareBounds = shareButtonBounds,
                        alarmBounds = alarmRowBounds,
                        onNext = {
                            if (tutorialStep < 5) {
                                tutorialStep++
                            } else {
                                tutorialStep = -1
                                isTutorialFinished = true
                                sharedPrefs.edit().putBoolean("tutorial_finished", true).apply()
                            }
                        }
                    )
                }
            }

            updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    isDownloading = isDownloadingUpdate,
                    onDismiss = { updateInfo = null },
                    onConfirm = {
                        coroutineScope.launch {
                            isDownloadingUpdate = true
                            try {
                                downloadAndInstallApk(context, info.apkUrl, info.sha256)
                            } catch (e: Exception) {
                                Toast.makeText(context, "다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isDownloadingUpdate = false
                                updateInfo = null
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    autoLaunch: Boolean,
    saveGallery: Boolean,
    useTenThousandUnit: Boolean,
    initialAlarmStart: Int,
    initialAlarmEnd: Int,
    onDismiss: () -> Unit,
    onContactDeveloper: () -> Unit,
    onUpdatePrefs: (Boolean, Boolean, Boolean) -> Unit,
    onAlarmConfigConfirm: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    var auto by remember { mutableStateOf(autoLaunch) }
    var save by remember { mutableStateOf(saveGallery) }
    var tenThousand by remember { mutableStateOf(useTenThousandUnit) }
    var showInternalAlarmDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var settingsUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingFromSettings by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf("") }
    var gallerySize by remember { mutableStateOf("") }

    // Language Pack States
    val modelManager = remember { RemoteModelManager.getInstance() }
    var koDownloaded by remember { mutableStateOf(false) }
    var enDownloaded by remember { mutableStateOf(false) }
    var jaDownloaded by remember { mutableStateOf(false) }
    var zhDownloaded by remember { mutableStateOf(false) }
    
    // Loading states for each language
    var processingModels by remember { mutableStateOf(mapOf<String, Boolean>()) }

    fun checkModels() {
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                koDownloaded = models.any { it.language == TranslateLanguage.KOREAN }
                enDownloaded = models.any { it.language == TranslateLanguage.ENGLISH }
                jaDownloaded = models.any { it.language == TranslateLanguage.JAPANESE }
                zhDownloaded = models.any { it.language == TranslateLanguage.CHINESE }
            }
    }

    LaunchedEffect(Unit) {
        checkModels()
        withContext(Dispatchers.IO) {
            val cacheBytes = listOf(File(context.cacheDir, "images"), File(context.cacheDir, "updates"))
                .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            val galleryBytes = File(context.filesDir, "gallery")
                .walkTopDown().filter { it.isFile }.sumOf { it.length() }
            withContext(Dispatchers.Main) {
                cacheSize = formatSize(cacheBytes)
                gallerySize = formatSize(galleryBytes)
            }
        }
    }

    fun manageModel(language: String, languageName: String, isDownloaded: Boolean, isMandatory: Boolean) {
        val model = TranslateRemoteModel.Builder(language).build()
        
        if (isDownloaded && !isMandatory) {
            processingModels = processingModels.toMutableMap().apply { put(language, true) }
            modelManager.deleteDownloadedModel(model)
                .addOnSuccessListener {
                    Toast.makeText(context, "$languageName 언어팩 삭제 완료", Toast.LENGTH_SHORT).show()
                    processingModels = processingModels.toMutableMap().apply { put(language, false) }
                    checkModels()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "$languageName 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    processingModels = processingModels.toMutableMap().apply { put(language, false) }
                }
        } else if (!isDownloaded) {
            processingModels = processingModels.toMutableMap().apply { put(language, true) }
            // 기본 언어팩(한국어·영어)은 WiFi 없이도 다운로드, 선택 언어팩은 WiFi 필요
            val conditions = if (isMandatory) DownloadConditions.Builder().build()
                             else DownloadConditions.Builder().requireWifi().build()

            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    Toast.makeText(context, "$languageName 언어팩 다운로드 완료!", Toast.LENGTH_SHORT).show()
                    processingModels = processingModels.toMutableMap().apply { put(language, false) }
                    checkModels()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "$languageName 다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    processingModels = processingModels.toMutableMap().apply { put(language, false) }
                }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("기능", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("앱 시작 시 카메라 자동 실행")
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(" 촬영 후 갤러리에 저장")
                    Switch(checked = save, onCheckedChange = { save = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("만원 단위 표기")
                        Text("(1만원 이상만 인식)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = tenThousand, onCheckedChange = { tenThousand = it })
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showInternalAlarmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text("알림 시간 설정", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text("언어팩 관리 (통역용)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("언어를 다운받아보세요.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                @Composable
                fun LanguageRow(name: String, languageCode: String, isDownloaded: Boolean, isMandatory: Boolean) {
                    val isProcessing = processingModels[languageCode] == true
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name)
                            if (isMandatory) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(기본)", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        if (isProcessing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("처리 중...", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            if (isMandatory) {
                                if (isDownloaded) {
                                    Text("설치됨", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("자동 설치 중", fontSize = 12.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { manageModel(languageCode, name, isDownloaded, false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (isDownloaded) "삭제" else "다운로드", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                LanguageRow("한국어", TranslateLanguage.KOREAN, koDownloaded, true)
                LanguageRow("영어", TranslateLanguage.ENGLISH, enDownloaded, true)
                LanguageRow("일본어", TranslateLanguage.JAPANESE, jaDownloaded, false)
                LanguageRow("중국어", TranslateLanguage.CHINESE, zhDownloaded, false)

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text("저장소", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("캐시")
                        Text("공유·업데이트 임시 파일", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cacheSize.isNotEmpty()) Text(cacheSize, color = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    listOf(File(context.cacheDir, "images"), File(context.cacheDir, "updates"))
                                        .forEach { dir -> dir.listFiles()?.forEach { it.delete() } }
                                    val bytes = listOf(File(context.cacheDir, "images"), File(context.cacheDir, "updates"))
                                        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
                                    withContext(Dispatchers.Main) {
                                        cacheSize = formatSize(bytes)
                                        Toast.makeText(context, "캐시를 정리했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("지우기", fontSize = 12.sp) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("앱 갤러리")
                        Text("저장된 사진 전체 용량", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    if (gallerySize.isNotEmpty()) Text(gallerySize, color = Color.Gray)
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                Text("정보", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("앱 버전")
                    Text("1.2.7", color = Color.Gray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("업데이트 확인")
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    val info = try { checkForUpdate(context) } catch (e: Exception) { null }
                                    isCheckingUpdate = false
                                    if (info != null) settingsUpdateInfo = info
                                    else Toast.makeText(context, "최신 버전입니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("확인") }
                    }
                }

                TextButton(
                    onClick = { showLicenseDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("오픈소스 라이선스", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                TextButton(
                    onClick = onContactDeveloper,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("개발자에게 문의하기")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "made by 검암동",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpdatePrefs(auto, save, tenThousand)
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )

    if (showInternalAlarmDialog) {
        AlarmConfigDialog(
            initialStart = initialAlarmStart,
            initialEnd = initialAlarmEnd,
            onDismiss = { showInternalAlarmDialog = false },
            onConfirm = { start, end ->
                onAlarmConfigConfirm(start, end)
                showInternalAlarmDialog = false
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("오픈소스 라이선스") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("이 앱은 다음 오픈소스 라이브러리를 사용합니다:\n\n" +
                            "- Google ML Kit: Vision Text Recognition\n" +
                            "- Google ML Kit: On-device Translation\n" +
                            "- Jetpack Compose\n" +
                            "- CameraX\n" +
                            "\n" +
                            "자세한 라이선스 정보는 각 프로젝트의 공식 홈페이지를 참조하세요.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text("닫기") }
            }
        )
    }

    settingsUpdateInfo?.let { info ->
        UpdateDialog(
            info = info,
            isDownloading = isDownloadingFromSettings,
            onDismiss = { settingsUpdateInfo = null },
            onConfirm = {
                coroutineScope.launch {
                    isDownloadingFromSettings = true
                    try {
                        downloadAndInstallApk(context, info.apkUrl, info.sha256)
                    } catch (e: Exception) {
                        Toast.makeText(context, "다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isDownloadingFromSettings = false
                        settingsUpdateInfo = null
                    }
                }
            }
        )
    }
}

@Composable
fun TranslatorView(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    
    var targetLanguage by remember { mutableStateOf(TranslateLanguage.ENGLISH) }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("대기 중") }
    var inputText by remember { mutableStateOf("") }
    
    // Tutorial State
    var isTutorialFinished by remember { mutableStateOf(sharedPrefs.getBoolean("translator_tutorial_finished", false)) }
    var tutorialStep by remember { mutableStateOf(if (isTutorialFinished) -1 else 0) }
    
    // Bounds
    var langBounds by remember { mutableStateOf<Rect?>(null) }
    var inputBounds by remember { mutableStateOf<Rect?>(null) }
    var topViewBounds by remember { mutableStateOf<Rect?>(null) }
    
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var translator by remember { mutableStateOf<Translator?>(null) }
    var isKoreanDownloaded by remember { mutableStateOf(false) }

    // TTS Initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    
    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e("TTS", "Initialization failed")
            }
        }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    fun speak(text: String, languageCode: String) {
        if (tts == null) return
        val locale = when(languageCode) {
            TranslateLanguage.ENGLISH -> Locale.ENGLISH
            TranslateLanguage.JAPANESE -> Locale.JAPANESE
            TranslateLanguage.CHINESE -> Locale.CHINESE
            TranslateLanguage.KOREAN -> Locale.KOREA
            else -> Locale.ENGLISH
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(context, "TTS 언어 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    // Check models (Source + Target)
    LaunchedEffect(targetLanguage) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.KOREAN)
            .setTargetLanguage(targetLanguage)
            .build()
        val newTranslator = Translation.getClient(options)
        
        val modelManager = RemoteModelManager.getInstance()
        val targetModel = TranslateRemoteModel.Builder(targetLanguage).build()
        val koreanModel = TranslateRemoteModel.Builder(TranslateLanguage.KOREAN).build()
        
        // Check both models
        modelManager.isModelDownloaded(koreanModel).addOnSuccessListener { ko ->
            isKoreanDownloaded = ko
            modelManager.isModelDownloaded(targetModel).addOnSuccessListener { target ->
                if (ko && target) {
                    translator = newTranslator
                    statusText = "통역 준비 완료"
                } else {
                    translator = null
                    val missing = mutableListOf<String>()
                    if (!ko) missing.add("한국어")
                    if (!target) missing.add("해당 외국어")
                    statusText = "${missing.joinToString(", ")} 언어팩 필요 (설정에서 다운로드)"
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {}
            translator?.close()
        }
    }

    fun translateAndAdd(text: String, isUser: Boolean) {
        if (translator == null) {
            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (text.isBlank()) return

        if (isUser) {
            translator!!.translate(text)
                .addOnSuccessListener { translated ->
                    chatHistory = chatHistory + ChatMessage(text, translated, true)
                    statusText = "대기 중"
                }
                .addOnFailureListener { e ->
                    statusText = "번역 실패: ${e.message}"
                    Log.e("Translator", "Translation failed", e)
                }
        } else {
             chatHistory = chatHistory + ChatMessage(text, "(번역 불가 - 한국어 번역기 필요)", false)
        }
    }

    fun toggleListening() {
        if (isListening) {
            speechRecognizer.stopListening()
            isListening = false
            statusText = "음성 인식 종료"
        } else {
            if (translator == null) {
                 Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                 return
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusText = "말씀하세요..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { 
                    isListening = false
                    statusText = "번역 중..."
                }
                override fun onError(error: Int) { 
                    isListening = false
                    statusText = "오류 발생: $error" 
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.get(0) ?: ""
                    if (text.isNotEmpty()) {
                        translateAndAdd(text, true)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            try {
                speechRecognizer.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Toast.makeText(context, "음성 인식 시작 실패", Toast.LENGTH_SHORT).show()
                isListening = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(700.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("실시간 통역", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { targetLanguage = TranslateLanguage.ENGLISH }, 
                        colors = ButtonDefaults.buttonColors(containerColor = if(targetLanguage == TranslateLanguage.ENGLISH) MaterialTheme.colorScheme.primary else Color.LightGray)) { Text("영어") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { targetLanguage = TranslateLanguage.JAPANESE },
                        colors = ButtonDefaults.buttonColors(containerColor = if(targetLanguage == TranslateLanguage.JAPANESE) MaterialTheme.colorScheme.primary else Color.LightGray)) { Text("일본어") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { targetLanguage = TranslateLanguage.CHINESE },
                        colors = ButtonDefaults.buttonColors(containerColor = if(targetLanguage == TranslateLanguage.CHINESE) MaterialTheme.colorScheme.primary else Color.LightGray)) { Text("중국어") }
                }
                
                Text(statusText, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp), color = Color.Gray)
                
                Divider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(16.dp)
                        .clickable {
                             val lastMsg = chatHistory.lastOrNull()
                             if (lastMsg != null && lastMsg.isUser) {
                                 speak(lastMsg.translatedText, targetLanguage)
                             }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val lastMsg = chatHistory.lastOrNull()
                    if (lastMsg != null && lastMsg.isUser) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = lastMsg.translatedText,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.rotate(180f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                             Text(
                                text = "(터치하여 듣기)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.rotate(180f)
                            )
                        }
                    } else {
                        Text(
                            text = "인터넷 연결 시\n\n여기에 상대방을 위한\n통역 결과가 표시됩니다",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            modifier = Modifier.rotate(180f)
                        )
                    }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    reverseLayout = true
                ) {
                    items(chatHistory.reversed()) { msg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.clickable {
                                    val textToRead = msg.translatedText
                                    val langToRead = if (msg.isUser) targetLanguage else TranslateLanguage.KOREAN
                                    if (!textToRead.startsWith("(")) {
                                        speak(textToRead, langToRead)
                                    }
                                }
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
                                    Divider(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text(text = msg.translatedText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                Divider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { toggleListening() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = "Mic",
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("텍스트 입력...") },
                        maxLines = 1
                    )
                    
                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            translateAndAdd(inputText, true)
                            inputText = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmConfigDialog(
    initialStart: Int,
    initialEnd: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var startText by remember { mutableStateOf(initialStart.toString()) }
    var endText by remember { mutableStateOf(initialEnd.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림 시간 설정") },
        text = {
            Column {
                Text("근무 시작 시간과 종료 시간을 입력하세요.\n(설정된 시간의 5분 전에 알림이 울립니다)")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = startText,
                    onValueChange = { if (it.length <= 2) startText = it.filter { c -> c.isDigit() } },
                    label = { Text("시작 시간 (0-23)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { if (it.length <= 2) endText = it.filter { c -> c.isDigit() } },
                    label = { Text("종료 시간 (0-23)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startText.toIntOrNull() ?: 0
                val end = endText.toIntOrNull() ?: 23
                if (start in 0..23 && end in 0..23 && start <= end) {
                    onConfirm(start, end)
                } else {
                    // Simple error handling: just don't dismiss if invalid
                }
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun ResultView(
    initialText: String,
    currentBitmap: Bitmap?,
    scanMode: ScanMode,
    disposalBitmaps: List<Bitmap>,
    onModeToggle: () -> Unit,
    isAlarmEnabled: Boolean,
    memoContent: String,
    showMemo: Boolean,
    onMemoChange: (String) -> Unit,
    onMemoToggle: () -> Unit,
    onTranslatorClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onGalleryCameraClick: () -> Unit,
    onAlarmToggle: (Boolean) -> Unit,
    onGeneralSettingsClick: () -> Unit,
    onReplayTutorial: () -> Unit,
    onImageClick: () -> Unit,
    onReportBounds: (String, Rect) -> Unit
) {
    // remember(initialText)가 key 변경 시 상태를 재생성하므로 LaunchedEffect 불필요
    var text by remember(initialText) { mutableStateOf(initialText) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = scanMode.label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onModeToggle() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Alarm Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .onGloballyPositioned { coordinates ->
                    onReportBounds("alarmRow", coordinates.boundsInRoot())
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Translator Icon
            IconButton(onClick = onTranslatorClick) {
                Icon(Icons.Filled.Translate, contentDescription = "통역")
            }

            // App Gallery Icon
            IconButton(onClick = onGalleryClick) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "앱 갤러리")
            }

            // Gallery Camera Icon
            IconButton(onClick = onGalleryCameraClick) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "갤러리 카메라")
            }
            
            // Memo Icon
            IconButton(onClick = onMemoToggle) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "업무 일지",
                    tint = if (showMemo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "알림",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp)
            )
            Switch(
                checked = isAlarmEnabled,
                onCheckedChange = onAlarmToggle,
                modifier = Modifier.scale(0.8f)
            )
            Box {
                IconButton(onClick = { showSettingsMenu = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "설정")
                }
                DropdownMenu(
                    expanded = showSettingsMenu,
                    onDismissRequest = { showSettingsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("일반 설정") },
                        onClick = {
                            showSettingsMenu = false
                            onGeneralSettingsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("사용법 다시보기") },
                        onClick = {
                            showSettingsMenu = false
                            onReplayTutorial()
                        }
                    )
                }
            }
        }

        if (showMemo) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = memoContent,
                onValueChange = onMemoChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                label = { Text("메모장") },
                placeholder = { Text("인수인계 사항이나 폐기 내역 등을 적어두세요.") },
                maxLines = 5
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .onGloballyPositioned { coordinates ->
                    onReportBounds("imageBox", coordinates.boundsInRoot())
                }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onImageClick() }
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (scanMode == ScanMode.DISPOSAL && disposalBitmaps.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(disposalBitmaps) { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "폐기 사진",
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            } else if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Receipt Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add Photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (scanMode == ScanMode.DISPOSAL) "터치하여 폐기 사진 선택 (여러 장 가능)" else "터치하여 사진 등록",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    onReportBounds("resultField", coordinates.boundsInRoot())
                },
            label = { Text("결과 (수정 가능)") },
            maxLines = 10
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Receipt", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "결과가 복사되었습니다.", Toast.LENGTH_SHORT).show()

                if (scanMode != ScanMode.DISPOSAL) {
                    if (currentBitmap != null) {
                        shareImageOnly(context, currentBitmap)
                    } else {
                        Toast.makeText(context, "공유할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onGloballyPositioned { coordinates ->
                    onReportBounds("shareButton", coordinates.boundsInRoot())
                }
        ) {
            Text("공유 (복사 및 전송)", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// startHour~endHour 중 짝수 시간의 5분 전에 각각 exact alarm 등록
fun scheduleAlarms(context: Context, startHour: Int, endHour: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val hours = (startHour..endHour).filter { it % 2 == 0 }
    cancelAlarms(context) 

    hours.forEach { hour ->
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hour, 
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.MINUTE, -5) 

            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Toast.makeText(context, "알람 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun cancelAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    // 이전에 어느 시간대가 등록됐는지 알 수 없으므로 0~24 전체 취소
    for (hour in 0..24) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            hour,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

// OCR 결과 화면의 이미지를 JPEG로 압축해 외부 앱과 공유
fun shareImageOnly(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val shareFile = File(cachePath, "share_image.jpg")
        FileOutputStream(shareFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "com.example.receiptmanager.fileprovider",
            shareFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(null, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val shareIntent = Intent.createChooser(intent, "이미지 공유")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "공유 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        Log.e("shareImageOnly", "공유 실패", e)
    }
}

@Composable
fun CameraView(
    saveToGallery: Boolean,
    showGalleryButton: Boolean = true,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit,
    onGalleryClick: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        // 기기마다 기본 배율이 다를 수 있으므로 항상 1x로 초기화
                        camera.cameraControl.setZoomRatio(1.0f)

                        val scaleGestureDetector = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                val delta = detector.scaleFactor
                                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                                return true
                            }
                        })

                        previewView.setOnTouchListener {
                            view, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                                camera.cameraControl.startFocusAndMetering(action)
                                view.performClick()
                            }
                            true
                        }

                    } catch (exc: Exception) {
                        Log.e("CameraView", "Use case binding failed", exc)
                        onError(exc)
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        if (showGalleryButton) IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "갤러리",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(80.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.Transparent)
                .border(4.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                .clickable {
                    val executor = ContextCompat.getMainExecutor(context)
                    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val bitmap = image.toBitmap()
                            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            
                            if (saveToGallery) {
                                saveBitmapToGallery(context, rotatedBitmap)
                            }
                            
                            onImageCaptured(rotatedBitmap)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception)
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(65.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// 폰 사진첩(MediaStore)에 저장 - API 29 이상은 ContentValues, 미만은 파일 직접 생성
fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "Receipt_${System.currentTimeMillis()}.jpg"
    var fos: java.io.OutputStream? = null
    var uri: Uri? = null

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ReceiptManager")
            }
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/ReceiptManager"
            val file = File(imagesDir)
            if (!file.exists()) file.mkdirs()
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(context, "갤러리에 저장됨", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        Log.e("saveBitmapToGallery", "폰 갤러리 저장 실패", e)
    }
}

// 비트맵에서 텍스트 인식 결과(Text)를 suspend 함수로 반환
suspend fun recognizeTextFromBitmap(bitmap: Bitmap): com.google.mlkit.vision.text.Text =
    suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

// CU 폐기 라벨은 "Again" 문구가 고정으로 찍히므로, 이를 기준으로 바로 다음 줄(상품명×수량)만 읽어
// 배경에 같이 찍힌 제품 포장지의 유통기한·영양정보 등은 무시한다.
fun parseDisposalLabel(visionText: com.google.mlkit.vision.text.Text): List<DisposalItem> {
    val lines = visionText.textBlocks.flatMap { it.lines }.map { it.text.trim() }
    val quantityRegex = Regex("[×xX]\\s*(\\d+)\\s*$")
    val items = mutableListOf<DisposalItem>()
    for (i in lines.indices) {
        if (lines[i].contains("again", ignoreCase = true)) {
            val nextLine = lines.getOrNull(i + 1) ?: continue
            val match = quantityRegex.find(nextLine) ?: continue
            val quantity = match.groupValues[1].toIntOrNull() ?: continue
            val name = nextLine.substring(0, match.range.first).trim()
            if (name.isNotEmpty()) items.add(DisposalItem(name, quantity))
        }
    }
    return items
}

// 폐기: 오늘 날짜 + 상품명·수량 목록
fun formatDisposalList(items: List<DisposalItem>): String {
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val sb = StringBuilder("${month}월${day}일(금일) 폐기 목록입니다")
    items.forEach { sb.append("\n${it.name} ${it.quantity}개") }
    return sb.toString()
}

// 파손: 단건 보고 문구
fun formatDamageReport(item: DisposalItem): String = "${item.name} ${item.quantity}개 파손입니다"

// ML Kit로 비트맵에서 텍스트 인식 후 파싱 함수에 전달
fun processImage(bitmap: Bitmap, isPos1Checked: Boolean, isPos2Checked: Boolean, useTenThousandUnit: Boolean, onResult: (String) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            val result = parseReceiptText(visionText, isPos1Checked, isPos2Checked, useTenThousandUnit)
            onResult(result)
        }
        .addOnFailureListener { e ->
            onResult("텍스트 인식 실패: ${e.message}")
        }
}

// OCR 결과에서 POS1/POS2 금액을 추출해 "N시 금고보관\nPOS1-X원\nPOS2-Y원" 형식으로 반환
fun parseReceiptText(visionText: com.google.mlkit.vision.text.Text, isPos1Checked: Boolean, isPos2Checked: Boolean, useTenThousandUnit: Boolean): String {
    val textBlocks = visionText.textBlocks
    val calendar = Calendar.getInstance()
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(Calendar.MINUTE)
    val timeInHours = currentHour + (currentMinute / 60.0)
    var nearestEven = (timeInHours / 2.0).roundToInt() * 2
    if (nearestEven == 24) nearestEven = 0
    val formattedTime = "$nearestEven"

    data class ExtractedAmount(val value: Int, val x: Int)
    val amounts = mutableListOf<ExtractedAmount>()

    val minAmount = if (useTenThousandUnit) 10000 else 10

    for (block in textBlocks) {
        for (line in block.lines) {
            val lineText = line.text
            val lineBox = line.boundingBox

            if (lineText.contains(",")) {
                val cleanText = lineText.replace(Regex("[^0-9]"), "")
                if (cleanText.isNotEmpty()) {
                    try {
                        val value = cleanText.toInt()
                        if (value >= minAmount && value < 100000000) {
                            if (!lineText.contains("202") && !lineText.contains("년")) {
                                amounts.add(ExtractedAmount(value, lineBox?.centerX() ?: 0))
                            }
                        }
                    } catch (e: NumberFormatException) { }
                }
            }
        }
    }

    val sb = StringBuilder()
    sb.append("${formattedTime}시 금고보관")

    val distinctAmounts = amounts.distinctBy { it.value }.sortedBy { it.x }

    var detectedPos1 = false
    var detectedPos2 = false

    for (block in textBlocks) {
        for (line in block.lines) {
            val text = line.text.trim()
            val lowerText = text.lowercase()

            if (text == "0001" || text == "1") detectedPos1 = true
            if (text == "0002" || text == "2") detectedPos2 = true

            if (lowerText.contains("pos") || text.contains("포스")) {
                val cleaned = lowerText.replace(Regex("[^a-z0-9\\-\\:]"), "")
                if (cleaned.contains("pos1") || cleaned.contains("pos-1") || cleaned.contains("pos:1") || 
                    cleaned.contains("pos01") || cleaned.contains("pos-01") || cleaned.contains("pos:01")) detectedPos1 = true
                if (cleaned.contains("pos2") || cleaned.contains("pos-2") || cleaned.contains("pos:2") || 
                    cleaned.contains("pos02") || cleaned.contains("pos-02") || cleaned.contains("pos:02")) detectedPos2 = true
                
                if (lowerText.contains("1") && !lowerText.contains("10") && !lowerText.contains("11") && !lowerText.contains("12")) {
                     val indexPos = lowerText.indexOf("pos")
                     val index1 = lowerText.indexOf("1")
                     if (kotlin.math.abs(indexPos - index1) < 10) detectedPos1 = true
                }
                if (lowerText.contains("2") && !lowerText.contains("20")) {
                     val indexPos = lowerText.indexOf("pos")
                     val index2 = lowerText.indexOf("2")
                     if (kotlin.math.abs(indexPos - index2) < 10) detectedPos2 = true
                }
            }

            if (lowerText.contains("no") || lowerText.contains("w")) {
                val regex1 = Regex("(?:no|w)[\\D]*0?1(?!\\d)", RegexOption.IGNORE_CASE)
                val regex2 = Regex("(?:no|w)[\\D]*0?2(?!\\d)", RegexOption.IGNORE_CASE)
                if (regex1.containsMatchIn(text)) detectedPos1 = true
                if (regex2.containsMatchIn(text)) detectedPos2 = true
            }
        }
    }

    fun formatAmount(valInt: Int): String {
        return if (useTenThousandUnit) {
            "${valInt / 10000}만원"
        } else {
            java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(valInt) + "원"
        }
    }

    if (distinctAmounts.isEmpty()) {
        val unit = if (useTenThousandUnit) "만원" else "원"
        sb.append("\nPOS1-??$unit")
        sb.append("\nPOS2-??$unit")
    } else if (distinctAmounts.size == 1) {
        val amount = distinctAmounts[0]
        if (isPos1Checked && !isPos2Checked) {
            sb.append("\nPOS1-${formatAmount(amount.value)}")
            sb.append("\nPOS2-0원")
        } else if (!isPos1Checked && isPos2Checked) {
            sb.append("\nPOS1-0원")
            sb.append("\nPOS2-${formatAmount(amount.value)}")
        } else {
            val isPos1 = detectedPos1 && !detectedPos2
            if (isPos1) {
                sb.append("\nPOS1-${formatAmount(amount.value)}")
                sb.append("\nPOS2-0원")
            } else {
                sb.append("\nPOS1-0원")
                sb.append("\nPOS2-${formatAmount(amount.value)}")
            }
        }
    } else {
        if (isPos1Checked && !isPos2Checked) {
             sb.append("\nPOS1-${formatAmount(distinctAmounts[0].value)}")
             sb.append("\nPOS2-0원")
        } else if (!isPos1Checked && isPos2Checked) {
             sb.append("\nPOS1-0원")
             sb.append("\nPOS2-${formatAmount(distinctAmounts[0].value)}")
        } else {
            val finalAmounts = distinctAmounts.take(2)
            sb.append("\nPOS1-${formatAmount(finalAmounts[0].value)}")
            sb.append("\nPOS2-${formatAmount(finalAmounts[1].value)}")
        }
    }

    return sb.toString()
}

@Composable
fun TutorialOverlay(
    step: Int,
    imageBounds: Rect?,
    resultBounds: Rect?,
    shareBounds: Rect?,
    alarmBounds: Rect?,
    onNext: () -> Unit
) {
    val targetRect = when (step) {
        0 -> null
        1 -> imageBounds
        2 -> resultBounds
        3 -> shareBounds
        4 -> alarmBounds
        5 -> alarmBounds
        else -> null
    }

    val guideText = when (step) {
        0 -> "안녕하세요.\n\n편한 CU 금고보관을 위한 어플입니다"
        1 -> "이 부분을 클릭하여\n\n사진을 촬영하거나 등록할 수 있습니다"
        2 -> "사진이 등록되면\n\n자연스럽게 양식에 맞춘 결과가\n\n이곳에 나타납니다"
        3 -> "이후 이 버튼을 눌러\n채팅방에 사진을 공유할 수 있습니다\n\n보고 양식은 클립보드에 복사되니,\n바로 붙여넣으면 됩니다."
        4 -> "앨범 아이콘을 누르면 앱 갤러리가 열립니다.\n\n카메라 아이콘은 결과 화면에 영향 없이\n사진을 갤러리에만 저장합니다.\n\n사진을 꾹 누르면 여러 장을 선택해\n저장·삭제·공유할 수 있습니다."
        5 -> "다양한 기능들이 준비되어 있습니다.\n\n메모장에 인수인계 내용을 적어두거나\n간단한 번역 기능도 활용해보세요."
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onNext() }
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            with(drawContext.canvas.nativeCanvas) {
                val checkPoint = saveLayer(null, null)
                drawRect(color = Color.Black.copy(alpha = 0.7f), size = size)
                if (targetRect != null) {
                    drawRect(
                        topLeft = Offset(targetRect.left, targetRect.top),
                        size = targetRect.size,
                        color = Color.Transparent,
                        blendMode = BlendMode.Clear
                    )
                }
                restoreToCount(checkPoint)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = guideText,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TranslatorTutorialOverlay(
    step: Int,
    langBounds: Rect?,
    inputBounds: Rect?,
    topViewBounds: Rect?,
    onNext: () -> Unit
) {
    val targetRect = when (step) {
        0 -> langBounds
        1 -> inputBounds
        2 -> topViewBounds
        else -> null
    }

    val guideText = when (step) {
        0 -> "원하는 언어를 선택할 수 있습니다.\n\n설정에서 언어팩을 다운로드해야 사용 가능합니다."
        1 -> "이곳에서 음성(마이크) 또는 텍스트로\n번역할 내용을 입력할 수 있습니다."
        2 -> "상대방에게 보여지는 화면입니다.\n글씨가 거꾸로 뒤집혀 표시되어\n맞은편 상대방이 읽기 편합니다."
        3 -> "오프라인 번역이라 정확도가 다소 낮을 수 있습니다.\n\n인터넷이 연결된 상태에서 번역된 글을 누르면\n음성(TTS)으로 읽어줍니다."
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onNext() }
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            with(drawContext.canvas.nativeCanvas) {
                val checkPoint = saveLayer(null, null)
                drawRect(color = Color.Black.copy(alpha = 0.7f), size = size)
                if (targetRect != null) {
                    drawRect(
                        topLeft = Offset(targetRect.left, targetRect.top),
                        size = targetRect.size,
                        color = Color.Transparent,
                        blendMode = BlendMode.Clear
                    )
                }
                restoreToCount(checkPoint)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = guideText,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// version.json의 versionCode와 현재 앱 versionCode를 비교해 업데이트 여부 판단
suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://raw.githubusercontent.com/bugea0002/CU-/main/version.json")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val json = JSONObject(connection.inputStream.bufferedReader().readText())
        connection.disconnect()
        val remoteVersionCode = json.getInt("versionCode")
        @Suppress("DEPRECATION")
        val currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        if (remoteVersionCode > currentVersionCode) {
            UpdateInfo(
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                releaseNotes = json.optString("releaseNotes", ""),
                sha256 = json.getString("sha256")
            )
        } else null
    } catch (e: Exception) {
        null
    }
}

// APK 다운로드 후 SHA-256 무결성 검증, 통과 시 설치 인텐트 실행
suspend fun downloadAndInstallApk(context: Context, apkUrl: String, expectedSha256: String) {
    withContext(Dispatchers.IO) {
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connect()
        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()
        val apkFile = File(updateDir, "update.apk")
        connection.inputStream.use { input ->
            FileOutputStream(apkFile).use { output -> input.copyTo(output) }
        }
        connection.disconnect()

        val digest = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            apkFile.delete()
            throw IllegalStateException("업데이트 파일 무결성 검증 실패")
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.receiptmanager.fileprovider",
                apkFile
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text("업데이트 (v${info.versionName})") },
        text = {
            Column {
                if (info.releaseNotes.isNotBlank()) {
                    Text(info.releaseNotes)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (isDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("다운로드 중...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDownloading) { Text("업데이트") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) { Text("나중에") }
        }
    )
}

// 앱 내부 갤러리 저장 (filesDir/gallery/) - 앱 삭제 전까지 영구 보존
fun saveToAppGallery(context: Context, bitmap: Bitmap) {
    try {
        val galleryDir = File(context.filesDir, "gallery")
        galleryDir.mkdirs()
        val file = File(galleryDir, "IMG_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun shareMultipleImages(context: Context, files: List<File>) {
    try {
        val uris = ArrayList<Uri>()
        files.forEach { file ->
            uris.add(FileProvider.getUriForFile(context, "com.example.receiptmanager.fileprovider", file))
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "사진 공유"))
    } catch (e: Exception) {
        Toast.makeText(context, "공유 실패: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// URI에서 Bitmap 로딩 - API 28 미만은 deprecated getBitmap, 이상은 ImageDecoder 사용
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT < 28) {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    } else {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    }
}

// 바이트 크기를 사람이 읽기 쉬운 단위로 변환
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}

// 앱 시작 시 한국어·영어 언어팩을 WiFi 무관하게 자동 설치
fun autoDownloadMandatoryModels(context: Context) {
    val modelManager = RemoteModelManager.getInstance()
    listOf(TranslateLanguage.KOREAN, TranslateLanguage.ENGLISH).forEach { language ->
        val model = TranslateRemoteModel.Builder(language).build()
        modelManager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
            if (!downloaded) {
                modelManager.download(model, DownloadConditions.Builder().build())
                    .addOnSuccessListener { Log.d("MLKit", "$language 자동 설치 완료") }
                    .addOnFailureListener { e -> Log.e("MLKit", "$language 자동 설치 실패", e) }
            }
        }
    }
}

fun loadAppGalleryImages(context: Context): List<File> {
    val galleryDir = File(context.filesDir, "gallery")
    return galleryDir.listFiles()
        ?.filter { it.extension == "jpg" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}

@Composable
fun AppGalleryView(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var images by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<File?>(null) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    // 하나라도 선택되면 멀티셀렉트 모드로 전환
    val isSelectionMode = selectedFiles.isNotEmpty()
    val dateFormat = remember { SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN) }
    val groupedImages = remember(images) {
        images.groupBy { file -> dateFormat.format(Date(file.lastModified())) }
    }

    fun refreshImages() {
        coroutineScope.launch(Dispatchers.IO) {
            val result = loadAppGalleryImages(context)
            withContext(Dispatchers.Main) { images = result }
        }
    }

    LaunchedEffect(Unit) { refreshImages() }

    Dialog(onDismissRequest = {
        if (isSelectionMode) selectedFiles = emptySet()
        else onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Text("${selectedFiles.size}개 선택됨", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { selectedFiles = emptySet() }) { Text("취소") }
                    } else {
                        Text("앱 갤러리", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onDismiss) { Text("닫기") }
                    }
                }
                Divider()

                if (images.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("저장된 사진이 없습니다.", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        groupedImages.forEach { (date, files) ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            gridItems(files) { file ->
                                GalleryThumbnail(
                                    file = file,
                                    isSelected = file in selectedFiles,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedFiles = if (file in selectedFiles)
                                                selectedFiles - file else selectedFiles + file
                                        } else selectedImage = file
                                    },
                                    onLongClick = { selectedFiles = selectedFiles + file }
                                )
                            }
                        }
                    }
                }

                if (isSelectionMode) {
                    Divider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                selectedFiles.forEach { file ->
                                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                    if (bmp != null) saveBitmapToGallery(context, bmp)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "${selectedFiles.size}장 저장 완료", Toast.LENGTH_SHORT).show()
                                    selectedFiles = emptySet()
                                }
                            }
                        }) { Text("폰에 저장") }

                        TextButton(onClick = {
                            shareMultipleImages(context, selectedFiles.toList())
                            selectedFiles = emptySet()
                        }) { Text("공유") }

                        TextButton(
                            onClick = { showBatchDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("삭제") }
                    }
                }
            }
        }
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("사진 삭제") },
            text = { Text("선택한 ${selectedFiles.size}장을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedFiles.forEach { it.delete() }
                    images = images.filter { it !in selectedFiles }
                    selectedFiles = emptySet()
                    showBatchDeleteConfirm = false
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("취소") }
            }
        )
    }

    selectedImage?.let { file ->
        PhotoViewerDialog(
            file = file,
            onDismiss = { selectedImage = null },
            onExport = {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) saveBitmapToGallery(context, bitmap)
                selectedImage = null
            },
            onDelete = {
                file.delete()
                images = images.filter { it != file }
                selectedImage = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryThumbnail(
    file: File,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            // inSampleSize=4: 원본의 1/4 크기로 디코딩해 썸네일 메모리 절약
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)

        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
            )
        }
    }
}

@Composable
fun PhotoViewerDialog(
    file: File,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("뒤로") }
                    Row {
                        TextButton(onClick = onExport) { Text("폰에 저장") }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("삭제") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        contentScale = ContentScale.Fit
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 앱 갤러리에서 삭제할까요?\n(폰 갤러리에는 영향 없음)") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }
}

