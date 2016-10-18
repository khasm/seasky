#include "mie_TpmVerifier.h"
#include "TpmClient.h"
#include <string>

using namespace std;

JNIEXPORT jboolean JNICALL Java_mie_TpmVerifier_verify (JNIEnv *env, jobject obj, jstring addr)
{
    const char *str = env->GetStringUTFChars(addr, NULL);
    string address(str);
    return verify(address);
}