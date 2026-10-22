package com.playerzin.kairo

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import java.io.File

object Diag {
    fun instalar(ctx: Context) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val txt = android.util.Log.getStackTraceString(e).take(2500)
                val i = Intent(ctx, CrashActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                i.putExtra("t", txt)
                ctx.startActivity(i)
            } catch (x: Throwable) { }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }

    fun marcar(ctx: Context, ligado: Boolean) {
        val f = File(ctx.filesDir, "carregando.flag")
        try { if (ligado) f.writeText("1") else f.delete() } catch (x: Throwable) { }
    }

    fun marcaExiste(ctx: Context): Boolean = File(ctx.filesDir, "carregando.flag").exists()

    fun relatorio(ctx: Context): String {
        if (android.os.Build.VERSION.SDK_INT < 30) return ""
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val l = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1)
            if (l.isEmpty()) return ""
            val r = l[0]
            val nome = when (r.reason) {
                ApplicationExitInfo.REASON_CRASH -> "CRASH Java"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH NATIVO (C++)"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "MEMORIA BAIXA"
                ApplicationExitInfo.REASON_SIGNALED -> "MORTO POR SINAL"
                ApplicationExitInfo.REASON_ANR -> "TRAVOU (ANR)"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "FALHA AO INICIAR"
                else -> ""
            }
            if (nome.isEmpty()) return ""
            return "Último fechamento: $nome\nsinal/status: ${r.status}\n${r.description ?: ""}"
        } catch (x: Throwable) {
            return ""
        }
    }
}

class CrashActivity : android.app.Activity() {
    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        val txt = intent.getStringExtra("t") ?: "sem dados"
        val sv = android.widget.ScrollView(this)
        val tv = android.widget.TextView(this)
        tv.text = "A Kairo fechou por um erro.\nTire um print desta tela e mande pro Claude:\n\n" + txt
        tv.setTextIsSelectable(true)
        tv.setPadding(32, 96, 32, 32)
        tv.textSize = 12f
        sv.addView(tv)
        setContentView(sv)
    }
}
