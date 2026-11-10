package com.matias.lembretealarme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class AlarmeActivity : ComponentActivity() {
    private val fechar = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // a) ícones da barra brancos sobre o fundo escuro
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        onBackPressedDispatcher.addCallback(this) { } // bloqueia o Voltar
        ContextCompat.registerReceiver(this, fechar, IntentFilter("FECHAR_ALARME"),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        mostrar(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); mostrar(intent)
    }

    private fun mostrar(i: Intent) {
        val falta = textoFalta(i.getLongExtra("antecedencia", 0L))
        val nome = i.getStringExtra("nome") ?: "Lembrete"
        val desc = i.getStringExtra("descricao") ?: ""
        val data = i.getStringExtra("data") ?: ""
        val hora = i.getStringExtra("hora") ?: ""
        val suave = Color.White.copy(alpha = 0.75f)
        setContent {
            TemaApp { // b) tema novo
                Column(
                    Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Marinho, Color(0xFF0F1626))))
                        .safeDrawingPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.6f))
                    Text("⏰", fontSize = 72.sp)
                    Spacer(Modifier.height(16.dp))
                    // c) cores novas
                    Text(falta, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ambar)
                    Spacer(Modifier.height(8.dp))
                    Text(nome, fontSize = 42.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, lineHeight = 48.sp, color = Color.White)
                    Spacer(Modifier.height(40.dp))
                    Text(hora, fontSize = 64.sp, fontWeight = FontWeight.Light, color = Color.White)
                    Text(data, fontSize = 26.sp, color = suave)
                    if (desc.isNotBlank()) {
                        Spacer(Modifier.height(40.dp))
                        Text(desc, fontSize = 24.sp, textAlign = TextAlign.Center,
                            lineHeight = 32.sp, color = suave)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            stopService(Intent(this@AlarmeActivity, AlarmeService::class.java))
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ambar, contentColor = Marinho)
                    ) { Text("Desligar", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(fechar)
        super.onDestroy()
    }
}
