package com.miguelrodriguez19.stayleafy.platform.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareType
import com.miguelrodriguez19.stayleafy.model.ReminderScheduleType
import com.miguelrodriguez19.stayleafy.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderNotificationGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotificationGateway {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            REMINDER_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && notificationManager.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    override fun publish(delivery: PendingReminderDelivery): ReminderNotificationResult {
        if (!canPostNotifications()) return ReminderNotificationResult.BLOCKED
        ensureChannel()
        val locale = context.resources.configuration.locales[0]
        val careName = context.getString(delivery.careType.titleResource())
        val dueDate = LocalDate.ofEpochDay(delivery.dueEpochDay).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )
        val notification = NotificationCompat.Builder(context, REMINDER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                context.getString(
                    if (delivery.scheduleType == ReminderScheduleType.SEASONAL) {
                        R.string.notification_season_title
                    } else {
                        R.string.notification_due_title
                    },
                    careName,
                ),
            )
            .setContentText(
                context.getString(
                    if (delivery.scheduleType == ReminderScheduleType.SEASONAL) {
                        R.string.notification_season_body
                    } else {
                        R.string.notification_due_body
                    },
                    delivery.plantName,
                    careName.lowercase(locale),
                    dueDate,
                ),
            )
            .setContentIntent(delivery.contentIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(
            reminderNotificationTag(delivery.noticeId),
            REMINDER_NOTIFICATION_ID,
            notification,
        )
        return ReminderNotificationResult.POSTED
    }

    override fun cancel(noticeId: String) {
        notificationManager.cancel(reminderNotificationTag(noticeId), REMINDER_NOTIFICATION_ID)
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
    }

    private fun PendingReminderDelivery.contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("stayleafy://notice/${Uri.encode(noticeId)}"))
            .putExtra(EXTRA_NOTICE_ID, noticeId)
            .putExtra(EXTRA_PLANT_ID, plantId)
            .putExtra(EXTRA_OPEN_NOTICES, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun CareType.titleResource(): Int = when (this) {
    CareType.WATERING -> R.string.care_watering
    CareType.PRUNING -> R.string.care_pruning
    CareType.FERTILIZING -> R.string.care_fertilizing
}
