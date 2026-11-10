package com.matias.lembretealarme

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import android.os.Build
class AlarmeService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == "PARAR") { stopSelf(); return START_NOT_STICKY }

        val nm = getSystemService(NotificationManager::class.java)
        // canal novo e sem som: quem toca agora é o serviço
        nm.createNotificationChannel(
            NotificationChannel("alarme_v2", "Alarmes", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) }
        )

        val tela = Intent(this, AlarmeActivity::class.java).putExtras(intent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val piTela = PendingIntent.getActivity(this, 0, tela,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val piParar = PendingIntent.getService(this, 1,
            Intent(this, AlarmeService::class.java).setAction("PARAR"),
            PendingIntent.FLAG_IMMUTABLE)

        val falta = textoFalta(intent.getLongExtra("antecedencia", 0L))
        val desc = intent.getStringExtra("descricao") ?: ""
        val notif = NotificationCompat.Builder(this, "alarme_v2")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(intent.getStringExtra("nome") ?: "Lembrete")
            .setContentText(if (desc.isBlank()) falta else "$falta • $desc")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(piTela, true)
            .setContentIntent(piTela)
            .setOngoing(true)
            .addAction(0, "Desligar", piParar)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notif)
        }

        if (player == null) {
            // toque de alarme do usuário; se não existir, usa o nosso alarme.mp3
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: Uri.parse("android.resource://$packageName/${R.raw.alarme}")
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                try { setDataSource(this@AlarmeService, uri) }
                catch (e: Exception) {
                    reset()
                    setDataSource(this@AlarmeService,
                        Uri.parse("android.resource://$packageName/${R.raw.alarme}"))
                }
                isLooping = true
                prepare(); start()
            }
        }

        if (Settings.canDrawOverlays(this)) startActivity(tela) // abre sozinha mesmo desbloqueado
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release(); player = null
        sendBroadcast(Intent("FECHAR_ALARME").setPackage(packageName)) // fecha a tela
        super.onDestroy()
    }
}
