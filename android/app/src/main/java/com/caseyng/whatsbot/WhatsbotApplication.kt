package com.caseyng.whatsbot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Application entry point.
 *
 * Responsibilities:
 * - Plant Timber logging tree (debug builds only).
 * - Register the three notification channels required by the manifest:
 *     1. service_status  — IMPORTANCE_LOW  (persistent foreground service ticker)
 *     2. action_alerts   — IMPORTANCE_DEFAULT (automation events)
 *     3. errors          — IMPORTANCE_HIGH (connection failures, fatal errors)
 *
 * Manual DI singletons are initialised here as lazy properties so that
 * the object graph is built on first access, not at Application startup.
 */
class WhatsbotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        plantLogger()
        createNotificationChannels()
    }

    // ── Logging ──────────────────────────────────────────────────────────────

    private fun plantLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // TODO(production): plant a file-based or remote crash-reporting tree here.
    }

    // ── Notification channels ─────────────────────────────────────────────────

    private fun createNotificationChannels() {
        // NotificationChannel is a no-op below API 26, but minSdk is 26 so the
        // Build.VERSION check is kept for clarity and future-proofing.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService<NotificationManager>() ?: return

        val channels = listOf(
            NotificationChannel(
                getString(R.string.notification_channel_service_status_id),
                getString(R.string.notification_channel_service_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_service_status_description)
                setShowBadge(false)
            },

            NotificationChannel(
                getString(R.string.notification_channel_action_alerts_id),
                getString(R.string.notification_channel_action_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_action_alerts_description)
            },

            NotificationChannel(
                getString(R.string.notification_channel_errors_id),
                getString(R.string.notification_channel_errors_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.notification_channel_errors_description)
            },
        )

        manager.createNotificationChannels(channels)
    }
}
