package com.playerzin.kairo

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

const val MODELO_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
const val SISTEMA = "Você é a Kairo, uma IA simpática que roda direto no celular do usuário. Responda sempre em português do Brasil. Seja curta e direta, a menos que peçam detalhes."

class Msg(val role: String, texto: String) { var text by mutableStateOf(texto) }
class Conversa(val id: String, titulo: String) {
    var titulo by mutableStateOf(titulo)
    val msgs = mutableStateListOf<Msg>()
}

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kairo", 0)
    private val arquivoModelo = File(app.filesDir, "model.gguf")

    var nome by mutableStateOf<String?>(prefs.getString("nome", null))
    val conversas = mutableStateListOf<Conversa>()
    var atualId by mutableStateOf<String?>(null)
    var modeloPronto by mutableStateOf(false)
    var carregandoModelo by mutableStateOf(false)
    var progresso by mutableStateOf(-1f)
    var status by mutableStateOf("")
    var gerando by mutableStateOf(false)
    var erro by mutableStateOf("")

    init {
        carregarConversas()
        novaConversa()
        prepararModelo()
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

    private fun temAsset(): Boolean = try {
        app.assets.list("")?.contains("model.gguf") == true
    } catch (e: Exception) { false }

    fun prepararModelo() {
        if (arquivoModelo.exists()) { carregarModelo(); return }
        val antigo = File(app.getExternalFilesDir(null) ?: app.filesDir, "model.gguf")
        val doAsset = temAsset()
        if (!antigo.exists() && !doAsset) return
        status = if (antigo.exists()) "Movendo o cérebro" else "Preparando o cérebro (só na 1ª vez)"
        progresso = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(arquivoModelo.path + ".part")
                val ent: java.io.InputStream
                val total: Long
                if (antigo.exists()) {
                    ent = antigo.inputStream()
                    total = antigo.length()
                } else {
                    total = try { app.assets.openFd("model.gguf").use { it.length } } catch (e: Exception) { -1L }
                    ent = app.assets.open("model.gguf")
                }
                ent.use { i ->
                    tmp.outputStream().use { o ->
                        val b = ByteArray(1 shl 20)
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
                if (tmp.renameTo(arquivoModelo) && antigo.exists()) antigo.delete()
            } catch (e: Exception) {
                erro = "Falha ao preparar: ${e.message}"
            }
            progresso = -1f
            carregarModelo()
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
        status = "Baixando o cérebro"
        progresso = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(arquivoModelo.path + ".part")
                val ja = if (tmp.exists()) tmp.length() else 0L
                val c = URL(MODELO_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                if (ja > 0) c.setRequestProperty("Range", "bytes=$ja-")
                val code = c.responseCode
                if (code == 416) {
                    tmp.renameTo(arquivoModelo)
                } else {
                    val append = code == 206
                    val total = if (append) ja + c.contentLengthLong else c.contentLengthLong
                    var d = if (append) ja else 0L
                    c.inputStream.use { i ->
                        FileOutputStream(tmp, append).use { o ->
                            val b = ByteArray(1 shl 18)
                            while (true) {
                                val r = i.read(b)
                                if (r < 0) break
                                o.write(b, 0, r)
                                d += r
                                if (total > 0) progresso = d.toFloat() / total
                            }
                        }
                    }
                    if (total <= 0 || d >= total) tmp.renameTo(arquivoModelo)
                    else erro = "Download incompleto. Toque em baixar de novo para continuar."
                }
            } catch (e: Exception) {
                erro = "Falha no download (toque de novo para continuar): ${e.message}"
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
