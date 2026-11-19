package de.zeroxion.einkaufsliste

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone so it can be shared on the next start –
 * there is no other way to get at the error without a computer and adb.
 */
object CrashReport {
    private fun file(context: Context) = File(context.applicationContext.filesDir, "absturz.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                } catch (e: Exception) {
                    "?"
                }
                file(app).writeText(
                    "Einkaufsliste $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                        "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN).format(Date()) +
                        " · Thread ${thread.name}\n\n" + trace.take(12_000),
                )
            } catch (e: Throwable) {
                // never fail while handling a crash
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}

/** After a crash: offers to share the error report (e.g. with Claude) once. */
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReport.read(context)) }
    val text = report ?: return
    val close = {
        CrashReport.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Die App ist abgestürzt") },
        text = {
            Text(
                "Beim letzten Mal ist ein Fehler passiert. Schick den Fehlerbericht weiter, damit er behoben werden kann.\n\n" +
                    text.lines().take(8).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Fehlerbericht Einkaufsliste")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, "Fehlerbericht teilen"))
                close()
            }) { Text("Bericht teilen") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Schließen") } },
    )
}
