package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DiagnosticLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String,
    val errorCode: String? = null,
    val details: String? = null
)

data class SystemCheck(
    val title: String,
    val isPassed: Boolean,
    val summary: String,
    val detail: String? = null
)

object DiagnosticLogger {
    private const val TAG = "SPOTR_DIAGNOSTICS"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    private val _systemChecks = MutableStateFlow<List<SystemCheck>>(emptyList())
    val systemChecks: StateFlow<List<SystemCheck>> = _systemChecks.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun log(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        errorCode: String? = null,
        details: String? = null,
        throwable: Throwable? = null
    ) {
        val timestamp = timeFormat.format(Date())
        val computedDetails = details ?: throwable?.let {
            buildString {
                append(it::class.java.name)
                append(": ")
                append(it.message ?: "No message")
                it.cause?.let { cause ->
                    append("\nCaused by: ")
                    append(cause::class.java.name)
                    append(": ")
                    append(cause.message)
                }
            }
        }

        val entry = DiagnosticLogEntry(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            errorCode = errorCode,
            details = computedDetails
        )

        // Android logcat mirroring
        val logcatMsg = buildString {
            append("[$tag] ")
            if (!errorCode.isNullOrBlank()) append("[$errorCode] ")
            append(message)
            if (!computedDetails.isNullOrBlank()) append("\n$computedDetails")
        }

        when (level) {
            DiagnosticLevel.INFO -> Log.i(TAG, logcatMsg)
            DiagnosticLevel.SUCCESS -> Log.i(TAG, "SUCCESS: $logcatMsg")
            DiagnosticLevel.WARN -> Log.w(TAG, logcatMsg)
            DiagnosticLevel.ERROR -> Log.e(TAG, logcatMsg)
        }

        // Keep last 150 entries in memory
        _logs.value = (_logs.value + entry).takeLast(150)
    }

    fun logInfo(tag: String, message: String, details: String? = null) {
        log(DiagnosticLevel.INFO, tag, message, details = details)
    }

    fun logSuccess(tag: String, message: String, details: String? = null) {
        log(DiagnosticLevel.SUCCESS, tag, message, details = details)
    }

    fun logWarn(tag: String, message: String, errorCode: String? = null, details: String? = null) {
        log(DiagnosticLevel.WARN, tag, message, errorCode = errorCode, details = details)
    }

