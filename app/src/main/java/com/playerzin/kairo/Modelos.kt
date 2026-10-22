package com.playerzin.kairo

class ModeloInfo(val id: String, val nome: String, val desc: String, val url: String, val tamanho: String)

val MODELOS = listOf(
    ModeloInfo(
        "0.5b", "Rápido (Qwen 0.5B)",
        "O mais veloz, respostas simples. Já vem dentro do app.",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        "~400 MB"
    ),
    ModeloInfo(
        "1.5b", "Esperto (Qwen 1.5B)",
        "Bem mais inteligente, porém mais lento.",
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "~1,1 GB"
    ),
    ModeloInfo(
        "coder1.5b", "Programador (Qwen Coder 1.5B)",
        "Feito para código e arquivos (json, zip, addons).",
        "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        "~1,1 GB"
    )
)
