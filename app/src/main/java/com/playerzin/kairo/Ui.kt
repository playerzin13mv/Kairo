package com.playerzin.kairo

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch
import java.util.Calendar

val Bg = Color(0xFF15171C)
val Surf = Color(0xFF1F232B)
val Mint = Color(0xFF7FCFB6)
val Sky = Color(0xFF8FB8E8)
val Txt = Color(0xFFE4E1D8)
val Dim = Color(0xFF9BA1AC)
val OnAccent = Color(0xFF10211C)
val Err = Color(0xFFE59A9A)
val Grad = Brush.linearGradient(listOf(Mint, Sky))

val Fonte = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold)
)

fun gradStyle(tam: TextUnit, negrito: Boolean = true) = TextStyle(
    brush = Grad, fontSize = tam, fontFamily = Fonte,
    fontWeight = if (negrito) FontWeight.Bold else FontWeight.Normal
)

@Composable
fun KairoTheme(content: @Composable () -> Unit) {
    val b = Typography()
    val tipo = Typography(
        bodyLarge = b.bodyLarge.copy(fontFamily = Fonte),
        bodyMedium = b.bodyMedium.copy(fontFamily = Fonte),
        bodySmall = b.bodySmall.copy(fontFamily = Fonte),
        labelLarge = b.labelLarge.copy(fontFamily = Fonte),
        labelMedium = b.labelMedium.copy(fontFamily = Fonte),
        titleMedium = b.titleMedium.copy(fontFamily = Fonte),
        titleLarge = b.titleLarge.copy(fontFamily = Fonte)
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint, secondary = Sky, background = Bg, surface = Surf,
            onBackground = Txt, onSurface = Txt, onPrimary = OnAccent
        ),
        typography = tipo
    ) {
        ProvideTextStyle(TextStyle(fontFamily = Fonte)) { content() }
    }
}

fun saudacao(nome: String): Pair<String, String> {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val par = when (h) {
        in 5..11 -> listOf(
            "Bom dia, $nome! ☀️", "Oi, $nome! 👋", "E aí, $nome! 😄", "Opa, $nome! Bom dia!"
        ) to listOf(
            "Café na mão e ideias na cabeça?", "O que vamos criar hoje?",
            "Dia bom pra começar um projeto novo!", "Em que posso te ajudar?"
        )
        in 12..17 -> listOf(
            "Boa tarde, $nome! 🌤️", "Fala, $nome! ✨", "Oi, $nome! 👋", "Salve, $nome! 🎮"
        ) to listOf(
            "Bora tirar uma ideia do papel?", "Como está sendo o seu dia?",
            "Pronta pra qualquer ideia sua!", "O que vamos fazer agora?"
        )
        in 18..23 -> listOf(
            "Boa noite, $nome! 🌙", "Oi, $nome! 👋", "E aí, $nome! 😎", "Fala, $nome! ✨"
        ) to listOf(
            "Hora perfeita pra criar com calma.", "Como foi o seu dia?",
            "Vamos fechar o dia com uma boa ideia?", "No que vamos trabalhar?"
        )
        else -> listOf(
            "Ainda acordado, $nome? 🌙", "Olá, coruja! 🦉", "Oi, $nome! 😴"
        ) to listOf(
            "A madrugada rende boas ideias!", "Não esquece de descansar, viu?",
            "Estou aqui se precisar de companhia."
        )
    }
    return par.first.random() to par.second.random()
}

val SUGESTOES = listOf(
    "Me conta uma curiosidade", "Ideias de vídeo pro YouTube", "Escreve uma piada curta",
    "Explica o que é um port de jogo", "Me dá um nome pra um canal", "Como organizo meu dia?",
    "Resume o que é uma IA", "Inventa uma história curta"
)

suspend fun entrarComGoogle(ctx: Context): String? {
    val opt = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(WEB_CLIENT_ID)
        .build()
    val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
    val r = CredentialManager.create(ctx).getCredential(ctx, req)
    val c = r.credential
    if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val g = GoogleIdTokenCredential.createFrom(c.data)
        return g.givenName ?: g.displayName ?: "amigo"
    }
    return null
}

