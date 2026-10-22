#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <algorithm>
#include "llama.h"
void kairo_warmup(llama_model*);

static llama_model* g_model = nullptr;
static std::atomic<bool> g_stop{false};
static bool g_init = false;

static size_t utf8_ok(const std::string& s) {
    size_t n = s.size();
    for (size_t back = 1; back <= 3 && back <= n; back++) {
        unsigned char c = (unsigned char)s[n - back];
        if ((c & 0xC0) == 0x80) continue;
        size_t need = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
        if (need > back) return n - back;
        return n;
    }
    return n;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_playerzin_kairo_Native_carregar(JNIEnv* env, jobject, jstring path) {
    if (!g_init) { llama_backend_init(); g_init = true; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    const char* p = env->GetStringUTFChars(path, nullptr);
    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(p, mp);
    env->ReleaseStringUTFChars(path, p);
    return g_model != nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_playerzin_kairo_Native_parar(JNIEnv*, jobject) { g_stop = true; }

extern "C" JNIEXPORT jstring JNICALL
Java_com_playerzin_kairo_Native_gerar(JNIEnv* env, jobject, jobjectArray roles, jobjectArray conts, jobject cb) {
    if (!g_model) return env->NewStringUTF("[sem modelo]");
    g_stop = false;
    jclass cls = env->GetObjectClass(cb);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");

    int n = env->GetArrayLength(roles);
    std::vector<std::string> rs(n), cs(n);
    for (int i = 0; i < n; i++) {
        jstring a = (jstring)env->GetObjectArrayElement(roles, i);
        const char* ca = env->GetStringUTFChars(a, nullptr); rs[i] = ca;
        env->ReleaseStringUTFChars(a, ca); env->DeleteLocalRef(a);
        jstring b = (jstring)env->GetObjectArrayElement(conts, i);
        const char* cb2 = env->GetStringUTFChars(b, nullptr); cs[i] = cb2;
        env->ReleaseStringUTFChars(b, cb2); env->DeleteLocalRef(b);
    }
    std::vector<llama_chat_message> msgs(n);
    for (int i = 0; i < n; i++) { msgs[i].role = rs[i].c_str(); msgs[i].content = cs[i].c_str(); }

    const char* tmpl = llama_model_chat_template(g_model, nullptr);
    if (!tmpl) tmpl = "chatml";
    std::vector<char> buf(65536);
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), buf.size());
    if (len > (int)buf.size()) {
        buf.resize(len);
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), buf.size());
    }
    if (len < 0) return env->NewStringUTF("[erro no template]");
    std::string prompt(buf.data(), len);

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int nt = -llama_tokenize(vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> toks(nt);
    llama_tokenize(vocab, prompt.c_str(), prompt.size(), toks.data(), toks.size(), true, true);

    const int n_ctx = 4096;
    if (nt > n_ctx - 128) return env->NewStringUTF("\n\n[A conversa ficou longa demais. Comece uma nova conversa.]");

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx; cp.n_batch = 512; cp.n_threads = 4; cp.n_threads_batch = 4;
    llama_context* ctx = llama_init_from_model(g_model, cp);
    if (!ctx) return env->NewStringUTF("[sem memoria para o contexto]");

    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    bool ok = true;
    int pos = 0;
    while (pos < nt && !g_stop) {
        int c = std::min(512, nt - pos);
        llama_batch b = llama_batch_get_one(toks.data() + pos, c);
        if (llama_decode(ctx, b)) { ok = false; break; }
        pos += c;
    }

    std::string pending;
    int gen = 0;
    while (ok && !g_stop && gen < 2048 && (nt + gen) < n_ctx - 1) {
        llama_token tok = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        char piece[256];
        int pl = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, true);
        if (pl > 0) pending.append(piece, pl);
        size_t k = utf8_ok(pending);
        if (k > 0) {
            std::string out = pending.substr(0, k);
            pending.erase(0, k);
            jstring js = env->NewStringUTF(out.c_str());
            env->CallVoidMethod(cb, mid, js);
            env->DeleteLocalRef(js);
        }
        llama_batch nb = llama_batch_get_one(&tok, 1);
        if (llama_decode(ctx, nb)) break;
        gen++;
    }
    llama_sampler_free(smpl);
    llama_free(ctx);
    return env->NewStringUTF(ok ? "" : "[erro ao processar]");
}
