#ifndef DEPSKY_STORAGE_H
#define DEPSKY_STORAGE_H

#include <jni.h>
#include <vector>
#include <string>
#include <map>
#include <mutex>
#include <thread>
#include "BackendStorage.h"
#include "Status.h"

namespace MIE::DEPSKY{

class DepskyStorage: public BackendStorage{

    JavaVM *jvm;
    JNIEnv *mainEnv;
    jobject dsClient;
    jclass dscClass;
    jclass dsDataUnitClass;
    jmethodID dataUnitConstructor;
    jmethodID writeID;
    jmethodID readID;
    jmethodID setModeID;
    jmethodID removeID;
    jmethodID netUpID;
    jmethodID netDownID;
    jmethodID resetTimesID;
    int id;
    std::map<std::thread::id, JNIEnv*> threadEnv;
    std::mutex lock;

    JNIEnv* attachThread();
    void detachThread();

  public:
    DepskyStorage(int model, int cid = 1);
    ~DepskyStorage();
    int read(const std::string& name, std::vector<char>& buffer);
    bool write(const std::string& name, const char* buffer, unsigned buffer_size);
    bool remove(const std::string& name);
    double getTotalNetUp();
    double getTotalNetDown();
    double getParallelNetUp();
    double getParallelNetDown();
    void resetTimes();
};

}//end namespace mie
#endif
