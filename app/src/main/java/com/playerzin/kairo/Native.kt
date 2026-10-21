package com.playerzin.kairo

object Native {
    init { System.loadLibrary("kairo") }
    external fun responder(prompt: String): String
}
