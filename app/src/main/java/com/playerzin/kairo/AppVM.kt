package com.playerzin.kairo

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val MODELO_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
const val SISTEMA = "Você é a Kairo, uma IA simpática que roda direto no celular do usuário. Responda sempre em português do Brasil, de forma clara e objetiva."

class Msg(val role: String, texto: String) { var text by mutableStateOf(texto) }
class Conversa(val id: String, titulo: String) {
    var titulo by mutableStateOf(titulo)
    val msgs = mutableStateListOf<Msg>()
}

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kairo", 0)
    private val arquivoModelo = File(app.getExternalFilesDir(null) ?: app.filesDir, "model.gguf")

    var nome by mutableStateOf<String?>(prefs.getString("nome", null))
    val conversas = mutableStateListOf<Conversa>()
    var atualId by mutableStateOf<String?>(null)
    var modeloPronto by mutableStateOf(false)
    var carregandoModelo by mutableStateOf(false)
    var progresso by mutableStateOf(-1f)
    var gerando by mutableStateOf(false)
    var erro by mutableStateOf("")

    init {
        carregarConversas()
        novaConversa()
        carregarModelo()
    }

    fun entrar(n: String) { nome = n; prefs.edit().putString("nome", n).apply() }
    fun sair() { nome = null; prefs.edit().remove("nome").apply() }

    fun atual(): Conversa? = conversas.firstOrNull { it.id == atualId }

    fun novaConversa() {
        val vazia = conversas.firstOrNull { it.msgs.isEmpty() }
        if (vazia != null) { atualId = vazia.id; return }
        val c = Conversa(System.currentTimeMillis().toString(), "Nova conversa")
        conversas.add(0, c)
        atualId = c.id
    }

    fun abrir(id: String) { atualId = id }

    fun apagar(id: String) {
        if (gerando && id == atualId) Native.parar()
        conversas.removeAll { it.id == id }
        if (atualId == id) atualId = conversas.firstOrNull()?.id
        if (conversas.isEmpty()) novaConversa()
        salvar()
    }

    fun parar() { Native.parar() }

    fun enviar(texto: String) {
        if (!modeloPronto || gerando || texto.isBlank()) return
        var conv = atual()
        if (conv == null) { novaConversa(); conv = atual() }
        val c = conv ?: return
        c.msgs.add(Msg("user", texto))
        if (c.titulo == "Nova conversa") c.titulo = texto.take(30)
        val resp = Msg("assistant", "")
        c.msgs.add(resp)
        gerando = true
        viewModelScope.launch(Dispatchers.Default) {
            val roles = arrayListOf("system")
            val conts = arrayListOf(SISTEMA)
            c.msgs.dropLast(1).forEach { roles.add(it.role); conts.add(it.text) }
            val err = Native.gerar(roles.toTypedArray(), conts.toTypedArray(), TokenCallback { resp.text += it })
            if (err.isNotEmpty()) resp.text += err
            gerando = false
            salvar()
        }
    }

    fun carregarModelo() {
        if (!arquivoModelo.exists() || carregandoModelo) return
        carregandoModelo = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = Native.carregar(arquivoModelo.absolutePath)
            modeloPronto = ok
            carregandoModelo = false
            if (!ok) erro = "Não consegui carregar o modelo."
        }
    }

    fun baixarModelo() {
        if (progresso >= 0f) return
        erro = ""
        progresso = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(arquivoModelo.path + ".part")
                val c = URL(MODELO_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                val total = c.contentLengthLong
                c.inputStream.use { i ->
                    tmp.outputStream().use { o ->
                        val b = ByteArray(65536)
                        var d = 0L
                        while (true) {
                            val r = i.read(b)
                            if (r < 0) break
                            o.write(b, 0, r)
                            d += r
                            if (total > 0) progresso = d.toFloat() / total
                        }
                    }
                }
                tmp.renameTo(arquivoModelo)
            } catch (e: Exception) {
                erro = "Falha no download: ${e.message}"
            }
            progresso = -1f
            carregarModelo()
        }
    }

    fun salvar() {
        try {
            val arr = JSONArray()
            conversas.filter { it.msgs.isNotEmpty() }.forEach { c ->
                val m = JSONArray()
                c.msgs.forEach { m.put(JSONObject().put("r", it.role).put("x", it.text)) }
                arr.put(JSONObject().put("id", c.id).put("t", c.titulo).put("m", m))
            }
            File(app.filesDir, "conversas.json").writeText(arr.toString())
        } catch (e: Exception) { }
    }

    private fun carregarConversas() {
        try {
            val f = File(app.filesDir, "conversas.json")
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val c = Conversa(o.getString("id"), o.getString("t"))
                val m = o.getJSONArray("m")
                for (j in 0 until m.length()) {
                    val mo = m.getJSONObject(j)
                    c.msgs.add(Msg(mo.getString("r"), mo.getString("x")))
                }
                conversas.add(c)
            }
        } catch (e: Exception) { }
    }
}
