#include <jni.h>
#include <dlfcn.h>

static JavaVM* g_jvm = nullptr;

static bool isLoaded = false;

using LoadFunc = void (*)(JavaVM *);
void LoadMod(JavaVM* vm, const char* path, int index) {
    const char* options[] = {"LeviMod_LoadBefore", "LeviMod_LoadAfter"};
    const char* type = nullptr;
    if(index !=(index <= 1)) type = options[0];
    type = options[index];
    
    if (void *handle = dlopen(path, RTLD_NOW)) {
      LoadFunc func = (LoadFunc)dlsym(handle, type);
      if (func) {
        func(vm);
      }
    }
}

extern "C" jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    isLoaded = true;
    return JNI_VERSION_1_6;
}

extern "C" {
JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_ModNativeLoader_nativeLoadMod(
        JNIEnv* env,
        jobject thiz,
        jstring libPath,
        jint jindex
) {
    if(isLoaded) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(libPath, nullptr);

    int index = (int) jindex;
    LoadMod(g_jvm, path, index);
    return JNI_TRUE;
}

};