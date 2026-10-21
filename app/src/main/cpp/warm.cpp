#include <vector>
#include <string>
#include "llama.h"

void kairo_warmup(llama_model* m) {
    const llama_vocab* v = llama_model_get_vocab(m);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 512; cp.n_batch = 512; cp.n_threads = 4; cp.n_threads_batch = 4;
    llama_context* c = llama_init_from_model(m, cp);
    if (!c) return;
    std::string s = "Olá, tudo bem? Vamos conversar um pouco para aquecer.";
    std::vector<llama_token> t(128);
    int n = llama_tokenize(v, s.c_str(), s.size(), t.data(), t.size(), true, true);
    if (n > 0) {
        llama_batch b = llama_batch_get_one(t.data(), n);
        llama_decode(c, b);
    }
    llama_free(c);
}