@Composable
fun App(vm: AppVM) {
    if (vm.nome == null) LoginScreen(vm) else Principal(vm)
}

@Composable
fun LoginScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nome by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✦", style = gradStyle(72.sp))
        Text("Kairo", style = gradStyle(44.sp))
        Text("Sua IA, direto no seu celular", color = Dim)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = {
                if (WEB_CLIENT_ID.startsWith("COLE_AQUI")) {
                    erro = "O login do Google ainda não foi configurado. Entre com seu nome por enquanto."
                } else scope.launch {
                    carregando = true; erro = ""
                    try {
                        val n = entrarComGoogle(ctx)
                        if (n != null) vm.entrar(n) else erro = "Não consegui entrar."
                    } catch (e: Exception) {
                        erro = "Login Google falhou: ${e.message}"
                    }
                    carregando = false
                }
            },
            enabled = !carregando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) { Text(if (carregando) "Entrando..." else "Entrar com Google", fontSize = 16.sp) }
        if (erro.isNotEmpty()) Text(erro, color = Err, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("ou", color = Dim)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nome, onValueChange = { nome = it }, singleLine = true,
            placeholder = { Text("Digite seu nome", color = Dim) },
            shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mint, unfocusedBorderColor = Surf,
                focusedContainerColor = Surf, unfocusedContainerColor = Surf,
                focusedTextColor = Txt, unfocusedTextColor = Txt, cursorColor = Mint
            )
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { if (nome.isNotBlank()) vm.entrar(nome.trim()) },
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(26.dp)
        ) { Text("Entrar", color = Mint) }
    }
}

@Composable
fun Principal(vm: AppVM) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Surf) {
                Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                    Text("✦ Kairo", style = gradStyle(28.sp))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.novaConversa(); scope.launch { drawer.close() } },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)
                    ) { Text("＋  Nova conversa") }
                    Spacer(Modifier.height(14.dp))
                    Text("Conversas", color = Dim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(vm.conversas, key = { it.id }) { c ->
                            val sel = c.id == vm.atualId
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Mint.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { vm.abrir(c.id); scope.launch { drawer.close() } }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(c.titulo, color = Txt, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("🗑", modifier = Modifier.clickable { vm.apagar(c.id) }.padding(start = 10.dp))
                            }
                        }
                    }
                    Text("Olá, ${vm.nome}", color = Txt, fontWeight = FontWeight.Bold)
                    Text("Versão 0.4 · cérebro 0.5B", color = Dim, fontSize = 12.sp)
                    TextButton(onClick = { vm.sair() }) { Text("Sair da conta", color = Dim) }
                }
            }
        }
    ) { Chat(vm) { scope.launch { drawer.open() } } }
}

@Composable
fun Chat(vm: AppVM, abrirMenu: () -> Unit) {
    val conv = vm.atual()
    val msgs = conv?.msgs
    var texto by remember { mutableStateOf("") }
    val lista = rememberLazyListState()
    LaunchedEffect(msgs?.size, msgs?.lastOrNull()?.text?.length) {
        val n = msgs?.size ?: 0
        if (n > 0) lista.scrollToItem(n - 1, 100000)
    }
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☰", fontSize = 26.sp, color = Txt, modifier = Modifier.clip(CircleShape).clickable { abrirMenu() }.padding(12.dp))
            Text("Kairo", style = gradStyle(22.sp))
            Spacer(Modifier.weight(1f))
            Text("＋", fontSize = 26.sp, color = Mint, modifier = Modifier.clip(CircleShape).clickable { vm.novaConversa() }.padding(12.dp))
        }
        if (vm.diag.isNotEmpty()) DiagCard(vm)
        if (!vm.modeloPronto) ModeloCard(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isNullOrEmpty()) Hero(vm)
            else LazyColumn(state = lista, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs) { m -> Bolha(m, vm.gerando && m === msgs.last()) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Pergunte algo à Kairo…", color = Dim) },
                shape = RoundedCornerShape(24.dp), maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mint, unfocusedBorderColor = Surf,
                    focusedContainerColor = Surf, unfocusedContainerColor = Surf,
                    focusedTextColor = Txt, unfocusedTextColor = Txt, cursorColor = Mint
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Grad).clickable {
                    if (vm.gerando) vm.parar()
                    else if (texto.isNotBlank()) { vm.enviar(texto.trim()); texto = "" }
                },
                contentAlignment = Alignment.Center
            ) { Text(if (vm.gerando) "■" else "➤", color = OnAccent, fontSize = 20.sp) }
        }
    }
}

