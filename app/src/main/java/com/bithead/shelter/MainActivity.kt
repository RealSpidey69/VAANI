package com.bithead.shelter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bithead.shelter.ai.ThreatAnalyzer
import com.bithead.shelter.ai.IncidentSummary
import com.bithead.shelter.data.AppDatabase
import com.bithead.shelter.data.Evidence
import com.bithead.shelter.data.TrustedContact
import com.bithead.shelter.data.TrustedContacts
import com.bithead.shelter.emergency.EmergencySms
import com.bithead.shelter.emergency.LocationCache
import com.bithead.shelter.emergency.LocationTrackingService
import com.bithead.shelter.security.Crypto
import com.bithead.shelter.security.AppDisguiseManager
import com.bithead.shelter.sensors.GestureDetector
import com.bithead.shelter.ui.VaaniApp
import com.bithead.shelter.ui.components.EvidencePlaybackState
import com.bithead.shelter.ui.components.EvidenceMediaPreviewState
import com.bithead.shelter.ui.components.SafewordDialog
import com.bithead.shelter.ui.theme.ShelterTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.crypto.SecretKey
import kotlin.coroutines.resume

class MainActivity : FragmentActivity() {

    private enum class CaptureState { IDLE, RECORDING, SEALING }

    private data class SealResult(val entryId: Long, val rawDeleted: Boolean)

    private data class RecoveryResult(val recovered: Int, val failure: String? = null)

