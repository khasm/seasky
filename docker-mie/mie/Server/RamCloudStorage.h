#ifndef RAMCLOUD_STORAGE_H
#define RAMCLOUD_STORAGE_H

#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/rsa.h>
#include <openssl/pem.h>
#include <atomic>
#include <queue>
#include "BackendStorage.h"
#include "RamCloudClient.h"
#include "ReedSol.h"
#include "Config.h"

namespace MIE::RC{

class RamCloudStorage: public BackendStorage{

    //server connections
    std::string ramcloud_host;
    std::string ramcloud_port;
    std::string ramcloud_cluster_name;
    std::map<unsigned, uint64_t> tableIds;
    bool terminate;
    std::mutex lock;
    std::condition_variable ready;
    bool parallel_requests;

    //parallel requests
    std::atomic<unsigned int> active_connections;
    std::atomic<unsigned int> threads;
    unsigned int max_connections;
    std::queue<RamCloudClient> connections;

    //normal requests
    std::map<unsigned, std::shared_ptr<std::queue<void*>>> requests;
    std::map<unsigned, std::pair<std::shared_ptr<std::mutex>, 
            std::shared_ptr<std::condition_variable>>> thread_locks;
    std::mutex aManagersGlobalLock;
    static void ServerManager(void* workerData);
    inline static void writeToServer(void* requestData);

    //statistics
    double netUploadTotalTime;
    double netUploadParallelTime;
    double netDownloadTotalTime;
    double netDownloadParallelTime;
    std::mutex up_lock;
    std::mutex down_lock;
    timespec upload_start;
    unsigned uploaders;
    timespec download_start;
    unsigned downloaders;

    //integrity check
    int rsa_bits;
    int rsa_exp;
    const EVP_MD* digest;
    EVP_PKEY *rsa_key;
    static pthread_mutex_t *opensslLocks;

    //fragmentation
    int reed_sol_k;
    int reed_sol_m;
    int reed_sol_w;

    std::queue<RamCloudClient> adquireConnections(unsigned nConnections);
    void releaseConnection(RamCloudClient& connection);
    bool processFragments(const std::string& name, std::vector<char>* buffer = NULL);
    static int getCurrentVersion(void* threadData, const std::vector<char>& oldMetadata);
    static int checkVersion(void* threadData, int oldVersion);
    static bool getQuorum(void* threadData, int version);
    static int extractVersion(const std::vector<char>& metadata);
    static int calculateVersion(std::shared_ptr<std::map<int, unsigned>> versionControl,
        unsigned maxFaulty);
    bool getReedSolMetadata(const std::vector<std::string>& metas, int nThreads, int& k, int& m,
        int& w, int& originalSize);
    //thread functions
    static void writeRequest(void* threadData);
    static void readRequest(void* threadData);
    //openssl multi thread
    void initOpenSSL();
    void destroyOpenSSL();
    static void opensslLock(int mode, int type, char *file, int line);
    static unsigned long opensslThreadId(void);
    //integrity check
    bool readKey();
    void generateKey();
    bool sign(const char* data, int data_len, std::vector<char>& signature);
    bool verify(const char* data, int data_len, const unsigned char* signature, int sig_len);
    int getHash(const char* data, int data_size, std::vector<char>& hash);
    static void convertHashToHex(const char* hash, int hash_size, std::string& sHash);

  public:
    RamCloudStorage(int clusters = 0);
    ~RamCloudStorage();
    int read(const std::string& name, std::vector<char>& buffer);
    bool write(const std::string& name, const char* buffer, unsigned buffer_size);
    bool remove(const std::string& name);
    double getTotalNetUp();
    double getTotalNetDown();
    double getParallelNetUp();
    double getParallelNetDown();
    void resetTimes();
};

}//end namespace MIE::RC
#endif
