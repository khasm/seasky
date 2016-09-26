#include "mie_crypto_PorterStemmer.h"
#include "PorterStemmer.c"

JNIEXPORT jint JNICALL Java_mie_crypto_PorterStemmer_stem
  (JNIEnv * env, jobject obj, jbyteArray array, jint i, jint j){
    jboolean isCopy;
    jbyte* jpointer = (*env)->GetByteArrayElements(env, array, &isCopy);
    char* b = (char*)jpointer;
    int ret = stem(b, i, j);
    return ret;
}