    private data class LocationFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val timestampMillis: Long
    )

    companion object {
        // Shared across Activity recreation so a new startup recovery cannot
        // race an in-flight seal owned by the previous Activity instance.
        private val sealMutex = Mutex()

        @Volatile
        private var startupRecoveryComplete = false

        @Volatile
        private var sealingInProgress = false

        @Volatile
        private var activeSealCompletion: CompletableDeferred<Unit>? = null

        private const val GESTURE_WINDOW_SECONDS = 12
        private const val GESTURE_READY_TIMEOUT_MS = 3_000L
        private const val GESTURE_FINAL_RESULT_TIMEOUT_MS = 2_000L
    }

    private lateinit var db: AppDatabase
    private var recorder: MediaRecorder? = null
    private var rawFile: File? = null
    private var activeIncidentId by mutableStateOf<String?>(null)
    private var mediaSealsPending by mutableIntStateOf(0)
    private var trustedContacts by mutableStateOf<List<TrustedContact>>(emptyList())
    private var pendingTrackingEnable = false
    private lateinit var key: SecretKey
    private val analyzer by lazy { ThreatAnalyzer(this) }
    private var mediaPlayer: MediaPlayer? = null
    private var playbackTempFile: File? = null
    private var playbackLoadJob: Job? = null
    private var playbackPositionJob: Job? = null
    private var playbackRequestId = 0L
    private var mediaPreviewTempFile: File? = null
    private var mediaPreviewJob: Job? = null
    private var mediaPreviewRequestId = 0L
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var gestureDetector: GestureDetector
    private var autoStopJob: Job? = null
    private val snackbarHostState = SnackbarHostState()

    // ---- UI state, observed by Compose -----------------------------------
    private var captureState by mutableStateOf(CaptureState.IDLE)
    private var safewordState by mutableStateOf("HELP")
    private var threatLabel by mutableStateOf("Ready")
    private var threatScore by mutableIntStateOf(0)
    private var lastLat by mutableStateOf<Double?>(null)
    private var lastLng by mutableStateOf<Double?>(null)
    private var communityLat by mutableStateOf<Double?>(null)
    private var communityLng by mutableStateOf<Double?>(null)
    private var communityAccuracy by mutableStateOf<Float?>(null)
    private var communityLocationTime by mutableLongStateOf(0L)
    private var showSafewordDialog by mutableStateOf(false)
    private var liveThreatJob: Job? = null
    private var vaultUnlocked by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(true)
    private var listeningEnabled by mutableStateOf(true)
    private var gestureWakeMode by mutableStateOf(false)
    private var safewordListeningActive by mutableStateOf(false)
    private var gestureWindowOpening by mutableStateOf(false)
    private var gestureWindowPending = false
    private var gestureWindowDeadlineMs = 0L
    private var gestureWindowSecondsRemaining by mutableIntStateOf(0)
    private var speechRequestInFlight = false
    private var safewordActivationPending = false
    private var pendingGestureEnable = false
    private var disguiseEnabled by mutableStateOf(false)
    private var playbackState by mutableStateOf(EvidencePlaybackState())
    private var mediaPreviewState by mutableStateOf(EvidenceMediaPreviewState())
    private var foreground = false
    private var restartListeningJob: Job? = null
    private var safewordWindowJob: Job? = null
    private var gestureRecognitionRestartJob: Job? = null
    private var safewordActivationJob: Job? = null
    private var communityLocationJob: Job? = null

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            runCatching {
                contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        trustedContacts = TrustedContacts.add(this, cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty())
                    }
                }
            }.onFailure { error ->
                lifecycleScope.launch { snackbarHostState.showSnackbar("Could not add contact: ${error.message}") }
            }
        }
    }
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importMedia(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = AppDatabase.get(this)
        key = loadOrCreateMasterKey()
        trustedContacts = TrustedContacts.load(this)
        EmergencySms.restore(this)
        LocationCache.restore(this)
        lifecycleScope.launch(Dispatchers.IO) {
            cacheDir.listFiles { file -> file.name.startsWith("temp_play_") || file.name.startsWith("temp_preview_") }
                ?.forEach(File::delete)
        }
        val needsStartupRecovery = !startupRecoveryComplete
        val needsPipelineBarrier = needsStartupRecovery || sealingInProgress
        if (needsPipelineBarrier) captureState = CaptureState.SEALING
        safewordState = loadSafeword()
        listeningEnabled = getSharedPreferences("shelter_prefs", MODE_PRIVATE).getBoolean("listening", true)
        gestureWakeMode = getSharedPreferences("shelter_prefs", MODE_PRIVATE).getBoolean("gesture_wake_mode", false)
        if (gestureWakeMode && !listeningEnabled) {
            listeningEnabled = true
            getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit().putBoolean("listening", true).apply()
        }
        disguiseEnabled = AppDisguiseManager.isEnabled(this)
        AppDisguiseManager.applyLauncherState(this, disguiseEnabled)
        requestPermissionsIfNeeded()
        initSpeechRecognizer()
        gestureDetector = GestureDetector(this) {
            if (gestureWakeMode && listeningEnabled && foreground) startSafewordWindow()
        }
        checkBiometricAvailability()

        setContent {
            ShelterTheme {
                val evidenceList by db.evidenceDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
                val smsStatuses by EmergencySms.statuses.collectAsStateWithLifecycle()
                val cachedLocation by LocationCache.latest.collectAsStateWithLifecycle()
                val locationTracking by LocationCache.trackingEnabled.collectAsStateWithLifecycle()
                VaaniApp(
                    isEmergency = captureState == CaptureState.RECORDING,
                    isSealing = captureState == CaptureState.SEALING,
                    safeword = safewordState,
                    threatLabel = threatLabel,
                    threatScore = threatScore,
                    lat = lastLat,
                    lng = lastLng,
                    mapLat = communityLat,
                    mapLng = communityLng,
                    mapAccuracyMeters = communityAccuracy,
                    mapLocationTimestamp = communityLocationTime.takeIf { it > 0L },
                    evidence = evidenceList,
                    vaultUnlocked = vaultUnlocked,
                    biometricAvailable = biometricAvailable,
                    snackbarHostState = snackbarHostState,
                    onTrigger = {
                        when (captureState) {
                            CaptureState.IDLE -> activateEmergency()
                            CaptureState.RECORDING -> stopEmergency()
                            CaptureState.SEALING -> lifecycleScope.launch {
                                snackbarHostState.showSnackbar("Please wait while the previous recording is sealed")
                            }
                        }
                    },
                    onEditSafeword = { showSafewordDialog = true },
                    playbackState = playbackState,
                    onPlaybackToggle = { toggleEvidencePlayback(it) },
                    onPlaybackStop = { stopEvidencePlayback() },
                    onPlaybackSeek = { evidence, position -> seekEvidencePlayback(evidence, position) },
                    onExport = { exportChainOfCustody(it) },
                    mediaPreviewState = mediaPreviewState,
                    onOpenMedia = { openMediaPreview(it) },
                    onCloseMedia = { closeMediaPreview() },
                    onDeleteEvidence = { deleteEvidence(it) },
                    trustedContacts = trustedContacts,
                    smsStatuses = smsStatuses,
                    locationTracking = locationTracking,
                    cachedLocation = cachedLocation,
                    onAddContact = { name, number -> addTrustedContact(name, number) },
                    onRemoveContact = { number -> trustedContacts = TrustedContacts.remove(this, number) },
                    onPickContact = { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                    onLocationTrackingChange = { updateLocationTracking(it) },
                    onRequestSmsPermission = { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 11) },
                    onImportMedia = { mediaPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    pendingEvidenceDir = File(filesDir, "pending"),
                    activeIncidentId = activeIncidentId,
                    onMediaCaptured = { sealMedia(it) },
                    onVerifyChain = { verifyEvidenceChain() },
                    onUnlockVault = { unlockVault() },
                    listening = listeningEnabled,
                    listeningActive = safewordListeningActive,
                    onListeningChange = { updateListeningEnabled(it) },
                    gestureWakeMode = gestureWakeMode,
                    gestureWindowOpening = gestureWindowOpening,
                    gestureWindowSecondsRemaining = gestureWindowSecondsRemaining,
                    onGestureWakeModeChange = { updateGestureWakeMode(it) },
                    disguiseEnabled = disguiseEnabled,
                    onDisguiseEnabledChange = { updateAppDisguise(it) },
                    onLockVault = { stopEvidencePlayback(); closeMediaPreview(); vaultUnlocked = false },
                    onVaultHidden = { stopEvidencePlayback(); closeMediaPreview() },
                    onRefreshMapLocation = { refreshCommunityLocation() }
                )
                if (showSafewordDialog) {
                    SafewordDialog(
                        current = safewordState,
                        onDismiss = { showSafewordDialog = false },
                        onSave = { saveSafeword(it); showSafewordDialog = false }
                    )
                }
            }
        }
        if (needsPipelineBarrier) {
            lifecycleScope.launch {
                try {
                    if (needsStartupRecovery) recoverOrphanedEvidence()
                    awaitActiveSeal()
                } finally {
                    captureState = CaptureState.IDLE
                    if (!gestureWakeMode) restartSafewordListening()
                }
            }
        }
    }

    // ---- Keys & prefs -------------------------------------------------------

    private fun loadOrCreateMasterKey(): SecretKey {
        // Key now lives in the Android Keystore (hardware-backed where available)
        // instead of being persisted as raw base64 bytes in SharedPreferences.
        return Crypto.getOrCreateKeystoreKey()
    }

    private fun loadSafeword(): String {
        val prefs = getSharedPreferences("shelter_prefs", MODE_PRIVATE)
        return prefs.getString("user_safeword", "HELP") ?: "HELP"
    }

    private fun saveSafeword(newWord: String) {
        safewordState = newWord.trim().uppercase(Locale.getDefault())
        getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit().putString("user_safeword", safewordState).apply()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (!hasLocationPermission()) {
            // Android 12+ requires both permissions in the same request so the
            // system can offer the user its Precise / Approximate choice.
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 10)
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    /**
     * Requests a fresh GPS/network fix instead of relying on a possibly stale
     * or null getLastKnownLocation() reading. Falls back to the last-known
     * fix only if a live update can't be obtained within the timeout.
     */
    private suspend fun getLocation(): LocationFix? {
        val hasFine = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            snackbarHostState.showSnackbar("Location permission denied — location features are unavailable")
            return null
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnownProviders = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (hasFine) add(LocationManager.GPS_PROVIDER)
        }
        val bestLastKnown = lastKnownProviders
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxWithOrNull(compareBy<Location>({ it.time }, { if (it.hasAccuracy()) -it.accuracy else Float.NEGATIVE_INFINITY }))

        val liveProviders = buildList {
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (hasFine && runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        if (liveProviders.isEmpty()) {
            snackbarHostState.showSnackbar("Location services are off — using the most recent saved fix")
            return bestLastKnown?.toLocationFix()
        }

        // Request network and GPS together. Indoors, the network provider can
        // win immediately; otherwise the 3-second cap falls back to the best
        // last-known reading instead of waiting indefinitely for satellites.
        val liveLocation = withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                var completed = false
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!completed && cont.isActive) {
                            completed = true
                            runCatching { lm.removeUpdates(this) }
                            cont.resume(location)
                        }
                    }
                }
                var registered = false
                liveProviders.forEach { provider ->
                    try {
                        lm.requestLocationUpdates(provider, 0L, 0f, listener, mainLooper)
                        registered = true
                    } catch (_: SecurityException) {
                        // Try any remaining provider. The permission state may
                        // have changed between the initial check and request.
                    } catch (_: IllegalArgumentException) {
                        // Provider disappeared or is unsupported on this device.
                    }
                }
                if (!registered && !completed) {
                    completed = true
                    cont.resume(null)
                }
                cont.invokeOnCancellation {
                    completed = true
                    runCatching { lm.removeUpdates(listener) }
                }
            }
        }
        return (liveLocation ?: bestLastKnown)?.toLocationFix()
    }

    private fun Location.toLocationFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy() && accuracy.isFinite() && accuracy > 0f) accuracy else null,
        timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    private fun updateAppDisguise(enabled: Boolean) {
        if (enabled) stopEvidencePlayback()
        AppDisguiseManager.setEnabled(this, enabled)
        disguiseEnabled = enabled
    }

    private fun refreshCommunityLocation() {
        communityLocationJob?.cancel()
        communityLocationJob = lifecycleScope.launch {
            getLocation()?.let { fix ->
                communityLat = fix.latitude
                communityLng = fix.longitude
                communityAccuracy = fix.accuracyMeters
                communityLocationTime = fix.timestampMillis
                LocationCache.save(this@MainActivity,
                    com.bithead.shelter.emergency.CachedLocation(fix.latitude, fix.longitude, fix.timestampMillis, fix.accuracyMeters))
            }
        }
    }

    private fun addTrustedContact(name: String, number: String) {
        runCatching { TrustedContacts.add(this, name, number) }
            .onSuccess { trustedContacts = it }
            .onFailure { error -> lifecycleScope.launch { snackbarHostState.showSnackbar(error.message ?: "Invalid contact") } }
    }

    private fun updateLocationTracking(enabled: Boolean) {
        if (!enabled) {
            LocationCache.setEnabled(this, false)
            stopService(Intent(this, LocationTrackingService::class.java))
            return
        }
        if (!hasLocationPermission()) {
            pendingTrackingEnable = true
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 10)
            return
        }
        LocationCache.setEnabled(this, true)
        startLocationProtection()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
    }

    private fun startLocationProtection() {
        try {
            ContextCompat.startForegroundService(this,
                Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_START))
        } catch (error: Exception) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Location protection could not start: ${error.message}") }
        }
    }

    private fun importMedia(uri: Uri) {
        if (captureState == CaptureState.SEALING || mediaSealsPending > 0) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Wait for the current evidence to finish sealing") }
            return
        }
        val mime = contentResolver.getType(uri).orEmpty()
        val type = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            else -> {
                lifecycleScope.launch { snackbarHostState.showSnackbar("Choose an image or video") }
                return
            }
        }
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> if (type == "video") "mp4" else "jpg"
        }
        val size = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L
        if (size > 250L * 1024 * 1024) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Choose a file under 250 MB") }
            return
        }
        val session = activeIncidentId ?: System.currentTimeMillis().toString()
        lifecycleScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    val directory = File(filesDir, "pending").apply { mkdirs() }
                    val timestamp = System.currentTimeMillis()
                    val target = File(directory, "evidence_${timestamp}_${type}_${session}.raw.$extension")
                    contentResolver.openInputStream(uri).use { source ->
                        requireNotNull(source) { "Cannot open the selected media" }
                        target.outputStream().use { output -> source.copyTo(output) }
                    }
                    target
                }
                sealMedia(raw)
            } catch (error: Exception) {
                snackbarHostState.showSnackbar("Media import failed: ${error.message}")
            }
        }
    }

    // ---- Safeword voice trigger ----------------------------------------------

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            listeningEnabled = false
            gestureWakeMode = false
            getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit()
                .putBoolean("listening", false)
                .putBoolean("gesture_wake_mode", false)
                .apply()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    speechRequestInFlight = false
                    if (captureState != CaptureState.IDLE) return
                    safewordListeningActive = true
                    if (gestureWakeMode && gestureWindowPending) {
                        gestureWindowOpening = false
                        if (gestureWindowDeadlineMs == 0L) {
                            gestureWindowDeadlineMs = SystemClock.elapsedRealtime() + GESTURE_WINDOW_SECONDS * 1_000L
                            getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.vibrate(
                                VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                            startGestureWindowCountdown()
                        }
                    }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val wasGestureWindow = gestureWakeMode && gestureWindowPending
                    speechRequestInFlight = false
                    safewordListeningActive = false
                    if (wasGestureWindow) {
                        val retryable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        if (!retryable || !restartGestureRecognitionIfTimeRemains()) {
                            finishGestureWindow(cancelRecognizer = false)
                            lifecycleScope.launch {
                                snackbarHostState.showSnackbar(speechErrorMessage(error))
                            }
                        }
                    } else if (captureState == CaptureState.IDLE && !gestureWakeMode) {
                        restartSafewordListening()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val wasGestureWindow = gestureWakeMode && gestureWindowPending
                    val matched = checkSafewordMatches(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    speechRequestInFlight = false
                    safewordListeningActive = false
                    if (wasGestureWindow && !matched && !restartGestureRecognitionIfTimeRemains()) {
                        finishGestureWindow(cancelRecognizer = false)
                    }
                    if (captureState == CaptureState.IDLE && !gestureWakeMode && !matched) restartSafewordListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    checkSafewordMatches(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Safeword not heard — gesture remains armed"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognizer was busy — wait a moment and jerk again"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required for the safeword window"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition is unavailable offline on this device"
        else -> "Safeword window closed — gesture remains armed"
    }

    private fun checkSafewordMatches(matches: ArrayList<String>?): Boolean {
        if (captureState != CaptureState.IDLE || !listeningEnabled || !foreground || safewordActivationPending ||
            (gestureWakeMode && !gestureWindowPending)) return false
        val matched = matches?.any { it.contains(safewordState, ignoreCase = true) } == true
        if (matched) queueEmergencyFromSafeword()
        return matched
    }

    private fun queueEmergencyFromSafeword() {
        if (safewordActivationPending) return
        safewordActivationPending = true
        restartListeningJob?.cancel()
        if (gestureWakeMode) finishGestureWindow(cancelRecognizer = true)
        else {
            speechRequestInFlight = false
            safewordListeningActive = false
            speechRecognizer?.cancel()
        }
        safewordActivationJob?.cancel()
        safewordActivationJob = lifecycleScope.launch {
            delay(200L)
            safewordActivationPending = false
            if (foreground && listeningEnabled && captureState == CaptureState.IDLE) activateEmergency()
        }
    }

    private fun startSafewordListening(): Boolean {
        if (captureState != CaptureState.IDLE || !foreground || !listeningEnabled || speechRecognizer == null ||
            speechRequestInFlight || safewordListeningActive ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Prefer the on-device recognizer where the OS/device supports it,
                // so the safeword phrase isn't sent to a cloud speech API by default.
                // NOTE: not all Android versions/devices honor this — see README
                // "Known limitations" for the real offline-guarantee status.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            speechRequestInFlight = true
            speechRecognizer?.startListening(intent)
            return true
        } catch (_: Exception) {
            speechRequestInFlight = false
            safewordListeningActive = false
            return false
        }
    }

    private fun startSafewordWindow() {
        if (!gestureWakeMode || gestureWindowPending || safewordActivationPending || captureState != CaptureState.IDLE ||
            !foreground || !listeningEnabled) return
        gestureWindowPending = true
        gestureWindowOpening = true
        gestureWindowDeadlineMs = 0L
        gestureWindowSecondsRemaining = 0
        if (!startSafewordListening()) {
            finishGestureWindow(cancelRecognizer = true)
            lifecycleScope.launch {
                snackbarHostState.showSnackbar("Could not open the safeword window — check microphone access")
            }
            return
        }
        safewordWindowJob?.cancel()
        safewordWindowJob = lifecycleScope.launch {
            delay(GESTURE_READY_TIMEOUT_MS)
            if (gestureWindowPending && gestureWindowOpening) {
                finishGestureWindow(cancelRecognizer = true)
                snackbarHostState.showSnackbar("Microphone did not become ready — gesture remains armed")
            }
        }
    }

    private fun startGestureWindowCountdown() {
        safewordWindowJob?.cancel()
        gestureWindowSecondsRemaining = GESTURE_WINDOW_SECONDS
        safewordWindowJob = lifecycleScope.launch {
            while (gestureWindowPending) {
                val remainingMs = gestureWindowDeadlineMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) break
                gestureWindowSecondsRemaining = ((remainingMs + 999L) / 1_000L).toInt()
                delay(remainingMs.coerceAtMost(1_000L))
            }
            if (!gestureWindowPending) return@launch
            gestureWindowSecondsRemaining = 0
            safewordListeningActive = false
            speechRecognizer?.stopListening()
            delay(GESTURE_FINAL_RESULT_TIMEOUT_MS)
            if (gestureWindowPending) finishGestureWindow(cancelRecognizer = true)
        }
    }

    private fun restartGestureRecognitionIfTimeRemains(): Boolean {
        if (!gestureWindowPending || gestureWindowDeadlineMs <= SystemClock.elapsedRealtime()) return false
        gestureWindowOpening = true
        gestureRecognitionRestartJob?.cancel()
        gestureRecognitionRestartJob = lifecycleScope.launch {
            delay(150L)
            if (gestureWindowPending && !startSafewordListening()) {
                finishGestureWindow(cancelRecognizer = true)
                snackbarHostState.showSnackbar("Could not reopen the safeword listener — gesture remains armed")
            }
        }
        return true
    }

    private fun finishGestureWindow(cancelRecognizer: Boolean) {
        gestureWindowPending = false
        gestureWindowOpening = false
        gestureWindowDeadlineMs = 0L
        gestureWindowSecondsRemaining = 0
        speechRequestInFlight = false
        safewordListeningActive = false
        safewordWindowJob?.cancel()
        safewordWindowJob = null
        gestureRecognitionRestartJob?.cancel()
        gestureRecognitionRestartJob = null
        if (cancelRecognizer) speechRecognizer?.cancel()
    }

    private fun cancelSafewordSession() {
        restartListeningJob?.cancel()
        safewordActivationJob?.cancel()
        safewordActivationPending = false
        finishGestureWindow(cancelRecognizer = true)
    }

    private fun restartSafewordListening() {
        restartListeningJob?.cancel()
        if (listeningEnabled && foreground && captureState == CaptureState.IDLE && !gestureWakeMode) {
            restartListeningJob = lifecycleScope.launch { delay(1000); startSafewordListening() }
        }
    }

    private fun updateListeningEnabled(enabled: Boolean) {
        if (enabled && (speechRecognizer == null ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Enable microphone access and a speech recognition service to listen for your safeword") }
            requestPermissionsIfNeeded()
            return
        }
        listeningEnabled = enabled
        if (!enabled) gestureWakeMode = false
        getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit()
            .putBoolean("listening", enabled)
            .putBoolean("gesture_wake_mode", gestureWakeMode)
            .apply()
        cancelSafewordSession()
        if (enabled && !gestureWakeMode) restartSafewordListening()
    }

    private fun updateGestureWakeMode(enabled: Boolean) {
        if (enabled && !gestureDetector.isAvailable) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("This device has no accelerometer for Gesture-Wake") }
            return
        }
        if (enabled && speechRecognizer == null) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("No speech recognition service is available on this device") }
            return
        }
        if (enabled && ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingGestureEnable = true
            requestPermissionsIfNeeded()
            return
        }
        if (enabled && foreground && !gestureDetector.start()) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Accelerometer could not be started") }
            return
        }
        pendingGestureEnable = false
        gestureWakeMode = enabled
        if (enabled) listeningEnabled = true
        getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit()
            .putBoolean("gesture_wake_mode", enabled)
            .putBoolean("listening", listeningEnabled)
            .apply()
        cancelSafewordSession()
        if (!enabled && listeningEnabled && foreground) restartSafewordListening()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (LocationCache.trackingEnabled.value && hasLocationPermission()) startLocationProtection()
        checkBiometricAvailability()
        val sensorStarted = gestureDetector.start()
        if (gestureWakeMode && !sensorStarted) {
            gestureWakeMode = false
            listeningEnabled = false
            getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit()
                .putBoolean("gesture_wake_mode", false)
                .putBoolean("listening", false)
                .apply()
            lifecycleScope.launch { snackbarHostState.showSnackbar("Gesture-Wake disabled because the accelerometer is unavailable") }
        } else if (!gestureWakeMode) {
            startSafewordListening()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listeningEnabled = false
                if (pendingGestureEnable) {
                    gestureWakeMode = false
                    pendingGestureEnable = false
                    getSharedPreferences("shelter_prefs", MODE_PRIVATE).edit()
                        .putBoolean("gesture_wake_mode", false)
                        .putBoolean("listening", false)
                        .apply()
                    lifecycleScope.launch { snackbarHostState.showSnackbar("Gesture-Wake needs microphone permission") }
                }
            } else if (pendingGestureEnable) {
                updateGestureWakeMode(true)
            } else if (!gestureWakeMode) {
                startSafewordListening()
            }
            if (hasLocationPermission()) refreshCommunityLocation()
            if (pendingTrackingEnable && hasLocationPermission()) {
                pendingTrackingEnable = false
                updateLocationTracking(true)
            }
        } else if (requestCode == 11) {
            lifecycleScope.launch {
                snackbarHostState.showSnackbar(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
                    "SMS alerts are ready" else "SMS permission denied — emergency recording still works")
            }
        }
    }

    // ---- Emergency capture -----------------------------------------------------

    private fun activateEmergency() {
        if (captureState != CaptureState.IDLE || sealingInProgress || mediaSealsPending > 0) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Microphone permission denied — cannot record evidence") }
            requestPermissionsIfNeeded()
            return
        }
        stopEvidencePlayback()
        restartListeningJob?.cancel()
        safewordWindowJob?.cancel()
        safewordListeningActive = false
        captureState = CaptureState.RECORDING
        threatLabel = "Monitoring peaks…"
        threatScore = 0
        speechRecognizer?.cancel()

        // Live-tick the threat meter in the UI while recording, instead of
        // only showing a number after the fact.
        liveThreatJob?.cancel()
        liveThreatJob = lifecycleScope.launch {
            analyzer.liveResult.collect { live ->
                if (captureState == CaptureState.RECORDING) {
                    threatLabel = live.label
                    threatScore = live.score
                }
            }
        }

        try {
            val pendingDir = File(filesDir, "pending")
            if (!pendingDir.exists() && !pendingDir.mkdirs()) {
                throw IOException("Could not create private pending-evidence storage")
            }
            val captureId = System.currentTimeMillis()
            activeIncidentId = captureId.toString()
            val file = File(pendingDir, "evidence_$captureId.raw.m4a")
            rawFile = file
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            // The evidence recorder gets the microphone first. Starting the
            // analyzer before MediaRecorder can starve or reject capture on
            // devices that do not support concurrent microphone clients.
            analyzer.startListening()
            runCatching { EmergencySms.send(this, trustedContacts, LocationCache.latest.value) }
                .onFailure { error -> lifecycleScope.launch {
                    snackbarHostState.showSnackbar("Recording started, but SMS failed: ${error.message}")
                } }

            autoStopJob?.cancel()
            autoStopJob = lifecycleScope.launch {
                delay(5 * 60 * 1000L)
                if (captureState == CaptureState.RECORDING) stopEmergency()
            }
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            analyzer.stopAndAnalyze()
            val preservedRaw = rawFile?.takeIf { it.exists() }
            rawFile = null
            activeIncidentId = null
            threatLabel = "Recording failed: ${e.message}"
            captureState = CaptureState.IDLE
            lifecycleScope.launch {
                val preserved = if (preservedRaw != null) " Any captured bytes were retained for recovery." else ""
                snackbarHostState.showSnackbar("Recording could not start: ${e.message ?: "microphone unavailable"}.$preserved")
            }
            if (!gestureWakeMode) restartSafewordListening()
        }
    }

    private fun stopEmergency() {
        if (captureState != CaptureState.RECORDING) return
        val sealCompletion = CompletableDeferred<Unit>()
        activeSealCompletion = sealCompletion
        sealingInProgress = true
        captureState = CaptureState.SEALING
        liveThreatJob?.cancel()
        liveThreatJob = null
        autoStopJob?.cancel()
        autoStopJob = null

        val result = analyzer.stopAndAnalyze()
        val recorderStopped = recorder?.runCatching { stop() }?.isSuccess == true
        runCatching { recorder?.release() }
        recorder = null
        val raw = rawFile
        rawFile = null
        val incidentId = activeIncidentId
        activeIncidentId = null

        if (!recorderStopped || raw == null || !raw.exists() || raw.length() == 0L) {
            threatLabel = "Recording could not be finalized"
            finishActiveSeal(sealCompletion)
            captureState = CaptureState.IDLE
            lifecycleScope.launch {
                snackbarHostState.showSnackbar(
                    "Evidence could not be finalized. The pending raw file was retained for recovery."
                )
            }
            if (!gestureWakeMode) restartSafewordListening()
            return
        }

        lifecycleScope.launch {
            var sealFailure: Exception? = null
            val sealed = try {
                sealEvidence(raw, result.label, result.score, incidentId = incidentId)
            } catch (e: Exception) {
                sealFailure = e
                null
            } finally {
                finishActiveSeal(sealCompletion)
                captureState = CaptureState.IDLE
                if (!gestureWakeMode) restartSafewordListening()
            }

            if (sealed == null) {
                val failure = sealFailure
                threatLabel = "Sealing failed: ${failure?.message}"
                snackbarHostState.showSnackbar(
                    "Evidence could not be sealed: ${failure?.message ?: "storage error"}. Raw audio was retained for recovery."
                )
                return@launch
            }

            threatLabel = result.label
            threatScore = result.score
            snackbarHostState.showSnackbar(
                if (sealed.rawDeleted) {
                    "Evidence sealed and added to the vault ✓"
                } else {
                    "Evidence sealed. The encrypted record is safe, but plaintext cleanup needs attention."
                }
            )

            // Location is optional metadata. It must never block the
            // evidence row from appearing in the Vault or invalidate it.
            runCatching {
                getLocation()?.let { fix ->
                    lastLat = fix.latitude
                    lastLng = fix.longitude
                    withContext(Dispatchers.IO) {
                        db.evidenceDao().updateLocation(sealed.entryId, fix.latitude, fix.longitude)
                    }
                }
            }
        }
    }

    private suspend fun sealEvidence(
        raw: File, label: String, score: Int,
        mediaType: String = "AUDIO", mimeType: String = "audio/mp4", incidentId: String? = null
    ): SealResult =
        withContext(NonCancellable + Dispatchers.IO) {
            sealMutex.withLock {
                val createdAt = Regex("^evidence_(\\d+)").find(raw.name)?.groupValues?.get(1)?.toLongOrNull()
                    ?: raw.lastModified().takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                require(raw.name.contains(".raw.") && raw.name.startsWith("evidence_")) { "Invalid evidence filename" }
                val encrypted = File(filesDir, if (mediaType == "AUDIO") "evidence_$createdAt.enc" else
                    "${raw.name.substringBefore(".raw.")}.${raw.extension}.enc")
                val temporary = File(filesDir, "${encrypted.name}.tmp")
                var finalized = false
                var committed = false

                try {
                    if (encrypted.exists()) throw IOException("Evidence destination already exists")
                    if (temporary.exists() && !temporary.delete()) {
                        throw IOException("Could not clear an old temporary evidence file")
                    }

                    Crypto.encrypt(raw, temporary, key)
                    moveAtomically(temporary, encrypted)
                    finalized = true

                    val fileHash = Crypto.sha256(encrypted)
                    val entryId = db.evidenceDao().insertChained(
                        Evidence(
                            createdAt = createdAt,
                            encryptedFile = encrypted.name,
                            latitude = null,
                            longitude = null,
                            threatLabel = label,
                            threatScore = score,
                            sha256 = "",
                            previousHash = null,
                            incidentId = incidentId,
                            mediaType = mediaType,
                            mimeType = mimeType
                        ),
                        fileHash
                    )
                    committed = true
                    SealResult(entryId, raw.delete())
                } catch (failure: Exception) {
                    temporary.delete()
                    if (finalized && !committed) {
                        val lookup = runCatching { db.evidenceDao().entryIdForFile(encrypted.name) }
                        if (lookup.isSuccess && lookup.getOrNull() != null) {
                            return@withLock SealResult(lookup.getOrThrow()!!, raw.delete())
                        }
                        // Delete only when Room positively confirms that the
                        // transaction did not commit. Otherwise leave the final
                        // file for startup recovery to reconcile safely.
                        if (lookup.isSuccess) encrypted.delete()
                    }
                    throw failure
                }
            }
        }

    private fun sealMedia(raw: File) {
        val match = Regex("^evidence_(\\d+)_(image|video)_(\\d+)\\.raw\\.(jpg|png|webp|heic|mp4)$")
            .matchEntire(raw.name)
        if (match == null || !raw.exists() || raw.length() == 0L) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Media capture is incomplete; no vault entry was created") }
            return
        }
        val kind = match.groupValues[2]
        val session = match.groupValues[3]
        val extension = match.groupValues[4]
        val mime = when (extension) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "video/mp4"
        }
        mediaSealsPending++
        lifecycleScope.launch {
            try {
                val sealed = sealEvidence(raw, if (kind == "video") "Video evidence" else "Image evidence", 0,
                    kind.uppercase(Locale.US), mime, session)
                LocationCache.latest.value?.let { fix ->
                    withContext(Dispatchers.IO) {
                        db.evidenceDao().updateLocation(sealed.entryId, fix.latitude, fix.longitude)
                    }
                }
                snackbarHostState.showSnackbar(if (sealed.rawDeleted) "Media sealed in Vault" else
                    "Media sealed; private temporary file cleanup needs attention")
            } catch (error: Exception) {
                snackbarHostState.showSnackbar("Media could not be sealed: ${error.message}. Private raw file retained for recovery")
            } finally {
                mediaSealsPending--
            }
        }
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun finishActiveSeal(completion: CompletableDeferred<Unit>) {
        sealingInProgress = false
        completion.complete(Unit)
        if (activeSealCompletion === completion) activeSealCompletion = null
    }

    private suspend fun awaitActiveSeal() {
        activeSealCompletion?.await()
    }

    /**
     * Re-computes SHA-256 of every sealed evidence file currently on disk and
     * re-derives the hash chain, comparing it against what's stored in Room.
     * This is the live "tamper test" demo: if any encrypted file has been
     * modified (or deleted/swapped) since sealing, the recomputed chain value
     * will not match the stored one and verification fails at that entry.
     */
    /**
     * Checks once whether this device can actually do biometric auth
     * (enrolled fingerprint/face) or a device PIN/pattern as fallback, so the
     * UI can show an honest "Unlock" vs. "Biometrics unavailable" state
     * instead of a button that silently fails.
     */
    private fun checkBiometricAvailability() {
        val manager = BiometricManager.from(this)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        biometricAvailable = result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Gates the Evidence Vault behind fingerprint/face (or device PIN as
     * fallback) so that recordings can't be browsed just by picking up an
     * unlocked phone — mirrors the biometric-gated evidence access pattern
     * used by comparable safety apps (e.g. UNDERCOVER).
     */
    private fun unlockVault() {
        if (!biometricAvailable) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("No fingerprint/face/PIN set up on this device") }
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Evidence Vault")
            .setSubtitle("Verify it's you before viewing sealed evidence")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vaultUnlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lifecycleScope.launch { snackbarHostState.showSnackbar("Unlock failed: $errString") }
                }
                override fun onAuthenticationFailed() {
                    lifecycleScope.launch { snackbarHostState.showSnackbar("Fingerprint/face not recognized — try again") }
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    override fun onStop() {
        super.onStop()
        foreground = false
        gestureDetector.stop()
        cancelSafewordSession()
        communityLocationJob?.cancel()
        stopEvidencePlayback()
        closeMediaPreview()
        // Re-lock the vault whenever the app leaves the foreground, so
        // background/multitasking can't be used to skip the biometric check.
        vaultUnlocked = false
    }

    private fun verifyEvidenceChain() {
        lifecycleScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    sealMutex.withLock {
                        val entries = db.evidenceDao().allAscending()
                        if (entries.isEmpty()) return@withLock "No evidence to verify yet"
                        var previousChain: String? = null
                        for (item in entries) {
                            val file = File(filesDir, item.encryptedFile)
                            val storedHash = item.deletedFileHash
                            val fileHash = when {
                                storedHash != null -> {
                                    if (file.exists() && Crypto.sha256(file) != storedHash) {
                                        return@withLock "⚠ Tamper detected: evidence #${item.id} does not match its deletion record"
                                    }
                                    if (item.deletedAt != null && file.exists()) {
                                        return@withLock "⚠ Deletion incomplete for evidence #${item.id}"
                                    }
                                    storedHash
                                }
                                file.exists() -> Crypto.sha256(file)
                                else -> return@withLock "⚠ Tamper detected: evidence #${item.id} is missing"
                            }
                            if (Crypto.chainHash(fileHash, previousChain) != item.sha256 || previousChain != item.previousHash) {
                                return@withLock "⚠ Tamper detected: evidence #${item.id} does not match its recorded hash chain"
                            }
                            previousChain = item.sha256
                        }
                        val deleted = entries.count { it.deletedAt != null }
                        val pending = entries.count { it.deletedFileHash != null && it.deletedAt == null }
                        when {
                            pending > 0 -> "Chain links verified; $pending evidence deletion${if (pending == 1) "" else "s"} still pending"
                            deleted > 0 -> "Chain verified ✓ — ${entries.size - deleted} evidence items intact; $deleted deletion record${if (deleted == 1) "" else "s"} retained"
                            else -> "Chain verified ✓ — all ${entries.size} evidence items intact"
                        }
                    }
                }
            } catch (e: Exception) {
                "Chain verification failed: ${e.message ?: "storage error"}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    private fun deleteEvidence(evidence: Evidence) {
        if (!vaultUnlocked) return
        if (playbackState.evidenceId == evidence.id) stopEvidencePlayback()
        if (mediaPreviewState.evidenceId == evidence.id) closeMediaPreview()
        lifecycleScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    sealMutex.withLock {
                        val dao = db.evidenceDao()
                        val current = dao.byId(evidence.id) ?: throw IOException("Evidence no longer exists")
                        if (current.deletedAt != null) return@withLock
                        val encrypted = File(filesDir, current.encryptedFile)
                        val fileHash = current.deletedFileHash ?: run {
                            if (!encrypted.exists()) throw IOException("Encrypted evidence is missing; verify the chain")
                            Crypto.sha256(encrypted)
                        }
                        if (Crypto.chainHash(fileHash, current.previousHash) != current.sha256) {
                            throw IOException("Evidence hash does not match the vault chain")
                        }
                        if (current.deletedFileHash == null && dao.requestDeletion(current.id, fileHash) != 1) {
                            throw IOException("Could not save the deletion record")
                        }
                        completePendingDeletion(current.copy(deletedFileHash = fileHash))
                    }
                }
                snackbarHostState.showSnackbar("Evidence deleted; its hash-chain deletion record remains")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Delete incomplete: ${e.message ?: "storage error"}. Tap Delete to retry")
            }
        }
    }

    private suspend fun completePendingDeletion(entry: Evidence) {
        val hash = entry.deletedFileHash ?: throw IOException("Deletion record is missing its file hash")
        val encrypted = File(filesDir, entry.encryptedFile)
        if (encrypted.exists() && Crypto.sha256(encrypted) != hash) {
            throw IOException("Encrypted recording changed after deletion was requested")
        }
        pendingRawFor(entry.encryptedFile)?.let { raw ->
            if (raw.exists() && !raw.delete()) throw IOException("Could not remove the pending raw recording")
        }
        if (encrypted.exists() && !encrypted.delete()) throw IOException("Could not remove the encrypted recording")
        if (db.evidenceDao().finishDeletion(entry.id, System.currentTimeMillis()) != 1) {
            throw IOException("Could not finish the deletion record")
        }
    }

    private suspend fun recoverOrphanedEvidence() {
        val result = withContext(NonCancellable + Dispatchers.IO) {
            sealMutex.withLock {
                if (startupRecoveryComplete) return@withLock null
                try {
                    val dao = db.evidenceDao()
                    val entries = dao.allAscending()
                    val knownFiles = entries.mapTo(HashSet()) { it.encryptedFile }
                    var failure: String? = null

                    // A crash can interrupt deletion after the hash-only marker
                    // commits. Finish removing those files before scanning orphans.
                    for (entry in entries.filter { it.deletedFileHash != null && it.deletedAt == null }) {
                        try {
                            completePendingDeletion(entry)
                        } catch (e: Exception) {
                            failure = "Deletion #${entry.id}: ${e.message ?: "storage error"}"
                            break
                        }
                    }

                    // A crash after the Room commit but before plaintext cleanup
                    // can leave the matching pending raw file behind. A present
                    // encrypted file plus its DB row proves that cleanup is safe.
                    entries.filter { it.deletedFileHash == null }.forEach { entry ->
                        if (File(filesDir, entry.encryptedFile).exists()) pendingRawFor(entry.encryptedFile)?.delete()
                    }

                    val orphanedFiles = filesDir.listFiles()
                        .orEmpty()
                        .filter { file ->
                            file.isFile && isEvidenceEncryptedFile(file.name) &&
                                file.name !in knownFiles
                        }
                        .sortedWith(compareBy<File>(
                            { evidenceTimestamp(it.name) ?: Long.MAX_VALUE },
                            { it.lastModified() },
                            { it.name }
                        ))
                    var recovered = 0

                    for (file in orphanedFiles) {
                        try {
                            if (file.length() <= 28L) throw IOException("${file.name} is incomplete")
                            val fileHash = Crypto.sha256(file)
                            val createdAt = evidenceTimestamp(file.name) ?: file.lastModified()
                            val media = mediaMetadata(file.name)
                            dao.insertChained(
                                Evidence(
                                    createdAt = createdAt,
                                    encryptedFile = file.name,
                                    latitude = null,
                                    longitude = null,
                                    threatLabel = "Recovered evidence",
                                    threatScore = 0,
                                    sha256 = "",
                                    previousHash = null,
                                    incidentId = media?.second ?: createdAt.toString(),
                                    mediaType = media?.first ?: "AUDIO",
                                    mimeType = media?.third ?: "audio/mp4"
                                ),
                                fileHash
                            )
                            pendingRawFor(file.name)?.delete()
                            recovered++
                        } catch (e: Exception) {
                            // Preserve this and all later files. Appending a newer
                            // orphan first would reverse their capture order.
                            if (failure == null) failure = e.message ?: "storage error"
                            break
                        }
                    }
                    RecoveryResult(recovered, failure)
                } catch (e: Exception) {
                    RecoveryResult(0, e.message ?: "storage error")
                } finally {
                    startupRecoveryComplete = true
                }
            }
        } ?: return

        if (result.recovered > 0) {
            snackbarHostState.showSnackbar(
                "Recovered ${result.recovered} sealed recording${if (result.recovered == 1) "" else "s"} into the vault"
            )
        }
        result.failure?.let {
            snackbarHostState.showSnackbar("Evidence recovery needs attention: $it")
        }
    }

    private fun evidenceTimestamp(fileName: String): Long? =
        Regex("^evidence_(\\d+)").find(fileName)?.groupValues?.get(1)?.toLongOrNull()

    private fun isEvidenceEncryptedFile(fileName: String): Boolean =
        fileName.matches(Regex("evidence_\\d+\\.enc")) ||
            mediaMetadata(fileName) != null

    private fun mediaMetadata(fileName: String): Triple<String, String, String>? {
        val match = Regex("^evidence_\\d+_(image|video)_(\\d+)\\.(jpg|png|webp|heic|mp4)\\.enc$")
            .matchEntire(fileName) ?: return null
        val mime = when (match.groupValues[3]) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "video/mp4"
        }
        return Triple(match.groupValues[1].uppercase(Locale.US), match.groupValues[2], mime)
    }

    private fun pendingRawFor(encryptedFileName: String): File? {
        val captureId = evidenceTimestamp(encryptedFileName) ?: return null
        if (mediaMetadata(encryptedFileName) != null) {
            val name = encryptedFileName.removeSuffix(".enc")
            val extension = name.substringAfterLast('.')
            return File(File(filesDir, "pending"), "${name.removeSuffix(".$extension")}.raw.$extension")
        }
        return File(File(filesDir, "pending"), "evidence_$captureId.raw.m4a")
    }

    /**
     * Writes a shareable chain-of-custody JSON for one evidence entry and
     * hands it to the Android share sheet — the "give this to a lawyer/
     * officer" wow moment that ties the crypto work to a real-world outcome.
     */
    private fun exportChainOfCustody(evidence: Evidence) {
        if (!vaultUnlocked) return
        try {
            val json = IncidentSummary.toChainOfCustodyJson(evidence)
            val exportDir = File(cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "shelter_evidence_${evidence.id}_custody.json")
            file.writeText(json)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.files", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share chain of custody record"))
        } catch (e: Exception) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Export failed: ${e.message}") }
        }
    }

    private fun toggleEvidencePlayback(evidence: Evidence) {
        if (!vaultUnlocked) return
        val player = mediaPlayer
        if (playbackState.evidenceId == evidence.id && playbackState.isPreparing) return
        if (playbackState.evidenceId == evidence.id && player != null) {
            if (runCatching { player.isPlaying }.getOrDefault(false)) {
                runCatching { player.pause() }
                playbackPositionJob?.cancel()
                playbackState = playbackState.copy(isPlaying = false)
            } else {
                try {
                    if (playbackState.durationMs > 0L && playbackState.positionMs >= playbackState.durationMs) {
                        player.seekTo(0L, MediaPlayer.SEEK_CLOSEST)
                        playbackState = playbackState.copy(positionMs = 0L)
                    }
                    player.start()
                    playbackState = playbackState.copy(isPlaying = true)
                    startPlaybackPositionUpdates(playbackRequestId)
                } catch (e: Exception) {
                    stopEvidencePlayback()
                    lifecycleScope.launch { snackbarHostState.showSnackbar("Playback failed: ${e.message}") }
                }
            }
            return
        }
        playEvidence(evidence)
    }

    private fun playEvidence(evidence: Evidence) {
        if (!vaultUnlocked) return
        stopEvidencePlayback()
        val encryptedFile = File(filesDir, evidence.encryptedFile)
        if (!encryptedFile.exists()) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Encrypted file not found") }
            return
        }

        val requestId = ++playbackRequestId
        val tempAudio = File(cacheDir, "temp_play_${evidence.id}_$requestId.m4a")
        playbackState = EvidencePlaybackState(evidenceId = evidence.id, isPreparing = true)
        playbackLoadJob = lifecycleScope.launch {
            var playerOwnsTemp = false
            try {
                withContext(Dispatchers.IO) {
                    if (tempAudio.exists() && !tempAudio.delete()) throw IOException("Could not clear playback cache")
                    Crypto.decrypt(encryptedFile, tempAudio, key)
                }
                if (requestId != playbackRequestId || !vaultUnlocked || !foreground) return@launch

                val player = MediaPlayer()
                mediaPlayer = player
                playbackTempFile = tempAudio
                player.setOnPreparedListener { prepared ->
                    if (requestId != playbackRequestId || !vaultUnlocked || !foreground || mediaPlayer !== prepared) {
                        releasePlaybackResources(prepared, tempAudio)
                        return@setOnPreparedListener
                    }
                    try {
                        val duration = prepared.duration.toLong().coerceAtLeast(0L)
                        prepared.start()
                        playbackState = EvidencePlaybackState(
                            evidenceId = evidence.id,
                            isPlaying = true,
                            durationMs = duration
                        )
                        startPlaybackPositionUpdates(requestId)
                    } catch (e: Exception) {
                        stopEvidencePlayback()
                        lifecycleScope.launch { snackbarHostState.showSnackbar("Playback failed: ${e.message}") }
                    }
                }
                player.setOnCompletionListener { completed ->
                    if (requestId != playbackRequestId || mediaPlayer !== completed) return@setOnCompletionListener
                    playbackPositionJob?.cancel()
                    playbackState = playbackState.copy(
                        isPreparing = false,
                        isPlaying = false,
                        positionMs = playbackState.durationMs
                    )
                    releasePlaybackResources(completed, tempAudio)
                }
                player.setOnErrorListener { failed, _, _ ->
                    if (requestId == playbackRequestId && mediaPlayer === failed) {
                        releasePlaybackResources(failed, tempAudio)
                        playbackState = EvidencePlaybackState()
                        lifecycleScope.launch { snackbarHostState.showSnackbar("This evidence audio could not be played") }
                    }
                    true
                }
                player.setDataSource(tempAudio.absolutePath)
                player.prepareAsync()
                playerOwnsTemp = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (requestId == playbackRequestId) {
                    playbackRequestId++
                    playbackPositionJob?.cancel()
                    playbackPositionJob = null
                    mediaPlayer?.let { releasePlaybackResources(it, playbackTempFile) }
                    playbackState = EvidencePlaybackState()
                    snackbarHostState.showSnackbar("Decryption or playback failed: ${e.message}")
                }
            } finally {
                if (!playerOwnsTemp) withContext(NonCancellable + Dispatchers.IO) { tempAudio.delete() }
            }
        }
    }

    private fun startPlaybackPositionUpdates(requestId: Long) {
        playbackPositionJob?.cancel()
        playbackPositionJob = lifecycleScope.launch {
            while (requestId == playbackRequestId) {
                delay(250L)
                val player = mediaPlayer ?: return@launch
                val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
                if (!isPlaying) return@launch
                val position = runCatching { player.currentPosition.toLong() }.getOrDefault(playbackState.positionMs)
                playbackState = playbackState.copy(isPlaying = true, positionMs = position)
            }
        }
    }

    private fun seekEvidencePlayback(evidence: Evidence, positionMs: Long) {
        if (!vaultUnlocked || playbackState.evidenceId != evidence.id) return
        val player = mediaPlayer ?: return
        val bounded = positionMs.coerceIn(0L, playbackState.durationMs)
        try {
            player.seekTo(bounded, MediaPlayer.SEEK_CLOSEST)
            playbackState = playbackState.copy(positionMs = bounded)
        } catch (e: Exception) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Could not seek in this recording: ${e.message}") }
        }
    }

    private fun stopEvidencePlayback() {
        playbackRequestId++
        playbackLoadJob?.cancel()
        playbackLoadJob = null
        playbackPositionJob?.cancel()
        playbackPositionJob = null
        mediaPlayer?.let { releasePlaybackResources(it, playbackTempFile) }
        mediaPlayer = null
        playbackTempFile?.delete()
        playbackTempFile = null
        playbackState = EvidencePlaybackState()
    }

    private fun openMediaPreview(evidence: Evidence) {
        if (!vaultUnlocked || evidence.mediaType.equals("AUDIO", ignoreCase = true)) return
        closeMediaPreview()
        val encryptedFile = File(filesDir, evidence.encryptedFile)
        if (!encryptedFile.exists()) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Encrypted media file not found") }
            return
        }
        val requestId = ++mediaPreviewRequestId
        val extension = when {
            evidence.mimeType.contains("png", true) -> "png"
            evidence.mimeType.contains("webp", true) -> "webp"
            evidence.mimeType.contains("heic", true) -> "heic"
            evidence.mediaType.equals("VIDEO", true) -> "mp4"
            else -> "jpg"
        }
        val temporary = File(cacheDir, "temp_preview_${evidence.id}_$requestId.$extension")
        mediaPreviewState = EvidenceMediaPreviewState(
            evidenceId = evidence.id,
            mediaType = evidence.mediaType,
            mimeType = evidence.mimeType,
            isPreparing = true
        )
        mediaPreviewJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (temporary.exists() && !temporary.delete()) throw IOException("Could not clear preview cache")
                    Crypto.decrypt(encryptedFile, temporary, key)
                }
                if (requestId != mediaPreviewRequestId || !vaultUnlocked || !foreground) {
                    temporary.delete()
                    return@launch
                }
                mediaPreviewTempFile = temporary
                mediaPreviewState = EvidenceMediaPreviewState(
                    evidenceId = evidence.id,
                    mediaType = evidence.mediaType,
                    mimeType = evidence.mimeType,
                    filePath = temporary.absolutePath
                )
            } catch (cancelled: CancellationException) {
                temporary.delete()
                throw cancelled
            } catch (error: Exception) {
                temporary.delete()
                if (requestId == mediaPreviewRequestId) {
                    mediaPreviewState = EvidenceMediaPreviewState()
                    snackbarHostState.showSnackbar("Media preview failed: ${error.message}")
                }
            }
        }
    }

    private fun closeMediaPreview() {
        mediaPreviewRequestId++
        mediaPreviewJob?.cancel()
        mediaPreviewJob = null
        mediaPreviewTempFile?.delete()
        mediaPreviewTempFile = null
        mediaPreviewState = EvidenceMediaPreviewState()
    }

    private fun releasePlaybackResources(player: MediaPlayer, tempAudio: File?) {
        if (mediaPlayer === player) mediaPlayer = null
        runCatching { player.release() }
        tempAudio?.delete()
        if (playbackTempFile == tempAudio) playbackTempFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        gestureDetector.stop()
        speechRecognizer?.destroy()
        stopEvidencePlayback()
        analyzer.close()
    }
}
