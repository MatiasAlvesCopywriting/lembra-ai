package com.matias.lembretealarme

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Locale
import android.net.Uri
import androidx.activity.SystemBarStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.launch

data class Evento(
    val nome: String, val descricao: String, val data: String, val hora: String,
    val id: Int = kotlin.random.Random.nextInt(1, Int.MAX_VALUE)
)

fun millisDe(data: String, hora: String): Long =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).parse("$data $hora")!!.time

fun salvarEventos(context: Context, eventos: List<Evento>) {
    val array = JSONArray()
    eventos.forEach {
        array.put(JSONObject().apply {
            put("nome", it.nome); put("descricao", it.descricao)
            put("data", it.data); put("hora", it.hora); put("id", it.id)
        })
    }
    context.getSharedPreferences("lembretes", Context.MODE_PRIVATE)
        .edit().putString("eventos", array.toString()).apply()
}

fun carregarEventos(context: Context): List<Evento> {
    val json = context.getSharedPreferences("lembretes", Context.MODE_PRIVATE)
        .getString("eventos", "[]") ?: "[]"
    val array = JSONArray(json)
    return (0 until array.length()).map {
        val o = array.getJSONObject(it)
        val data = o.getString("data"); val hora = o.getString("hora")
        // eventos antigos (sem id) usam o código antigo, então continuam podendo ser cancelados
        val id = o.optInt("id", (millisDe(data, hora) / 1000).toInt())
        Evento(o.getString("nome"), o.getString("descricao"), data, hora, id)
    }
}

// Antecedências em minutos: no horário, 3h, 1 dia, 7 dias e 30 dias antes
val ANTECEDENCIAS = listOf(0L, 3 * 60L, 24 * 60L, 7 * 24 * 60L, 30 * 24 * 60L)

fun agendarAlarme(context: Context, e: Evento) {
    val horarioEvento = millisDe(e.data, e.hora)
    val am = context.getSystemService(AlarmManager::class.java)
    ANTECEDENCIAS.forEachIndexed { i, minutos ->
        val millis = horarioEvento - minutos * 60_000
        if (millis < System.currentTimeMillis()) return@forEachIndexed // aviso já passou
        val intent = Intent(context, AlarmeReceiver::class.java)
            .putExtra("nome", e.nome).putExtra("descricao", e.descricao)
            .putExtra("data", e.data).putExtra("hora", e.hora).putExtra("id", e.id)
            .putExtra("antecedencia", minutos)
        // cada aviso tem um código diferente (id + i), assim um não apaga o outro
        val pi = PendingIntent.getBroadcast(context, e.id + i, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, null), pi)
    }
}

