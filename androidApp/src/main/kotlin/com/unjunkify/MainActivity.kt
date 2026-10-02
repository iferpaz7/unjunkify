package com.unjunkify

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unjunkify.data.JunkSnapshot
import com.unjunkify.scanner.AndroidJunkCleaner
import com.unjunkify.scanner.JunkCleaner
import com.unjunkify.sync.BatteryCheckWorker
import com.unjunkify.sync.StorageScanWorker
import com.unjunkify.ui.CleanViewModel
import com.unjunkify.ui.HealthViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Media read permissions appropriate for the current SDK:
         * granular READ_MEDIA_* on API 33+, legacy READ_EXTERNAL_STORAGE below.
         * The T2 delete-consent launcher reuses the ActivityResult pattern below.
         */
        fun mediaReadPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
    }

    private val requiredPermissions = mutableListOf<String>().apply {
        addAll(mediaReadPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Reusable RequestMultiplePermissions launcher pattern: request a set of
     * permissions, propagate media-denial state to the UI, then schedule
     * read-only workers. The delete-consent launcher below follows the same
     * ActivityResult pattern for the MediaStore consent dialog.
     */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            updateMediaPermissionState()
            scheduleHealthChecks()
        }

    /**
     * System delete-consent dialog result (`Access media files > Remove an
     * item`). RESULT_OK means the user granted deletion of the requested
     * batch; anything else leaves the gated snapshots listed.
     */
    private val deleteConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            deleteConsentShowing = false
            cleanViewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
        }

    /**
     * Optional usage-access opt-in (T4). PACKAGE_USAGE_STATS is a protected
     * special access, not a runtime permission: the only path is the system
     * Settings screen. On return, refresh Health so a fresh grant populates
     * the ranking; denial keeps battery/temperature working (FR-009).
     */
    private val usageAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            healthViewModel.refresh()
        }

    /** Open system Settings usage-access screen from the Health rationale card. */
    fun requestUsageAccess() {
        runCatching {
            usageAccessLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /** True while the system delete dialog is on screen (no double-launch). */
    private var deleteConsentShowing = false

    /** True once we have asked, so a missing grant counts as denial (FR-009 degrade). */
    private var mediaPermissionRequested = false

    private val cleanViewModel: CleanViewModel by viewModel()
    private val healthViewModel: HealthViewModel by viewModel()
    private val junkCleaner: JunkCleaner by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPermissionsIfNeeded()
        observeDeleteConsent()

        setContent {
            UnjunkifyApp(
                cleanViewModel = cleanViewModel,
                healthViewModel = healthViewModel,
                onRequestMediaPermission = ::requestMediaPermissions,
                onRequestUsageAccess = ::requestUsageAccess,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateMediaPermissionState()
    }

    /** Re-request media permissions, e.g. from the CleanScreen rationale card. */
    fun requestMediaPermissions() {
        val missing = mediaReadPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            updateMediaPermissionState()
            return
        }
        mediaPermissionRequested = true
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            mediaPermissionRequested = true
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            updateMediaPermissionState()
            scheduleHealthChecks()
        }
    }

    /**
     * Scan still runs on denial; affected media categories are skipped and
     * CleanScreen shows an inline explanation (FR-009 graceful degrade).
     * Images/video tracked separately so a partial grant (e.g. photos allowed,
     * videos denied) is reported honestly instead of as fully denied.
     */
    private fun updateMediaPermissionState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cleanViewModel.setMediaPermissionDenied(
                imagesDenied = mediaPermissionRequested &&
                    !isGranted(Manifest.permission.READ_MEDIA_IMAGES),
                videoDenied = mediaPermissionRequested &&
                    !isGranted(Manifest.permission.READ_MEDIA_VIDEO),
            )
        } else {
            cleanViewModel.setMediaPermissionDenied(
                mediaPermissionRequested &&
                    !isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
            )
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Batches foreign-media deletes into one system dialog per Android docs:
     * `MediaStore.createDeleteRequest()` on API 30+, the recoverable action's
     * IntentSender captured during the failed direct delete on API 29
     * (one item per round; the ViewModel re-queues the remainder).
     */
    private fun observeDeleteConsent() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cleanViewModel.pendingDeleteConsent.collect { pending ->
                    if (pending.isEmpty()) {
                        deleteConsentShowing = false
                    } else if (!deleteConsentShowing) {
                        deleteConsentShowing = true
                        launchDeleteConsent(pending)
                    }
                }
            }
        }
    }

    private fun launchDeleteConsent(pending: List<JunkSnapshot>) {
        try {
            val uris = pending.mapNotNull { runCatching { Uri.parse(it.pathOrKey) }.getOrNull() }
                .filter { it.scheme == ContentResolver.SCHEME_CONTENT }
            if (uris.isEmpty()) {
                deleteConsentShowing = false
                cleanViewModel.onDeleteConsentResult(false)
                return
            }
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createDeleteRequest(contentResolver, uris).intentSender
            } else {
                (junkCleaner as? AndroidJunkCleaner)?.takeConsentSender()
                    ?: run {
                        deleteConsentShowing = false
                        cleanViewModel.onDeleteConsentResult(false)
                        return
                    }
            }
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (_: Exception) {
            deleteConsentShowing = false
            cleanViewModel.onDeleteConsentResult(false)
        }
    }

    private fun scheduleHealthChecks() {
        val wm = WorkManager.getInstance(this)

        wm.enqueue(OneTimeWorkRequestBuilder<StorageScanWorker>().build())
        wm.enqueue(OneTimeWorkRequestBuilder<BatteryCheckWorker>().build())

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        wm.enqueueUniquePeriodicWork(
            "UnjunkifyStorageScan",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StorageScanWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "UnjunkifyBatteryCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BatteryCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
    }
}
