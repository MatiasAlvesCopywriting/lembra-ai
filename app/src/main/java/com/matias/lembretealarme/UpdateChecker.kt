package com.matias.lembretealarme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val REPO = "MatiasAlvesCopywriting/lembra-ai" // troque!

    data class Update(val version: String, val apkUrl: String)

    // Retorna a atualização se houver versão mais nova; senão, null
    suspend fun check(ctx: Context): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())

            val remote = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .first { it.getString("name").endsWith(".apk") }
                .getString("browser_download_url")

            val local = ctx.packageManager
                .getPackageInfo(ctx.packageName, 0).versionName ?: "0"

            if (isNewer(remote, local)) Update(remote, apkUrl) else null
        }.getOrNull() // sem internet ou erro: só ignora
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    // Baixa o APK e abre o instalador
    suspend fun downloadAndInstall(ctx: Context, update: Update) {
        // Permissão "instalar apps desconhecidos" (só na 1ª vez)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "lembra-ai.apk")
            URL(update.apkUrl).openStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }

        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
    }
}
