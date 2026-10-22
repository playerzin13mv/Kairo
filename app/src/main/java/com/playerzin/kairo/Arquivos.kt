package com.playerzin.kairo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArqGerado(val caminho: String, val conteudo: String)

object Arquivos {
    private val re = Regex("\u0060\u0060\u0060([^\\n\u0060]*)\\n([\\s\\S]*?)(?:\u0060\u0060\u0060|\\z)")

    fun extrair(texto: String): List<ArqGerado> {
        val saida = ArrayList<ArqGerado>()
        var n = 1
        for (m in re.findAll(texto)) {
            val info = m.groupValues[1].trim()
            val corpo = m.groupValues[2].trimEnd('\n', ' ')
            if (corpo.isBlank()) continue
            val partes = info.split(' ', ':').filter { it.isNotBlank() }
            var nome = partes.lastOrNull { it.contains('.') && !it.endsWith(".") } ?: ""
            if (nome.isEmpty()) {
                nome = "arquivo" + n + "." + extensao(partes.firstOrNull { it != "arquivo" } ?: "")
            }
            saida.add(ArqGerado(limpar(nome), corpo))
            n++
        }
        return saida
    }

    private fun extensao(l: String): String = when (l.lowercase()) {
        "kotlin", "kt" -> "kt"
        "java" -> "java"
        "json" -> "json"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "python", "py" -> "py"
        "cpp", "c++" -> "cpp"
        "c" -> "c"
        "xml" -> "xml"
        "html" -> "html"
        "css" -> "css"
        "bash", "sh", "shell" -> "sh"
        "yaml", "yml" -> "yaml"
        "lua" -> "lua"
        "markdown", "md" -> "md"
        "gradle" -> "gradle"
        "mcfunction" -> "mcfunction"
        else -> "txt"
    }

    private fun limpar(n: String): String {
        val p = n.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        return if (p.isEmpty()) "arquivo.txt" else p.joinToString("/")
    }

    private fun mimeDe(nome: String): String = when (nome.substringAfterLast('.').lowercase()) {
        "json" -> "application/json"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun gravar(ctx: Context, caminho: String, bytes: ByteArray): String {
        val simples = caminho.substringAfterLast('/')
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.Downloads.DISPLAY_NAME, simples)
                v.put(MediaStore.Downloads.MIME_TYPE, mimeDe(simples))
                v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Kairo")
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                if (uri == null) {
                    "Não consegui salvar."
                } else {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    "Salvo em Download/Kairo/" + simples
                }
            } else {
                val dir = File(ctx.getExternalFilesDir(null), "Kairo")
                dir.mkdirs()
                File(dir, simples).writeBytes(bytes)
                "Salvo em " + dir.path + "/" + simples
            }
        } catch (e: Exception) {
            "Erro ao salvar: " + e.message
        }
    }

    fun salvarUm(ctx: Context, a: ArqGerado): String =
        gravar(ctx, a.caminho, a.conteudo.toByteArray(Charsets.UTF_8))

    fun salvarZip(ctx: Context, arqs: List<ArqGerado>, nomeZip: String): String {
        val bos = ByteArrayOutputStream()
        val usados = HashSet<String>()
        ZipOutputStream(bos).use { z ->
            for (a in arqs) {
                var p = a.caminho
                var k = 2
                while (!usados.add(p)) {
                    val ponto = a.caminho.lastIndexOf('.')
                    p = if (ponto > 0) a.caminho.substring(0, ponto) + "_" + k + a.caminho.substring(ponto) else a.caminho + "_" + k
                    k++
                }
                z.putNextEntry(ZipEntry(p))
                z.write(a.conteudo.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return gravar(ctx, nomeZip, bos.toByteArray())
    }
}
