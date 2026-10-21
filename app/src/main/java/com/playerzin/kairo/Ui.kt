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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import kotlinx.coroutines.launch

const val WEB_CLIENT_ID = "COLE_AQUI_O_CLIENT_ID_WEB"

val Bg = Color(0xFF0B0B1A)
val Surf = Color(0xFF16162E)
val Purple = Color(0xFF7C4DFF)
val Cyan = Color(0xFF00E5FF)
val Txt = Color(0xFFE8E8FF)
val Dim = Color(0xFF8E8EB8)
val Grad = Brush.linearGradient(listOf(Purple, Cyan))

@Composable
fun KairoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple, secondary = Cyan, background = Bg, surface = Surf,
            onBackground = Txt, onSurface = Txt, onPrimary = Color.White
        ),
        content = content
    )
}

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
        Text("✦", style = TextStyle(brush = Grad, fontSize = 72.sp))
        Text("Kairo", style = TextStyle(brush = Grad, fontSize = 44.sp, fontWeight = FontWeight.Bold))
        Text("Sua IA, direto no seu celular", color = Dim)
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = {
                scope.launch {
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
        if (erro.isNotEmpty()) Text(erro, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Text("ou", color = Dim)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nome, onValueChange = { nome = it }, singleLine = true,
            placeholder = { Text("Digite seu nome", color = Dim) },
            shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Purple, unfocusedBorderColor = Surf,
                focusedContainerColor = Surf, unfocusedContainerColor = Surf,
                focusedTextColor = Txt, unfocusedTextColor = Txt, cursorColor = Cyan
            )
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { if (nome.isNotBlank()) vm.entrar(nome.trim()) },
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(26.dp)
        ) { Text("Entrar", color = Cyan) }
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
                    Text("✦ Kairo", style = TextStyle(brush = Grad, fontSize = 28.sp, fontWeight = FontWeight.Bold))
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
                                    .background(if (sel) Purple.copy(alpha = 0.25f) else Color.Transparent)
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
            Text("Kairo", style = TextStyle(brush = Grad, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Text("＋", fontSize = 26.sp, color = Cyan, modifier = Modifier.clip(CircleShape).clickable { vm.novaConversa() }.padding(12.dp))
        }
        if (!vm.modeloPronto) ModeloCard(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isNullOrEmpty()) Hero(vm)
            else LazyColumn(state = lista, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(msgs) { m -> Bolha(m) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = texto, onValueChange = { texto = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Pergunte algo à Kairo…", color = Dim) },
                shape = RoundedCornerShape(24.dp), maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple, unfocusedBorderColor = Surf,
                    focusedContainerColor = Surf, unfocusedContainerColor = Surf,
                    focusedTextColor = Txt, unfocusedTextColor = Txt, cursorColor = Cyan
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Grad).clickable {
                    if (vm.gerando) vm.parar()
                    else if (texto.isNotBlank()) { vm.enviar(texto.trim()); texto = "" }
                },
                contentAlignment = Alignment.Center
            ) { Text(if (vm.gerando) "■" else "➤", color = Color.White, fontSize = 20.sp) }
        }
    }
}

@Composable
fun Hero(vm: AppVM) {
    val saud = remember(vm.atualId) {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val p = if (h in 5..11) "Bom dia" else if (h in 12..17) "Boa tarde" else "Boa noite"
        p + "! " + listOf(
            "Em que posso te ajudar hoje?", "O que vamos criar hoje?",
            "Bora fazer algo legal?", "Pronta pra qualquer ideia sua!"
        ).random()
    }
    val sugestoes = listOf("Me conta uma curiosidade", "Ideias de vídeo pro YouTube", "Escreve uma piada curta", "Explica o que é um port de jogo")
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✦", style = TextStyle(brush = Grad, fontSize = 56.sp))
        Text("Oi, ${vm.nome}! 👋", style = TextStyle(brush = Grad, fontSize = 34.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Text(saud, color = Dim, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        sugestoes.forEach { s ->
            Box(
                Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Surf)
                    .clickable { vm.enviar(s) }.padding(horizontal = 18.dp, vertical = 12.dp)
            ) { Text(s, color = Txt, fontSize = 14.sp) }
        }
    }
}

@Composable
fun Bolha(m: Msg) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, if (user) 18.dp else 4.dp, if (user) 4.dp else 18.dp))
                .then(if (user) Modifier.background(Grad) else Modifier.background(Surf))
                .padding(14.dp, 10.dp)
        ) {
            SelectionContainer { Text(if (m.text.isEmpty()) "…" else m.text, color = Color.White, fontSize = 16.sp) }
        }
    }
}

@Composable
fun ModeloCard(vm: AppVM) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Surf).padding(16.dp)) {
        if (vm.carregandoModelo) {
            Text("Acordando a Kairo…", color = Txt)
        } else if (vm.progresso >= 0f) {
            Text("Baixando o cérebro da Kairo… ${(vm.progresso * 100).toInt()}%", color = Txt)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { vm.progresso }, modifier = Modifier.fillMaxWidth(), color = Cyan, trackColor = Bg)
        } else {
            Text("Pra conversar eu preciso baixar meu cérebro (Qwen2.5 1.5B, cerca de 1,1 GB, só uma vez). Use Wi-Fi!", color = Txt, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.baixarModelo() }, shape = RoundedCornerShape(14.dp)) { Text("Baixar agora") }
            if (vm.erro.isNotEmpty()) Text(vm.erro, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