fun cancelarAlarme(context: Context, e: Evento) {
    val am = context.getSystemService(AlarmManager::class.java)
    ANTECEDENCIAS.indices.forEach { i ->
        val intent = Intent(context, AlarmeReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, e.id + i, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) ?: return@forEach
        am.cancel(pi)
        pi.cancel()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent { TemaApp { App() } }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check(context) }

    var baixando by remember { mutableStateOf(false) }

    update?.let { u ->
        AlertDialog(
            onDismissRequest = { if (!baixando) update = null },
            title = { Text(if (baixando) "Baixando atualização..." else "Nova versão disponível! 🎉") },
            text = {
                if (baixando) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(color = Ambar)
                        Spacer(Modifier.height(12.dp))
                        Text("Aguarde, o instalador abre sozinho. ⏳")
                    }
                } else Text("A versão ${u.version} do Lembra Aí está pronta.")
            },
            confirmButton = {
                TextButton(
                    enabled = !baixando,
                    onClick = {
                        baixando = true
                        scope.launch {
                            runCatching { UpdateChecker.downloadAndInstall(context, u) }
                                .onFailure {
                                    android.widget.Toast.makeText(context, "Falha ao baixar. Tente de novo.",
                                        android.widget.Toast.LENGTH_SHORT).show()
                                }
                            baixando = false
                        }
                    }
                ) { Text("Atualizar") }
            },
            dismissButton = {
                if (!baixando) TextButton(onClick = { update = null }) { Text("Depois") }
            }
        )
    }

    val eventos = remember { mutableStateListOf<Evento>().apply { addAll(carregarEventos(context)) } }
    var editando by remember { mutableStateOf<Evento?>(null) }
    var explicar by remember { mutableStateOf(false) }

    fun abrirTelaCheia() {
        if (Build.VERSION.SDK_INT >= 34 &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")))
        }
    }

    val pedirNotif = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { abrirTelaCheia() }

    fun faltaPermissao(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return !nm.areNotificationsEnabled() ||
                (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent())
    }

    if (explicar) {
        AlertDialog(
            onDismissRequest = { explicar = false },
            title = { Text("Só mais um passo ⏰") },
            text = { Text("Para o alarme tocar mesmo com o celular bloqueado, " +
                    "o Android precisa da sua autorização. É rapidinho!") },
            confirmButton = {
                TextButton(onClick = {
                    explicar = false
                    if (Build.VERSION.SDK_INT >= 33)
                        pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else abrirTelaCheia()
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = { explicar = false }) { Text("Agora não") }
            }
        )
    }

    if (editando == null) {
        TelaInicial(
            eventos,
            onExcluir = {
                cancelarAlarme(context, it)
                eventos.remove(it)
                salvarEventos(context, eventos)
            },
            onEditar = { editando = it }
        ) {
            val c = Calendar.getInstance()
            DatePickerDialog(context, { _, ano, mes, dia ->
                editando = Evento("", "", "%02d/%02d/%04d".format(dia, mes + 1, ano), "")
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    } else {
        TelaEvento(
            inicial = editando!!,
            onSalvar = { novo ->
                cancelarAlarme(context, novo)
                eventos.removeAll { it.id == novo.id }
                eventos.add(novo)
                salvarEventos(context, eventos)
                agendarAlarme(context, novo)
                editando = null
                if (faltaPermissao()) explicar = true
            },
            onCancelar = { editando = null }
        )
    }
}

@Composable
fun TelaInicial(
    eventos: List<Evento>,
    onExcluir: (Evento) -> Unit,
    onEditar: (Evento) -> Unit,
    onMais: () -> Unit
) {
    val agora = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(eventos.count { millisDe(it.data, it.hora) >= agora })
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onMais, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ambar, contentColor = Marinho)
        ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        if (eventos.isEmpty()) Text("Nenhum evento ainda. Toque no +", color = TextoSuave)
        LazyColumn {
            items(eventos.sortedBy { millisDe(it.data, it.hora) }, key = { it.id }) { e ->
                val passou = millisDe(e.data, e.hora) < agora
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        .alpha(if (passou) 0.55f else 1f).clickable { onEditar(e) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (passou) Color(0xFFE6E7EB) else Color.White),
                    elevation = CardDefaults.cardElevation(if (passou) 0.dp else 3.dp)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        // faixa lateral: âmbar = vai acontecer, cinza = já passou
                        Box(Modifier.width(6.dp).fillMaxHeight()
                            .background(if (passou) TextoSuave else Ambar))
                        Row(Modifier.weight(1f).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.nome, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (passou) TextDecoration.LineThrough else null)
                                Text("${e.data} às ${e.hora}", fontSize = 14.sp, color = TextoSuave)
                                if (e.descricao.isNotBlank()) Text(e.descricao, fontSize = 14.sp)
                                if (passou) Text("Já passou", fontSize = 12.sp, color = TextoSuave)
                            }
                            IconButton(onClick = { onExcluir(e) }) {
                                Icon(painterResource(android.R.drawable.ic_menu_delete),
                                    contentDescription = "Excluir", tint = Color(0xFFC0392B))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(proximos: Int) {
    val hoje = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(java.util.Date())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Marinho, MarinhoClaro)))
            .padding(20.dp)
    ) {
        Text(hoje.replaceFirstChar { it.uppercase() },
            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("Meus Lembretes", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            when (proximos) {
                0 -> "Nenhum evento pela frente"
                1 -> "1 evento pela frente"
                else -> "$proximos eventos pela frente"
            },
            color = Ambar, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TelaEvento(inicial: Evento, onSalvar: (Evento) -> Unit, onCancelar: () -> Unit) {
    val context = LocalContext.current
    var nome by remember { mutableStateOf(inicial.nome) }
    var descricao by remember { mutableStateOf(inicial.descricao) }
    var data by remember { mutableStateOf(inicial.data) }
    var hora by remember { mutableStateOf(inicial.hora) }
    val editandoExistente = inicial.nome.isNotBlank()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(if (editandoExistente) "Editar evento" else "Novo evento", fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome do evento") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val (d, m, a) = data.split("/").map { it.toInt() }
            DatePickerDialog(context, { _, ano, mes, dia ->
                data = "%02d/%02d/%04d".format(dia, mes + 1, ano)
            }, a, m - 1, d).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Data: $data") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val c = Calendar.getInstance()
            TimePickerDialog(context, { _, h, m -> hora = "%02d:%02d".format(h, m) },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (hora.isEmpty()) "Escolher horário" else "Horário: $hora")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição (opcional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSalvar(inicial.copy(nome = nome, descricao = descricao, data = data, hora = hora)) },
            enabled = nome.isNotBlank() && hora.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (editandoExistente) "Salvar alterações" else "Programar evento") }
        TextButton(onClick = onCancelar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}
fun textoFalta(minutos: Long): String = when (minutos) {
    0L -> "É agora!"
    3 * 60L -> "Faltam 3 horas!"
    24 * 60L -> "Falta 1 dia!"
    7 * 24 * 60L -> "Faltam 7 dias!"
    30 * 24 * 60L -> "Faltam 30 dias!"
    else -> "Lembrete"
}
val Marinho = Color(0xFF1E2A4A)
val MarinhoClaro = Color(0xFF34466F)
val Ambar = Color(0xFFE0A458)
val Fundo = Color(0xFFF2F3F6)
val TextoSuave = Color(0xFF6B7280)

@Composable
fun TemaApp(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Marinho, onPrimary = Color.White,
        secondary = Ambar, onSecondary = Marinho,
        background = Fundo, surface = Color.White,
        onBackground = Marinho, onSurface = Marinho
    )
) { Surface(Modifier.fillMaxSize(), color = Fundo, content = content) }
