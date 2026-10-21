#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_playerzin_kairo_Native_responder(JNIEnv* env, jobject, jstring prompt) {
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string r = std::string("[C++] Kairo ouviu: ") + p;
    env->ReleaseStringUTFChars(prompt, p);
    return env->NewStringUTF(r.c_str());
}