    fun logError(
        tag: String,
        message: String,
        errorCode: String? = null,
        details: String? = null,
        throwable: Throwable? = null
    ) {
        log(DiagnosticLevel.ERROR, tag, message, errorCode = errorCode, details = details, throwable = throwable)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun showToast(context: Context, message: String, isLong: Boolean = true) {
        mainHandler.post {
            try {
                Toast.makeText(
                    context.applicationContext,
                    message,
                    if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show toast: ${e.message}")
            }
        }
    }

    fun runStartupDiagnostics(context: Context) {
        logInfo("STARTUP", "Initializing SPOTR startup diagnostics...")
        val checks = mutableListOf<SystemCheck>()

        // 1. Firebase App Check
        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                val app = FirebaseApp.getInstance()
                val projectId = app.options.projectId.orEmpty()
                checks.add(
                    SystemCheck(
                        title = "Firebase App",
                        isPassed = true,
                        summary = "Initialized (${app.name})",
                        detail = "Project ID: $projectId, App ID: ${app.options.applicationId}"
                    )
                )
                logSuccess("FIREBASE", "FirebaseApp initialized for project: $projectId")
            } else {
                checks.add(
                    SystemCheck(
                        title = "Firebase App",
                        isPassed = false,
                        summary = "Not initialized",
                        detail = "No FirebaseApp instances found. google-services.json may be missing."
                    )
                )
                logError("FIREBASE", "No FirebaseApp instances found in context")
            }
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "Firebase App",
                    isPassed = false,
                    summary = "Initialization Error",
                    detail = e.message
                )
            )
            logError("FIREBASE", "Error inspecting FirebaseApp", throwable = e)
        }

        // 2. Google Play Services Check
        try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            val isSuccess = resultCode == ConnectionResult.SUCCESS
            val statusString = when (resultCode) {
                ConnectionResult.SUCCESS -> "Available (SUCCESS: 0)"
                ConnectionResult.SERVICE_MISSING -> "SERVICE_MISSING (1)"
                ConnectionResult.SERVICE_UPDATING -> "SERVICE_UPDATING (2)"
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "SERVICE_VERSION_UPDATE_REQUIRED (3)"
                ConnectionResult.SERVICE_DISABLED -> "SERVICE_DISABLED (4)"
                ConnectionResult.SERVICE_INVALID -> "SERVICE_INVALID (9)"
                else -> "Play Services Code: $resultCode"
            }
            checks.add(
                SystemCheck(
                    title = "Google Play Services",
                    isPassed = isSuccess,
                    summary = statusString,
                    detail = if (isSuccess) "Play Services is active and ready" else "Google Play Services error: $statusString"
                )
            )
            if (isSuccess) {
                logSuccess("PLAY_SERVICES", "Google Play Services available ($statusString)")
            } else {
                logWarn("PLAY_SERVICES", "Google Play Services unavailable: $statusString", errorCode = "$resultCode")
            }
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "Google Play Services",
                    isPassed = false,
                    summary = "Check Failed",
                    detail = e.message
                )
            )
            logError("PLAY_SERVICES", "Failed to check Play Services availability", throwable = e)
        }

        // 3. OAuth Default Web Client ID Check
        try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val isValid = serverClientId.isNotBlank() && serverClientId.endsWith(".apps.googleusercontent.com")
            checks.add(
                SystemCheck(
                    title = "OAuth Web Client ID",
                    isPassed = isValid,
                    summary = if (isValid) "Configured in resources" else "Invalid / Missing",
                    detail = "Client ID: ${maskClientId(serverClientId)}"
                )
            )
            if (isValid) {
                logSuccess("AUTH_CONFIG", "default_web_client_id verified: ${maskClientId(serverClientId)}")
            } else {
                logError("AUTH_CONFIG", "default_web_client_id invalid or blank: '$serverClientId'")
            }
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "OAuth Web Client ID",
                    isPassed = false,
                    summary = "Resource missing",
                    detail = "R.string.default_web_client_id not found: ${e.message}"
                )
            )
            logError("AUTH_CONFIG", "Failed to retrieve default_web_client_id", throwable = e)
        }

        // 4. Runtime APK SHA-1 Fingerprint
        try {
            val runtimeSha1 = getCertificateSha1(context)
            val expectedHash = "53:CF:92:BE:44:BD:99:1A:D8:F7:3D:0A:EC:75:A6:B9:4A:3E:53:8E"
            val isMatch = runtimeSha1.equals(expectedHash, ignoreCase = true)
            checks.add(
                SystemCheck(
                    title = "APK SHA-1 Signing Hash",
                    isPassed = true,
                    summary = if (isMatch) "Matches debug keystore" else "Detected runtime SHA-1",
                    detail = "SHA-1: $runtimeSha1"
                )
            )
            logInfo("SIGNATURE", "Runtime APK SHA-1 fingerprint: $runtimeSha1 (Expected debug: $expectedHash)")
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "APK SHA-1 Signing Hash",
                    isPassed = false,
                    summary = "Failed to read",
                    detail = e.message
                )
            )
            logWarn("SIGNATURE", "Could not extract runtime SHA-1 fingerprint: ${e.message}")
        }

        // 5. Firebase Auth Current User & Status
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            checks.add(
                SystemCheck(
                    title = "Firebase Auth State",
                    isPassed = true,
                    summary = if (user != null) "Logged in (${user.email ?: user.uid})" else "No active session",
                    detail = if (user != null) "UID: ${user.uid}" else "Ready for Google Sign-In"
                )
            )
            logInfo("AUTH", if (user != null) "Active user: ${user.uid}" else "No current Firebase user")
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "Firebase Auth State",
                    isPassed = false,
                    summary = "Auth instance error",
                    detail = e.message
                )
            )
            logError("AUTH", "Error inspecting FirebaseAuth.getInstance()", throwable = e)
        }

        // 6. Firestore Database ID Check
        try {
            val dbId = context.getString(R.string.firestore_database_id)
            checks.add(
                SystemCheck(
                    title = "Firestore Database ID",
                    isPassed = dbId.isNotBlank(),
                    summary = if (dbId.isNotBlank()) "Configured" else "Missing",
                    detail = "Database ID: $dbId"
                )
            )
            logInfo("FIRESTORE", "Target Firestore Database: $dbId")
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "Firestore Database ID",
                    isPassed = false,
                    summary = "Missing config",
                    detail = e.message
                )
            )
            logError("FIRESTORE", "Failed to get firestore_database_id", throwable = e)
        }

        // 7. Network Connectivity
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(network)
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            checks.add(
                SystemCheck(
                    title = "Network Connectivity",
                    isPassed = hasInternet,
                    summary = if (hasInternet) "Online (Internet verified)" else "No active connection",
                    detail = if (hasInternet) "Wi-Fi or Mobile data connected" else "Check phone network settings"
                )
            )
            if (hasInternet) {
                logSuccess("NETWORK", "Device has active internet connectivity")
            } else {
                logWarn("NETWORK", "Device appears offline. Firebase operations may fail.", errorCode = "OFFLINE")
            }
        } catch (e: Exception) {
            checks.add(
                SystemCheck(
                    title = "Network Connectivity",
                    isPassed = false,
                    summary = "Network check error",
                    detail = e.message
                )
            )
            logWarn("NETWORK", "Could not check network connectivity", details = e.message)
        }

        _systemChecks.value = checks
        logSuccess("STARTUP", "Startup diagnostics completed. Total checks: ${checks.size}")
    }

    fun getCertificateSha1(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val cert = signatures?.firstOrNull()?.toByteArray() ?: return "No certificate found"
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun parseErrorCode(throwable: Throwable): String {
        // 1. FirebaseAuthException specific code
        if (throwable is FirebaseAuthException) {
            return throwable.errorCode
        }

        val msg = throwable.message.orEmpty()
        val causeMsg = throwable.cause?.message.orEmpty()
        val combined = "$msg $causeMsg"

        // 2. Common Google Sign-In / Credential Manager numeric codes
        return when {
            combined.contains("10:") || combined.contains("DEVELOPER_ERROR") -> "DEVELOPER_ERROR (10)"
            combined.contains("16:") || combined.contains("CANCELED") -> "CANCELED (16)"
            combined.contains("7:") || combined.contains("NETWORK_ERROR") -> "NETWORK_ERROR (7)"
            combined.contains("8:") || combined.contains("INTERNAL_ERROR") -> "INTERNAL_ERROR (8)"
            combined.contains("12500") -> "SIGN_IN_FAILED (12500)"
            combined.contains("12501") -> "USER_CANCELLED (12501)"
            combined.contains("12502") -> "SIGN_IN_CURRENTLY_IN_PROGRESS (12502)"
            throwable::class.java.simpleName.isNotBlank() -> throwable::class.java.simpleName
            else -> "UNKNOWN_ERROR"
        }
    }

    private fun maskClientId(clientId: String): String {
        return if (clientId.length > 20) {
            clientId.take(12) + "..." + clientId.takeLast(24)
        } else {
            clientId
        }
    }

    fun getFormattedReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=== SPOTR SYSTEM & AUTH DIAGNOSTIC REPORT ===")
        sb.appendLine("Generated at: ${timeFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("--- SYSTEM CHECKS ---")
        _systemChecks.value.forEach { check ->
            val status = if (check.isPassed) "[PASS]" else "[FAIL]"
            sb.appendLine("$status ${check.title}: ${check.summary}")
            if (!check.detail.isNullOrBlank()) {
                sb.appendLine("   Detail: ${check.detail}")
            }
        }
        sb.appendLine()
        sb.appendLine("--- EVENT LOGS (${_logs.value.size}) ---")
        _logs.value.forEach { entry ->
            val codeStr = entry.errorCode?.let { " [$it]" } ?: ""
            sb.appendLine("${entry.timestamp} [${entry.level}] [${entry.tag}]$codeStr ${entry.message}")
            if (!entry.details.isNullOrBlank()) {
                sb.appendLine("   Details: ${entry.details.replace("\n", "\n   ")}")
            }
        }
        sb.appendLine("=== END REPORT ===")
        return sb.toString()
    }
}
