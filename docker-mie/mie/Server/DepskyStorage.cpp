#include "DepskyStorage.h"
#include "ServerUtil.h"
#include <memory>

namespace MIE::DEPSKY{

using namespace std;

static const char *depskyLibraries = "depsky.jar:lib/AmazonDriver.jar:lib/aws-java-sdk-1.11.18.jar:lib/commons-codec-1.9.jar:lib/commons-io-1.4.jar:lib/commons-logging-1.2.jar:lib/DepSkyDependencies.jar:lib/GoogleStorageDriver.jar:lib/httpclient-4.5.2.jar:lib/httpcore-4.4.4.jar:lib/jackson-annotations-2.6.0.jar:lib/jackson-core-2.6.6.jar:lib/jackson-databind-2.6.4.jar:lib/joda-time-2.2.jar:lib/JReedSolEC.jar:lib/microsoft-windowsazure-api-0.4.6.jar:lib/PVSS.jar:lib/RackSpaceDriver.jar:lib/WindowsAzureDriver.jar:lib/jets3t/jackson-core-asl-1.8.1.jar:lib/jets3t/jackson-mapper-asl-1.8.1.jar:lib/jets3t/java-xmlbuilder-0.4.jar:lib/jets3t/jets3t-0.9.1.jar:lib/jets3t/servlet-api";

DepskyStorage::DepskyStorage(int model, int cid, const vector<string>& ips): id(cid)
{
    ///start jvm
    JavaVMInitArgs vm_args;
    unique_ptr<JavaVMOption[]> options(new JavaVMOption[3/*5*/]);
    size_t len = strlen(depskyLibraries)+strlen("-Djava.class.path=")+1;
    unique_ptr<char[]> opLibraries(new char[len]);
    sprintf(opLibraries.get(), "-Djava.class.path=%s", depskyLibraries);
    options[0].optionString = opLibraries.get();
    char options1[7], options2[7]/*, options3[8], options4[54]*/;
    sprintf(options1, "%s", "-Xms3g");
    sprintf(options2, "%s", "-Xmx3g");
    /*sprintf(options3, "%s", "-Xdebug");
    sprintf(options4, "%s", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n");*/
    options[1].optionString = options1;
    options[2].optionString = options2;
    /*options[3].optionString = options3;
    options[4].optionString = options4;*/
    vm_args.version = JNI_VERSION_1_6;             
    vm_args.nOptions = 3/*5*/;
    vm_args.options = options.get();

    jint res = JNI_CreateJavaVM(&jvm, (void**)&mainEnv, &vm_args);
    if (res != JNI_OK) {
        fatal("DEPSKY: Couldn't create JVM");
    }
    ///initialize depsky client
    jclass dscClassTemp = mainEnv->FindClass("depskys/core/LocalDepSkySClient");
    if(dscClassTemp == NULL){
        fatal("DEPSKY: Java class LocalDepSkySClient not found.");
    }
    dscClass = (jclass)mainEnv->NewGlobalRef(dscClassTemp);
    jmethodID constructor = mainEnv->GetMethodID(dscClass, "<init>", "(II[Ljava/lang/String;)V");
    bool fallback = false;
    if(constructor == NULL){
        constructor = mainEnv->GetMethodID(dscClass, "<init>", "(IZ)V");
        if(constructor == NULL){
          fatal("DEPSKY: LocalDepSkySClient constructor not found");
        }
        else{
          fallback = true;
          warning("DEPSKY: Using fallback LocalDepSkySClient constructor");
        }
    }
    //use true for remote storage, false for local drivers (local drivers requires the
    //depsky server running)
    if(fallback){
        bool local;
        if(model == 0){
            local = false;
        }
        else{
            local = true;
        }
        jobject dsClientTemp = mainEnv->NewObject(dscClass, constructor, id, local);
        if(dsClientTemp == NULL){
            fatal("DEPSKY: Error instantiating LocalDepSkySClient with fallback constructor");
        }
        dsClient = mainEnv->NewGlobalRef(dsClientTemp);
    }
    else{
        jclass java_string = mainEnv->FindClass("java/lang/String");
        if(NULL == java_string){
            fatal("DEPSKY: Java class String not found");
        }
        jobjectArray java_ips;
        if(ips.empty()){
            java_ips = mainEnv->NewObjectArray(0, java_string, NULL);
        }
        else{
            java_ips = mainEnv->NewObjectArray(ips.size(), java_string, NULL);
            for(size_t i = 0; i < ips.size(); i++){
                jstring j_ip = mainEnv->NewStringUTF(ips[i].c_str());
                mainEnv->SetObjectArrayElement(java_ips, i, j_ip);
            }
        }
        jobject dsClientTemp = mainEnv->NewObject(dscClass, constructor, id, model, java_ips);
        if(dsClientTemp == NULL){
          fatal("DEPSKY: Error instantiating LocalDepSkySClient");
        }
        dsClient = mainEnv->NewGlobalRef(dsClientTemp);
    }
    /*****optimizations to reduce amount of calls to jni*****/
    jclass dsDataUnitClassTemp = mainEnv->FindClass("depskys/core/DepSkySDataUnit");
    if(dsDataUnitClassTemp == NULL){
        fatal("DEPSKY: Java class DepSkySDataUnit not found");
    }
    dsDataUnitClass = (jclass)mainEnv->NewGlobalRef(dsDataUnitClassTemp);

    dataUnitConstructor = mainEnv->GetMethodID(dsDataUnitClass, "<init>", "(Ljava/lang/String;)V");
    if(constructor == NULL){
        fatal("DEPSKY: DepSkySDataUnit constructor not found");
    }

    writeID = mainEnv->GetMethodID(dscClass, "write", "(Ldepskys/core/DepSkySDataUnit;[B)[B");
    if(writeID == NULL){
        fatal("DepSky: LocalDepSkySClient write method not found");
    }

    readID = mainEnv->GetMethodID(dscClass, "read", "(Ldepskys/core/DepSkySDataUnit;)[B");
    if(readID == NULL){
        fatal("DepSky: LocalDepSkySClient read method not found");
    }

    removeID = mainEnv->GetMethodID(dscClass, "deleteContainer", "(Ldepskys/core/DepSkySDataUnit;)V");
    if(removeID == NULL){
        fatal("DepSky: LocalDepSkySClient remove method not found");
    }

    setModeID = mainEnv->GetMethodID(dsDataUnitClass, "setUsingErsCodes", "(Z)V");
    if(setModeID == NULL){
        fatal("DepSky: DepSkySDataUnit setUsingErsCodes method not found");
    }

    netUpID = mainEnv->GetMethodID(dscClass, "getNetworkUploadTime", "()J");
    if(netUpID == NULL){
        error("DepSky: LocalDepSkySClient getNetworkUploadTime method not found");
    }

    netDownID = mainEnv->GetMethodID(dscClass, "getNetworkDownloadTime", "()J");
    if(netDownID == NULL){
        error("DepSky: LocalDepSkySClient getNetworkDownloadTime method not found");
    }
    resetTimesID = mainEnv->GetMethodID(dscClass, "resetTimes", "()V");
    if(netDownID == NULL){
        error("DepSky: LocalDepSkySClient resetTimes method not found");
    }
    /*******************************************************/
    mainEnv->DeleteLocalRef(dscClassTemp);
    threadEnv[this_thread::get_id()] = mainEnv;
}

DepskyStorage::~DepskyStorage(){
    if(jvm != NULL){
        mainEnv->DeleteGlobalRef(dsClient);
        mainEnv->DeleteGlobalRef(dsDataUnitClass);
        mainEnv->DeleteGlobalRef(dscClass);
        jvm->DestroyJavaVM();
    }
}

JNIEnv* DepskyStorage::attachThread()
{
    unique_lock<mutex> tmp(lock);
    JNIEnv* env = threadEnv[this_thread::get_id()];
    if(env == NULL){
        JavaVMAttachArgs args;
        args.version = JNI_VERSION_1_6;
        args.name = NULL;
        args.group = NULL;
        jvm->AttachCurrentThread((void**)&env, &args);
        threadEnv[this_thread::get_id()] = env;
    }
    return env;
}

void DepskyStorage::detachThread()
{
    jvm->DetachCurrentThread();
}

int DepskyStorage::read(const string& name, vector<char>& buffer){
    JNIEnv* tEnv = attachThread();
    ///create date unit
    jstring jName = tEnv->NewStringUTF(name.c_str());
    jobject dsDataUnit = tEnv->NewObject(dsDataUnitClass, dataUnitConstructor, jName);
    if(dsDataUnit == NULL){
        error("DEPSKY::Read: Couldn't create DepSkySDataUnit object");
        return DEPSKY_ERROR;
    }
    ///set replication mode
    tEnv->CallVoidMethod(dsDataUnit, setModeID, true);
    ///call method
    jbyteArray jData = (jbyteArray)tEnv->CallObjectMethod(dsClient, readID, dsDataUnit);
    tEnv->DeleteLocalRef(jName);
    tEnv->DeleteLocalRef(dsDataUnit);
    if(jData != NULL){
        size_t dataSize = tEnv->GetArrayLength(jData);
        buffer.resize(dataSize);
        tEnv->GetByteArrayRegion(jData, 0, dataSize, (signed char*)(buffer.data()));
        tEnv->DeleteLocalRef(jData);
        return dataSize;
    }
    else{
        return NOT_FOUND;
    }
}

bool DepskyStorage::write(const string& name, const char* buffer, unsigned buffer_size){
    JNIEnv* tEnv = attachThread();
    ///create dataunit
    jstring jName = tEnv->NewStringUTF(name.c_str());
    jobject dsDataUnit = tEnv->NewObject(dsDataUnitClass, dataUnitConstructor, jName);
    if(dsDataUnit == NULL){
        error("DEPSKY::Write: Couldn't create DepSkySDataUnit object");
        return OP_FAILED;
    }
    ///set replication mode
    tEnv->CallVoidMethod(dsDataUnit, setModeID, true);
    ///call method
    jbyteArray bArray = tEnv->NewByteArray(buffer_size);
    tEnv->SetByteArrayRegion(bArray, 0, buffer_size, (jbyte*)buffer);
    jbyteArray jHash = (jbyteArray)tEnv->CallObjectMethod(dsClient, writeID, dsDataUnit, bArray);
    tEnv->DeleteLocalRef(bArray);
    tEnv->DeleteLocalRef(jName);
    tEnv->DeleteLocalRef(dsDataUnit);
    bool status = NO_ERRORS;
    if(jHash == NULL)
        status = OP_FAILED;
    tEnv->DeleteLocalRef(jHash);
    return status;
}

bool DepskyStorage::remove(const string& name){
    JNIEnv* tEnv = attachThread();
    ///create dataunit
    jstring jName = tEnv->NewStringUTF(name.c_str());
    jobject dsDataUnit = tEnv->NewObject(dsDataUnitClass, dataUnitConstructor, jName);
    if(dsDataUnit == NULL){
        error("DEPSKY::Remove: Couldn't create DepSkySDataUnit object");
        return OP_FAILED;
    }

    tEnv->CallVoidMethod(dsDataUnit, setModeID, true);
    tEnv->CallVoidMethod(dsClient, removeID, dsDataUnit);

    tEnv->DeleteLocalRef(jName);
    tEnv->DeleteLocalRef(dsDataUnit);
    return NO_ERRORS;
}

double DepskyStorage::getTotalNetUp(){
    if(NULL != netUpID){
        JNIEnv* tEnv = attachThread();
        return ((double)tEnv->CallLongMethod(dsClient, netUpID))/1000000000;
    }
    warning("DEPSKY::getNetUp: method is undefined");
    return 0;
}

double DepskyStorage::getTotalNetDown(){
    if(NULL != netDownID){
        JNIEnv* tEnv = attachThread();
        return ((double)tEnv->CallLongMethod(dsClient, netDownID))/1000000000;
    }
    warning("DEPSKY::getNetDown: method is undefined");
    return 0;
}

double DepskyStorage::getParallelNetUp()
{
    return getTotalNetUp();
}

double DepskyStorage::getParallelNetDown()
{
    return getTotalNetDown();
}

void DepskyStorage::resetTimes(){
    if(NULL != resetTimesID){
        JNIEnv* tEnv = attachThread();
        tEnv->CallVoidMethod(dsClient, resetTimesID);
    }
    else{
        warning("DEPSKY::resetTimes: method is undefined");
    }
}

}//end namespace mie::depsky