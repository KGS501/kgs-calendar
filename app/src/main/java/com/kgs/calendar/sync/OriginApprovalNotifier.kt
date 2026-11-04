package com.kgs.calendar.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kgs.calendar.MainActivity
import com.kgs.calendar.R
import com.kgs.calendar.data.local.entity.AccountEntity
import com.kgs.calendar.data.remote.OriginApprovalNotice
import com.kgs.calendar.data.remote.originApprovalNotices

/**
 * Tells the user once about each account that waits for an origin approval, and takes the
 * notification back when the account no longer does. Every failing sync passes through here, so
 * what was already announced is remembered.
 */
internal object OriginApprovalNotifier {
    private const val CHANNEL_ID = "kgs_sync_attention"
    private const val PREFS = "kgs_origin_approval_notices"
    private const val KEY_ANNOUNCED = "announced"
    private const val NOTIFICATION_TAG = "kgs-origin-approval"

    fun update(context: Context, accounts: List<AccountEntity>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val announced = prefs.getStringSet(KEY_ANNOUNCED, emptySet()).orEmpty()
        val notices = accounts.originApprovalNotices()
        val manager = NotificationManagerCompat.from(context)
        (announced - notices.map { it.key }.toSet()).forEach { manager.cancel(NOTIFICATION_TAG, it.hashCode()) }
        // Announced while notifications are off, it would never be seen; it stays due until they are on.
        val shown = if (manager.areNotificationsEnabled()) {
            ensureChannel(context)
            notices.filter { it.key !in announced }.forEach { post(context, manager, it) }
            notices
        } else {
            notices.filter { it.key in announced }
        }
        prefs.edit().putStringSet(KEY_ANNOUNCED, shown.map { it.key }.toSet()).apply()
    }

    private fun post(context: Context, manager: NotificationManagerCompat, notice: OriginApprovalNotice) {
        val open = PendingIntent.getActivity(
            context,
            notice.key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.origin_approval_notification_body, notice.origin)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.origin_approval_notification_title, notice.accountName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // POST_NOTIFICATIONS may be revoked at any moment; that only costs this notice.
        runCatching { manager.notify(NOTIFICATION_TAG, notice.key.hashCode(), notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_attention_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.sync_attention_channel_description) },
        )
    }
}