@Composable
fun Hero(vm: AppVM) {
    val sd = remember(vm.atualId) { saudacao(vm.nome ?: "") }
    val sugestoes = remember(vm.atualId) { SUGESTOES.shuffled().take(4) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✦", style = gradStyle(56.sp))
        Text(sd.first, style = gradStyle(32.sp), textAlign = TextAlign.Center)
        Text(sd.second, color = Dim, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        sugestoes.forEach { s ->
            Box(
                Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Surf)
                    .clickable { vm.enviar(s) }.padding(horizontal = 18.dp, vertical = 12.dp)
            ) { Text(s, color = Txt, fontSize = 15.sp) }
        }
    }
}

@Composable
fun Bolha(m: Msg, gerandoEsta: Boolean) {
    val user = m.role == "user"
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Box(
            Modifier.widthIn(max = 330.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, if (user) 18.dp else 4.dp, if (user) 4.dp else 18.dp))
                .then(if (user) Modifier.background(Grad) else Modifier.background(Surf))
                .padding(14.dp, 10.dp)
        ) {
            if (m.text.isEmpty()) Text("Pensando…", color = Dim, fontSize = 16.sp)
            else SelectionContainer {
                Text(m.text, color = if (user) OnAccent else Txt, fontSize = 17.sp, lineHeight = 24.sp)
            }
        }
        if (!user && m.text.isNotEmpty() && !gerandoEsta) {
            Text(
                "Copiar", color = Dim, fontSize = 13.sp,
                modifier = Modifier.clickable { clip.setText(AnnotatedString(m.text)) }.padding(8.dp)
            )
        }
    }
}

@Composable
fun ModeloCard(vm: AppVM) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Surf).padding(16.dp)) {
        if (vm.progresso >= 0f) {
            Text("${vm.status}… ${(vm.progresso * 100).toInt()}%", color = Txt)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { vm.progresso }, modifier = Modifier.fillMaxWidth(), color = Mint, trackColor = Bg)
        } else if (vm.precisaAcordar) {
            Text("Da última vez o app fechou ao acordar a Kairo. Toque para tentar de novo.", color = Txt)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.carregarModelo(true) }, shape = RoundedCornerShape(14.dp)) { Text("Acordar a Kairo") }
        } else if (vm.carregandoModelo) {
            Text("Acordando a Kairo… (na primeira vez demora um pouquinho)", color = Txt)
        } else {
            Text("Pra conversar eu preciso do meu cérebro (Qwen2.5 0.5B, cerca de 400 MB). Use Wi-Fi!", color = Txt, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.baixarModelo() }, shape = RoundedCornerShape(14.dp)) { Text("Baixar / continuar") }
            if (vm.erro.isNotEmpty()) Text(vm.erro, color = Err, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun DiagCard(vm: AppVM) {
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Surf).padding(14.dp)) {
        Text("Relatório do último fechamento", color = Err, fontWeight = FontWeight.Bold)
        SelectionContainer { Text(vm.diag, color = Txt, fontSize = 13.sp) }
        Row {
            Text("Copiar", color = Mint, modifier = Modifier.clickable { clip.setText(AnnotatedString(vm.diag)) }.padding(8.dp))
            Text("Fechar", color = Dim, modifier = Modifier.clickable { vm.diag = "" }.padding(8.dp))
        }
    }
}
