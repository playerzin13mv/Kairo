package com.playerzin.kairo

fun interface TokenCallback { fun onToken(t: String) }

object Native {
    init { System.loadLibrary("kairo") }
    external fun carregar(path: String): Boolean
    external fun gerar(roles: Array<String>, conts: Array<String>, cb: TokenCallback): String
    external fun parar()
}
