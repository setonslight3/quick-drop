package com.quickdrop.app.integration.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.quickdrop.app.MainActivity
import com.quickdrop.core.domain.ClassOccurrence
import com.quickdrop.core.domain.ReminderDecisionKind
import com.quickdrop.core.domain.ReminderPolicy
import com.quickdrop.core.model.UserPreferences
import com.quickdrop.core.time.AppClock

/**
 * Manages notification channels (Class, Study, Alarm), permission checks,
 * inexact AlarmManager scheduling for upcoming classes, quiet hours enforcement,
 * occurrence-ID deduplication, and deep links into exact contexts.
 */
class QuickDropNotificationScheduler(
    private val context: Context,
    private val clock: AppClock
) {
    companion object {
        const val CHANNEL_CLASS_REMINDERS = "quickdrop_channel_class_reminders"
        const val CHANNEL_STUDY_NUDGES = "quickdrop_channel_study_nudges"
        const val CHANNEL_WAKE_ALARM = "quickdrop_channel_wake_alarm"

        const val ACTION_CLASS_REMINDER = "com.quickdrop.app.ACTION_CLASS_REMINDER"
        const val ACTION_STUDY_NUDGE = "com.quickdrop.app.ACTION_STUDY_NUDGE"

        private const val DEDUPE_PREFS = "quickdrop_notification_dedupe"
        private const val KEY_DELIVERED_IDS = "delivered_occurrence_ids"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val dedupePrefs =
        context.getSharedPreferences(DEDUPE_PREFS, Context.MODE_PRIVATE)

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val classChannel = NotificationChannel(
                CHANNEL_CLASS_REMINDERS,
                "Class Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Inexact reminders before scheduled classes"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val studyChannel = NotificationChannel(
                CHANNEL_STUDY_NUDGES,
                "Study Prep & Drops",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Class preparation nudges and daily Drops"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_WAKE_ALARM,
                "Wake Alarms (Phase 3)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Opt-in class day wake alarms"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(
                listOf(classChannel, studyChannel, alarmChannel)
            )
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager.areNotificationsEnabled()
        }
    }

    fun getDeliveredOccurrenceIds(): Set<String> =
        dedupePrefs.getStringSet(KEY_DELIVERED_IDS, emptySet()) ?: emptySet()

    fun markOccurrenceDelivered(occurrenceId: String) {
        val updated = getDeliveredOccurrenceIds().toMutableSet().apply { add(occurrenceId) }
        dedupePrefs.edit().putStringSet(KEY_DELIVERED_IDS, updated).apply()
    }

    /**
     * Schedules or updates inexact class reminders for upcoming occurrences.
     * Uses `AlarmManager.ELAPSED_REALTIME_WAKEUP` / `RTC_WAKEUP` inexact `setWindow` so exact alarm
     * special access is never required for class reminders.
     */
    fun rebuildUpcomingClassReminders(
        occurrences: List<ClassOccurrence>,
        preferences: UserPreferences
    ) {
        ensureChannelsCreated()
        val granted = isNotificationPermissionGranted()
        val delivered = getDeliveredOccurrenceIds()

        for (occ in occurrences) {
            val evaluation = ReminderPolicy.evaluateClassReminder(
                occurrence = occ,
                preferences = preferences,
                notificationPermissionGranted = granted,
                alreadyDeliveredOccurrenceIds = delivered,
                clock = clock
            )

            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ACTION_CLASS_REMINDER
                putExtra("occurrenceId", occ.occurrenceId)
                putExtra("subjectId", occ.subjectId)
                putExtra("classStartEpochMs", occ.startInstant.toEpochMilli())
                putExtra("deepLinkUri", evaluation.deepLinkUri)
            }
            val requestCode = occ.occurrenceId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            when (evaluation.decision) {
                ReminderDecisionKind.DELIVER_NOW,
                ReminderDecisionKind.DEFER_UNTIL_QUIET_HOURS_END -> {
                    val triggerMs = evaluation.scheduledTriggerInstant?.toEpochMilli() ?: continue
                    // Best-effort inexact window (10 minutes) respecting OS battery/Doze policies
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerMs,
                        10 * 60_000L,
                        pendingIntent
                    )
                }
                ReminderDecisionKind.SUPPRESS_STALE_PAST_CLASS_START,
                ReminderDecisionKind.SUPPRESS_DISABLED_OR_NO_PERMISSION,
                ReminderDecisionKind.SUPPRESS_DUPLICATE_OCCURRENCE -> {
                    alarmManager.cancel(pendingIntent)
                }
            }
        }
    }

    fun showClassReminderNotification(
        occurrenceId: String,
        subjectName: String,
        deepLinkUri: String
    ) {
        if (!isNotificationPermissionGranted()) return
        if (occurrenceId in getDeliveredOccurrenceIds()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLinkUri)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            occurrenceId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Upcoming class: $subjectName")
            .setContentText("Tap to review your latest topic quiz before class starts.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(occurrenceId.hashCode(), notification)
        markOccurrenceDelivered(occurrenceId)
    }
}

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? com.quickdrop.app.QuickDropApplication ?: return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Reschedule future reminders after reboot or timezone change
                app.rescheduleRemindersAsync()
            }
            QuickDropNotificationScheduler.ACTION_CLASS_REMINDER -> {
                val occurrenceId = intent.getStringExtra("occurrenceId") ?: return
                val classStartMs = intent.getLongExtra("classStartEpochMs", 0L)
                val deepLink = intent.getStringExtra("deepLinkUri") ?: "quickdrop://open/home"
                // Discard stale class reminder if OS deferred delivery until after class start
                if (classStartMs > 0L && app.clock.now().toEpochMilli() >= classStartMs) {
                    return
                }
                app.notificationScheduler.showClassReminderNotification(
                    occurrenceId = occurrenceId,
                    subjectName = "Scheduled Class",
                    deepLinkUri = deepLink
                )
            }
        }
    }
}
