package com.playerzin.kairo

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

@Composable
fun ModelosDialog(vm: AppVM) {
    AlertDialog(
        onDismissRequest = { vm.mostrarModelos = false },
        containerColor = Surf,
        titleContentColor = Txt,
        textContentColor = Txt,
        title = { Text("Escolher cérebro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MODELOS.forEach { m ->
                    val ativo = m.id == vm.modeloId
                    val baixado = vm.baixado(m.id)
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (ativo) Mint.copy(alpha = 0.18f) else Bg).padding(12.dp)
                    ) {
                        Text(m.nome + (if (ativo) "  (em uso)" else ""), fontWeight = FontWeight.Bold, color = Txt)
                        Text(m.desc, color = Dim, fontSize = 13.sp)
                        if (!ativo) {
                            Row(Modifier.padding(top = 6.dp)) {
                                Text(
                                    if (baixado) "Usar" else "Usar (baixar " + m.tamanho + ")",
                                    color = Mint,
                                    modifier = Modifier.clickable { vm.trocarModelo(m.id) }.padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                                )
                                if (baixado && m.id != "0.5b") {
                                    Text("Apagar", color = Err, modifier = Modifier.clickable { vm.apagarModelo(m.id) }.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.mostrarModelos = false }) { Text("Fechar", color = Mint) } }
    )
}

@Composable
fun ArquivosCard(texto: String) {
    val ctx = LocalContext.current
    val arqs = remember(texto) { Arquivos.extrair(texto) }
    if (arqs.isEmpty()) return
    val temManifest = arqs.any { it.caminho.endsWith("manifest.json") }
    val carimbo = System.currentTimeMillis() / 1000
    Column(Modifier.padding(top = 8.dp).widthIn(max = 330.dp).clip(RoundedCornerShape(14.dp)).background(Surf).padding(12.dp)) {
        Text("Arquivos gerados (" + arqs.size + ")", color = Mint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        arqs.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.caminho, color = Txt, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    "Salvar", color = Mint, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        Toast.makeText(ctx, Arquivos.salvarUm(ctx, a), Toast.LENGTH_LONG).show()
                    }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Text(
                "Salvar tudo (.zip)", color = Sky,
                modifier = Modifier.clickable {
                    Toast.makeText(ctx, Arquivos.salvarZip(ctx, arqs, "kairo-" + carimbo + ".zip"), Toast.LENGTH_LONG).show()
                }.padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
            )
            if (temManifest) {
                Text(
                    "Salvar .mcaddon", color = Sky,
                    modifier = Modifier.clickable {
                        Toast.makeText(ctx, Arquivos.salvarZip(ctx, arqs, "kairo-" + carimbo + ".mcaddon"), Toast.LENGTH_LONG).show()
                    }.padding(vertical = 4.dp)
                )
            }
        }
    }
}
