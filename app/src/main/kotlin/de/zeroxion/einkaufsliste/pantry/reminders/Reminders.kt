package de.zeroxion.einkaufsliste.pantry.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.zeroxion.einkaufsliste.MainActivity
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.data.ShoppingRepository
import de.zeroxion.einkaufsliste.pantry.data.PantryRepository
import de.zeroxion.einkaufsliste.pantry.data.PantrySettings
import de.zeroxion.einkaufsliste.pantry.logic.ReminderPlan
import de.zeroxion.einkaufsliste.pantry.logic.shoppingName
import de.zeroxion.einkaufsliste.parsing.ParsedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

/**
 * Daily best-before check: an alarm at the chosen time looks at the pantry and shows one
 * notification listing everything that is newly due.
 */
object Reminders {
    private const val ACTION_CHECK = "de.zeroxion.einkaufsliste.action.MHD_CHECK"
    private const val CHANNEL = "mhd_reminder"
    private const val ID_REMINDER = 1001
    private const val ID_TEST = 1003

    /** Sets the alarm to the next reminder time (today or tomorrow). Replaces an existing one. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val settings = PantrySettings.get(context)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_MONTH, 1)
        }
        // Deliberately not an exact alarm (would need a special permission since Android 12):
        // setAndAllowWhileIdle also fires in doze mode, at most a few minutes late.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, checkIntent(context))
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Checks the pantry and notifies if something became due.
     * @param includeToday see [ReminderPlan.build]
     */
    @Synchronized
    fun check(context: Context, includeToday: Boolean) {
        // Without permission nothing is marked as reported – it is caught up later.
        if (!notificationsEnabled(context)) return
        val settings = PantrySettings.get(context)
        val items = PantryRepository.get(context).items.value.map { ReminderPlan.Item(it.id, it.name, it.date) }
        val plan = ReminderPlan.build(items, settings.notified, settings.leadDays, includeToday, LocalDate.now())
        settings.notified = plan.notified
        if (plan.hasNew) {
            show(context, ID_REMINDER, plan.title, plan.text, plan.bigText, plan.due.map { it.name })
        }
    }

    /** True if today's reminder time has already passed. */
    private fun timePassedToday(context: Context): Boolean {
        val settings = PantrySettings.get(context)
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) >= settings.hour * 60 + settings.minute
    }

    /**
     * Catches up a missed reminder (phone was off, Android swallowed the alarm) or reports right away
     * if something became due after today's reminder time.
     */
    fun checkIfTimePassed(context: Context, includeToday: Boolean) {
        if (timePassedToday(context)) check(context, includeToday)
    }

    /** After adding or changing products: a product added today that expires tomorrow would otherwise be late. */
    fun afterItemsChanged(context: Context) {
        schedule(context)
        checkIfTimePassed(context, includeToday = false)
    }

    fun notificationsEnabled(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun testNotification(context: Context) {
        val text = "So sieht die Erinnerung vor Ablauf des MHD aus."
        show(context, ID_TEST, "Test-Erinnerung", text, text, emptyList())
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, "MHD-Erinnerungen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Erinnerung vor Ablauf des Mindesthaltbarkeitsdatums"
        }
        nm.createNotificationChannel(channel)
    }

    private fun show(context: Context, id: Int, title: String, text: String, bigText: String, products: List<String>) {
        if (!notificationsEnabled(context)) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PANTRY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(0xFF0E9F6E.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
        if (products.isNotEmpty()) {
            val add = Intent(context, AddToShoppingReceiver::class.java)
                .putStringArrayListExtra(AddToShoppingReceiver.EXTRA_PRODUCTS, ArrayList(products))
                .putExtra(AddToShoppingReceiver.EXTRA_NOTIFICATION, id)
            val addIntent = PendingIntent.getBroadcast(
                context, id, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                R.drawable.ic_cart,
                if (products.size == 1) "Auf die Einkaufsliste" else "Alle auf die Einkaufsliste",
                addIntent,
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // permission was revoked in the meantime
        }
    }
}

/** Woken up daily at the chosen time. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.check(context, includeToday = true)
        Reminders.schedule(context) // next day
    }
}

/** Android deletes alarms on reboot and app update – they are set again here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Reminders.schedule(context)
                Reminders.checkIfTimePassed(context, includeToday = true)
            }
        }
    }
}

/** Notification button "Auf die Einkaufsliste": puts the due products on the shopping list. */
class AddToShoppingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val products = intent.getStringArrayListExtra(EXTRA_PRODUCTS).orEmpty()
        if (products.isEmpty()) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = ShoppingRepository.get(appContext).add(products.map { ParsedItem(shoppingName(it)) })
                NotificationManagerCompat.from(appContext).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
                withContext(Dispatchers.Main) {
                    val text = if (result.count == 1) {
                        "„${result.all.first().name}“ steht auf der Einkaufsliste"
                    } else {
                        "${result.count} Produkte stehen auf der Einkaufsliste"
                    }
                    Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCTS = "products"
        const val EXTRA_NOTIFICATION = "notification"
    }
}
