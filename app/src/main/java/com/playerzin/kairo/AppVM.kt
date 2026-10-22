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

val SISTEMA: String = "Você é a Kairo, uma assistente de IA que roda direto no celular. " +
    "Responda sempre em português do Brasil, respondendo exatamente ao que a pessoa escreveu. " +
    "Se ela fizer uma pergunta, responda de verdade. Nunca repita sempre a mesma saudação. " +
    "Quando a pessoa pedir código, arquivos, json, zip ou addon, escreva cada arquivo em um bloco de código " +
    "cuja primeira linha é três crases, a palavra arquivo, dois pontos e o caminho do arquivo, por exemplo: " +
    "\u0060\u0060\u0060arquivo:BP/manifest.json (depois vem o conteúdo e o fim do bloco). " +
    "Use um bloco por arquivo e explique pouco."

class Msg(val role: String, texto: String) { var text by mutableStateOf(texto) }
class Conversa(val id: String, titulo: String) {
    var titulo by mutableStateOf(titulo)
    val msgs = mutableStateListOf<Msg>()
}

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kairo", 0)

    var nome by mutableStateOf<String?>(prefs.getString("nome", null))
    var modeloId by mutableStateOf(prefs.getString("modelo", "0.5b") ?: "0.5b")
    val conversas = mutableStateListOf<Conversa>()
    var atualId by mutableStateOf<String?>(null)
    var modeloPronto by mutableStateOf(false)
    var carregandoModelo by mutableStateOf(false)
    var progresso by mutableStateOf(-1f)
    var status by mutableStateOf("")
    var gerando by mutableStateOf(false)
    var erro by mutableStateOf("")
    var precisaAcordar by mutableStateOf(false)
    var diag by mutableStateOf("")
    var apagarId by mutableStateOf<String?>(null)
    var mostrarModelos by mutableStateOf(false)
    var versaoModelos by mutableStateOf(0)

    init {
        diag = Diag.relatorio(app)
        File(app.filesDir, "model.gguf").delete()
        File(app.getExternalFilesDir(null) ?: app.filesDir, "model.gguf").delete()
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

    fun arquivoDe(id: String) = File(app.filesDir, "kairo-" + id + ".gguf")
    private fun infoAtual() = MODELOS.firstOrNull { it.id == modeloId } ?: MODELOS[0]
    fun modeloNome(): String = infoAtual().nome
    fun modeloTam(): String = infoAtual().tamanho

    fun baixado(id: String): Boolean {
        val v = versaoModelos
        return v >= 0 && arquivoDe(id).exists()
    }

    fun trocarModelo(id: String) {
        if (gerando || progresso >= 0f || carregandoModelo || id == modeloId) return
        modeloId = id
        prefs.edit().putString("modelo", id).apply()
        modeloPronto = false
        precisaAcordar = false
        erro = ""
        Diag.marcar(app, false)
        prepararModelo()
    }

    fun apagarModelo(id: String) {
        if (id == modeloId) return
        arquivoDe(id).delete()
        File(arquivoDe(id).path + ".part").delete()
        versaoModelos++
    }

    private fun temAsset(): Boolean = try {
        app.assets.list("")?.contains("model.gguf") == true
    } catch (e: Exception) { false }

    fun prepararModelo() {
        val arq = arquivoDe(modeloId)
        if (arq.exists()) { carregarModelo(); return }
        if (modeloId != "0.5b" || !temAsset()) return
        status = "Preparando o cérebro (só na 1ª vez)"
        progresso = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(arq.path + ".part")
                val total = try { app.assets.openFd("model.gguf").use { it.length } } catch (e: Exception) { -1L }
                app.assets.open("model.gguf").use { i ->
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
                tmp.renameTo(arq)
            } catch (e: Exception) {
                erro = "Falha ao preparar: " + e.message
            }
            progresso = -1f
            versaoModelos++
            carregarModelo()
        }
    }

    fun carregarModelo(manual: Boolean = false) {
        val arq = arquivoDe(modeloId)
        if (!arq.exists() || carregandoModelo) return
        if (!manual && Diag.marcaExiste(app)) {
            precisaAcordar = true
            erro = "Da última vez o app fechou ao acordar a Kairo."
            return
        }
        precisaAcordar = false
        carregandoModelo = true
        viewModelScope.launch(Dispatchers.IO) {
            Diag.marcar(app, true)
            val ok = Native.carregar(arq.absolutePath)
            Diag.marcar(app, false)
            modeloPronto = ok
            carregandoModelo = false
            if (!ok) erro = "Não consegui carregar o modelo."
        }
    }

    fun baixarModelo() {
        if (progresso >= 0f) return
        val id = modeloId
        val info = infoAtual()
        val arq = arquivoDe(id)
        erro = ""
        status = "Baixando o cérebro"
        progresso = 0f
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tmp = File(arq.path + ".part")
                val ja = if (tmp.exists()) tmp.length() else 0L
                val c = URL(info.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                if (ja > 0) c.setRequestProperty("Range", "bytes=" + ja + "-")
                val code = c.responseCode
                if (code == 416) {
                    tmp.renameTo(arq)
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
                    if (total <= 0 || d >= total) tmp.renameTo(arq)
                    else erro = "Download incompleto. Toque em baixar de novo para continuar."
                }
            } catch (e: Exception) {
                erro = "Falha no download (toque de novo para continuar): " + e.message
            }
            progresso = -1f
            versaoModelos++
            if (id == modeloId) carregarModelo()
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
