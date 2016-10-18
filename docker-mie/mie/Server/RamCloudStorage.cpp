#include <queue>
#include <list>
#include <cmath>
#include <sstream>
#include <iomanip>
#include <memory>
#include "RamCloudStorage.h"
#include "ServerUtil.h"

#include <fstream>
#include <iostream>
#include <signal.h>
#include <stdexcept>
#include <exception>

using namespace std;
using namespace RAMCloud;

namespace MIE::RC{

enum RAMCLOUD_REQUEST_CODES{
    READ        = 0,
    WRITE       = 1,
    PARALLEL    = 2,
};

pthread_mutex_t *RamCloudStorage::opensslLocks;

struct RamCloudRequestData{
    //id of the table where to read/write
    uint64_t table_id;
    //keep track of how many threads for clean up
    size_t total_threads;
    //how many threads need to finish
    size_t n_threads;
    //read/write or parallel
    int op_mode;
    //used for sign/verification openssl methods
    RamCloudStorage *storage;
    //connection to the server
    RamCloudClient client;
    //map to hold the fragments of the file to read/write
    shared_ptr<map<string, shared_ptr<vector<char>>>> fragments;
    //list of metadata retrieves from servers, used when reading, shared
    shared_ptr<vector<string>> list;
    //version control across threads
    shared_ptr<map<int,unsigned>> aVersionControl;
    //current version of the file
    shared_ptr<int> aVersion;
    //how many threads submited their version
    shared_ptr<unsigned> aThreadsVersion;
    //name of the file 
    shared_ptr<string> name;
    //initial metadata from reedsol, used when writing
    shared_ptr<string> metadata;
    //synchronize access to shared data structures between threads
    shared_ptr<mutex> shared_lock;
    //signal threads that a phase is complete
    shared_ptr<condition_variable> ready;
    //keep track of how many threads have finished successfully
    shared_ptr<unsigned> done;
    //keep track of how many threads failed //only here for completness
    shared_ptr<unsigned> failed;
    //keep track of how many fragments of each version were retrieveds
    shared_ptr<map<int, unsigned>> aRetrievedFragments;
    //keep track of total fragments retrieved
    shared_ptr<unsigned> aTotalFragments;
};

struct WorkerData{
    bool* terminate;
    RamCloudClient client;
    RamCloudStorage* storage;
    shared_ptr<queue<void*>> requests;
    shared_ptr<mutex> queue_lock;
    shared_ptr<condition_variable> request_ready;
};

RamCloudStorage::RamCloudStorage(int clusters) :  terminate(false), parallel_requests(false),
    active_connections(0), threads(0), max_connections(32), netUploadTotalTime(0),
    netUploadParallelTime(0), netDownloadTotalTime(0), netDownloadParallelTime(0), uploaders(0),
    downloaders(0)
{
    ramcloud_host = getRamcloudHost();
    ramcloud_port = getRamcloudPort();
    ramcloud_cluster_name = getClusterName();
    unsigned ramcloud_servers = getRamcloudClusters();
    string table_name = getTableName();
    rsa_bits = getRSABits();
    rsa_exp = getRSAExp();
    reed_sol_k = getReedSolK();  ///influences size of fragments
    reed_sol_m = getReedSolM();  ///influences fault recovery
    reed_sol_w = getReedSolW();

    initOpenSSL();
    if(!readKey()){
        generateKey();
    }
    if(!parallel_requests){
        for(unsigned i = 1; i <= ramcloud_servers; i++){
            requests[i] = make_shared<queue<void*>>();
            shared_ptr<mutex> queue_lock (new mutex());
            shared_ptr<condition_variable> request_ready (new condition_variable());
            thread_locks[i] = pair<shared_ptr<mutex>, shared_ptr<condition_variable>>
                    (queue_lock, request_ready);
            struct WorkerData *data = new WorkerData;
            data->terminate = &terminate;
            data->requests = requests[i];
            string table_name_tmp = table_name;
            if(0 == clusters){
                data->client = RamCloudClient(ramcloud_host, ramcloud_port, ramcloud_cluster_name + 
                    to_string(0));
                table_name_tmp += to_string(i);
            }
            else{
                data->client = RamCloudClient(ramcloud_host, ramcloud_port, ramcloud_cluster_name +
                    to_string(i));
            }
            data->storage = this;
            data->queue_lock = queue_lock;
            data->request_ready = request_ready;
            uint64_t table_id;
            int res = data->client.getTableId(table_name_tmp, table_id);
            if(res != NO_ERRORS){
                res = data->client.createTable(table_name_tmp, 1, table_id);
            }
            if(res == NO_ERRORS)
                tableIds[i] = table_id;

            thread worker (ServerManager, (void*)data);
            worker.detach();
        }
    }
    else{
        ///assign table names
        vector<string> ramcloud_table_names;
        for(unsigned i = 1; i <= ramcloud_servers; i++){
            string name(table_name + to_string(i));
            ramcloud_table_names.push_back(name);
        }
        ///create table or retrieve its id
        queue<RamCloudClient> clients = adquireConnections(1);
        RamCloudClient client = clients.front();
        for(unsigned i = 0; i < ramcloud_servers; i++){
            uint64_t table_id;
            int res = client.getTableId(ramcloud_table_names[i], table_id);
            if(res != NO_ERRORS){
                res = client.createTable(ramcloud_table_names[i], 1, table_id);
            }
            if(res == NO_ERRORS)
                tableIds[i] = table_id;
        }
        releaseConnection(client);
    }
}

RamCloudStorage::~RamCloudStorage()
{
    terminate = true;
    for(map<unsigned, pair<shared_ptr<mutex>, shared_ptr<condition_variable>>>::iterator 
            it = thread_locks.begin(); it != thread_locks.end(); ++it){
        it->second.second->notify_all();
    }
    destroyOpenSSL();
    unique_lock<mutex> connections_lock(lock);
  retry:
    while(!connections.empty()){
        connections.pop();
        active_connections--;
    }
    if(active_connections > 0){
        cout<<"Waiting for "<<active_connections<<endl<<flush;
        ready.wait(connections_lock);
        goto retry;
    }
    cout<<"Threads: "<<threads<<endl<<flush;
}

void RamCloudStorage::initOpenSSL()
{
    ERR_load_crypto_strings();
    digest = EVP_sha512();
    EVP_add_digest(digest);
    rsa_key = EVP_PKEY_new();
    ///initialize openssl locks
    int i;
    opensslLocks=(pthread_mutex_t *)OPENSSL_malloc(CRYPTO_num_locks() * sizeof(pthread_mutex_t));
    for(i=0; i<CRYPTO_num_locks(); i++) {
        pthread_mutex_init(&(opensslLocks[i]), NULL);
    }
    CRYPTO_set_locking_callback((void (*)(int, int, const char*, int))opensslLock);
    CRYPTO_set_id_callback((unsigned long (*)())opensslThreadId);
}

void RamCloudStorage::destroyOpenSSL()
{
    CRYPTO_set_locking_callback(NULL);
    for (int i=0; i<CRYPTO_num_locks(); i++){
        pthread_mutex_destroy(&(opensslLocks[i]));
    }
    OPENSSL_free(opensslLocks);
}

void RamCloudStorage::opensslLock(int mode, int type, char *file, int line)
{
    if (mode & CRYPTO_LOCK){
        pthread_mutex_lock(&(opensslLocks[type]));
    }
    else {
        pthread_mutex_unlock(&(opensslLocks[type]));
    }
}

unsigned long RamCloudStorage::opensslThreadId(void)
{
    unsigned long ret;
    ret=(unsigned long)pthread_self();
    return(ret);
}

bool RamCloudStorage::readKey(){
    FILE* priv_key_file = fopen("priv1.pem", "r");
    if(priv_key_file == NULL){
        printf("Private key file not found\n");
        return false;
    }
    FILE* pub_key_file = fopen("pub1.pem", "r");
    if(pub_key_file == NULL){
        printf("Public key file not found\n");
        fclose(priv_key_file);
        return false;
    }
    bool read = false;
    cout<<"Reading keys from files"<<endl;
    if(PEM_read_PUBKEY(pub_key_file, &rsa_key, NULL, NULL) == NULL){
        ERR_print_errors_fp(stderr);
        goto cleanup;
    }
    if(PEM_read_PrivateKey(priv_key_file, &rsa_key, NULL, NULL) == NULL){
        ERR_print_errors_fp(stderr);
        goto cleanup;
    }
    //cout<<"Private key read"<<endl;
    read = true;
    cout<<"Keys read successfully"<<endl;
    ///clean up and return
    cleanup:
    fclose(priv_key_file);
    fclose(pub_key_file);
    return read;
}

void RamCloudStorage::generateKey(){
    if(rsa_key != NULL){
        EVP_PKEY_free(rsa_key);
    }
    rsa_key = NULL;
    cout<<"Generating new keys"<<endl;
    EVP_PKEY_CTX *ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, NULL);
    if(ctx == NULL){
        ERR_print_errors_fp(stderr);
        abort();
    }
    //cout<<"Context initialized"<<endl;
    if(EVP_PKEY_keygen_init(ctx) <= 0){
        ERR_print_errors_fp(stderr);
        abort();
    }
    //cout<<"Keygen initialized"<<endl;
    if(EVP_PKEY_CTX_set_rsa_keygen_bits(ctx, rsa_bits) <= 0){
        ERR_print_errors_fp(stderr);
        abort();
    }
    //cout<<"rsa bit set"<<endl;
    if(EVP_PKEY_keygen(ctx, &rsa_key) <= 0){
        ERR_print_errors_fp(stderr);
        abort();
    }
    cout<<"Keys generated, now storing in files"<<endl;
    ///store keys in file
    FILE* priv_key_file = fopen("priv1.pem", "w");
    FILE* pub_key_file = fopen("pub1.pem", "w");
    if(!PEM_write_PrivateKey(priv_key_file, rsa_key, NULL, NULL, 0, NULL, NULL)){
        ERR_print_errors_fp(stderr);
        abort();
    }
    if(!PEM_write_PUBKEY(pub_key_file, rsa_key)){
        ERR_print_errors_fp(stderr);
        abort();
    }
    fclose(priv_key_file);
    fclose(pub_key_file);
    cout<<"Keys stored. Key generation done"<<endl;
}

void RamCloudStorage::ServerManager(void* workerData)
{
    struct WorkerData* data = (struct WorkerData*)workerData;
    unique_lock<mutex> queue_lock (*data->queue_lock, defer_lock);
    while(!*data->terminate){
        RamCloudRequestData* request = NULL;
        queue_lock.lock();
        while(data->requests->empty()){
            data->request_ready->wait(queue_lock);
            if(*data->terminate)
                goto cleanup;
        }
        request = (RamCloudRequestData*) data->requests->front();
        data->requests->pop();
        queue_lock.unlock();
        request->client = data->client;
        if(request->op_mode == READ){
            readRequest(request);
        }
        else if(request->op_mode == WRITE){
            writeRequest(request);
        }
    }
  cleanup:
    //data->storage->releaseConnection(data->client);
    delete data;
}

queue<RamCloudClient> RamCloudStorage::adquireConnections(unsigned nConnections)
{
    queue<RamCloudClient> clients;
    unique_lock<mutex> connections_lock(lock);
    retry:
    if(connections.size() >= nConnections){
        for(unsigned i = 0; nConnections > i; i++){
            clients.push(connections.front());
            connections.pop();
        }
    }
    else{
        if(active_connections - connections.size() + nConnections < max_connections){
            for(unsigned i = 0; nConnections > i; i++){
                if(!connections.empty()){
                    clients.push(connections.front());
                    connections.pop();
                }
                else{
                    clients.push(RamCloudClient(ramcloud_host, ramcloud_port,
                        ramcloud_cluster_name));
                    active_connections++;
                }
            }
        }
        else{
            ready.wait(connections_lock);
            goto retry;
        }
    }
    return clients;
}

void RamCloudStorage::releaseConnection(RamCloudClient& connection)
{
    unique_lock<mutex> connections_lock(lock);
    connections.push(connection);
    ready.notify_all();
}

int RamCloudStorage::read(const string& name, vector<char>& buffer)
{
    int result = NOT_FOUND;
    if(!terminate)
        if(processFragments(name, &buffer))
            result = buffer.size();
    return result;
}

bool RamCloudStorage::remove(const string& name)
{
    if(!terminate)
        if(processFragments(name))
            return NO_ERRORS;
    return OP_FAILED;
}

bool RamCloudStorage::processFragments(const string& name, vector<char>* buffer)
{
    down_lock.lock();
    timespec start = getTime();
    if(0 == downloaders)
        download_start = start;
    downloaders++;
    down_lock.unlock();
    shared_ptr<vector<string>> metadata_list (new vector<string>());
    metadata_list->reserve(tableIds.size());
    shared_ptr<map<string, shared_ptr<vector<char>>>> fragments;
    if(buffer != NULL)
        fragments = make_shared<map<string, shared_ptr<vector<char>>>>();
    shared_ptr<mutex> shared_lock(new mutex());
    shared_ptr<condition_variable> ready(new condition_variable);
    shared_ptr<map<int,unsigned>> version_control(new map<int,unsigned>);
    shared_ptr<map<int,unsigned>> retrieved_fragments(new map<int,unsigned>);
    shared_ptr<int> version(new int(INVALID_STATE));
    shared_ptr<unsigned> threads_version(new unsigned(0));
    shared_ptr<unsigned> done(new unsigned(0));
    shared_ptr<unsigned> failed(new unsigned(0));
    shared_ptr<unsigned> total_fragments(new unsigned(0));
    size_t max_failures = (tableIds.size() - 1)/3;
    size_t n_threads = tableIds.size() - max_failures;
    shared_ptr<string> tmp_name (new string(name));
    queue<RamCloudClient> clients;
    if(parallel_requests) 
        clients = adquireConnections(tableIds.size());
    unique_lock<mutex> managers_lock(aManagersGlobalLock);
    for(map<unsigned, uint64_t>::iterator it = tableIds.begin(); it != tableIds.end(); ++it){
        RamCloudRequestData *request = new RamCloudRequestData;
        request->total_threads = tableIds.size();
        request->n_threads = n_threads;
        request->table_id = it->second;
        request->fragments = fragments;
        request->list = metadata_list;
        request->name = tmp_name;
        request->storage = this;
        request->shared_lock = shared_lock;
        request->ready = ready;
        request->metadata = NULL;
        request->done = done;
        request->failed = failed;
        request->aVersionControl = version_control;
        request->aVersion = version;
        request->aThreadsVersion = threads_version;
        request->aRetrievedFragments = retrieved_fragments;
        request->aTotalFragments = total_fragments;
        if(parallel_requests){
            request->op_mode = PARALLEL;
            request->client = clients.front();
            clients.pop();
            thread tmp(readRequest, (void*)request);
            tmp.detach();
            threads++;
        }
        else{
            request->op_mode = READ;
            shared_ptr<mutex> queue_lock = thread_locks[it->first].first;
            shared_ptr<condition_variable> request_ready = thread_locks[it->first].second;
            unique_lock<mutex> tmp(*queue_lock);
            requests[it->first]->push((void*)request);
            request_ready->notify_all();
        }
    }
    managers_lock.unlock();
    bool success = false;
    unique_lock<mutex> phase_lock(*shared_lock);
    /*while((*done < n_threads && 
        ((fragments && fragments->size() < max_failures + 1) || metadata_list->size() < n_threads))
        && *failed <= max_failures){
        ready->wait(phase_lock);
    }*/
    while((fragments && (fragments->size() < max_failures + 1 || metadata_list->size() < n_threads) &&
        *failed <= max_failures) || (!fragments && *done < n_threads && *failed <= max_failures)){
        ready->wait(phase_lock);   
    }
    if(max_failures >= *failed && !fragments)
        success = true;
    phase_lock.unlock();
    timespec end = getTime();
    double time_total = diffSec(start, end);
    down_lock.lock();
    double time_parallel = diffSec(download_start, end);
    netDownloadTotalTime += time_total;
    netDownloadParallelTime += time_parallel;
    downloaders--;
    download_start = end;
    down_lock.unlock();
    phase_lock.lock();
    if(n_threads <= metadata_list->size()){
        int k = 0;
        int m = 0;
        int w = 0;
        int original_size = 0;
        if(getReedSolMetadata(*metadata_list, tableIds.size() - n_threads, k, m, w, original_size))
            if(decode(*fragments, k, m, w, *buffer, original_size))
                success = true;
    }
    #ifdef MIE_DEBUG
    if(success == false && name.compare("blobs.blob") != 0){
        printf("\n\nDEBUG:\n%d %lu %d %lu %lu\n\n", *done, n_threads, *failed, max_failures,
            metadata_list->size());
        fflush(stdin);
        raise(SIGABRT);
    }
    #endif
    return success;
}

void RamCloudStorage::readRequest(void* threadData)
{
    //cout<<"thread: "<<threadData<<endl;
    RamCloudRequestData* data = (RamCloudRequestData*) threadData;
    string metadata_name(*data->name+".metadata");
    vector<char> b;
    string metadata;
    bool verified = false;
    int status = data->client.read(data->table_id, metadata_name, b);
    if(0 <= status){
        string tmp(b.data(), b.size());
        ///verify integrity of metadata
        int pos1 = tmp.find("signature\n");
        string signature = tmp.substr(pos1+strlen("signature\n"));
        metadata = tmp.substr(0, pos1);
        verified = data->storage->verify((char*)metadata.c_str(), metadata.size(), 
            (const unsigned char*)signature.c_str(), signature.size());
    }
    int thread_version;
    int global_version;
    if(verified){
        thread_version = extractVersion(b);
        global_version = checkVersion(threadData, thread_version);
        if(NOT_FOUND == global_version && data->fragments != NULL){
            status = NOT_FOUND;
            goto end;
        }
        else if(OP_FINISHED == global_version && data->fragments != NULL){
            status = NO_ERRORS;
            unique_lock<mutex> tmp(*data->shared_lock);
            data->list->push_back(metadata);
            goto end;
        }
        else if(0 < global_version && thread_version == global_version && data->fragments != NULL){
            unique_lock<mutex> tmp(*data->shared_lock);
            data->list->push_back(metadata);
        }
        #ifdef MIE_DEBUG
        else{
            printf("BACKEND: Starting to read %s version %d\n", data->name->c_str(), global_version);
            fflush(stdin);
        }
        #endif
        if(data->fragments == NULL){
            if(data->client.remove(data->table_id, metadata_name) != NO_ERRORS){
                status = RAMCLOUD_EXCEPTION;
                goto end; //line 534
            }
        }
        size_t ids_start = metadata.find_first_of(' ', metadata.find("ids")+4)+1;
        string ids = metadata.substr(ids_start);
        size_t pos1 = 0, pos2;
        while(0 <= status){
            pos2 = ids.find_first_of(';', pos1);///split between file data
            if(pos2 == string::npos)
                break;
            int pos3 = ids.find_first_of(' ', pos1);///split between name and hash
            string n = ids.substr(pos1, pos3-pos1);
            string data_name(*(data->name)+"."+n+".datavalue");
            if(data->fragments != NULL){
                string hash = ids.substr(pos3+1, pos2-1-pos3);
                shared_ptr<vector<char>> data_buffer (new vector<char>());
                status = data->client.read(data->table_id, data_name, *data_buffer);
                if(0 <= status){
                    vector<char> hash_buffer;
                    int hash_size = data->storage->getHash(data_buffer->data(), data_buffer->size(),
                            hash_buffer);
                    string sHash;
                    convertHashToHex(hash_buffer.data(), hash_size, sHash);
                    if(0 == hash.compare(sHash)){
                        if(getQuorum(threadData, thread_version)){
                            unique_lock<mutex> tmp(*data->shared_lock);
                            (*data->fragments)[n] = data_buffer;
                            data->list->push_back(metadata);
                            #ifdef MIE_DEBUG
                            printf("BACKEND: Read %s\n", data_name.c_str());
                            fflush(stdin);
                            #endif
                        }
                        else{
                            warning("Version check failed for "+data_name);
                            status = NOT_FOUND;
                            #ifdef MIE_DEBUG
                            raise(SIGABRT);
                            #endif
                        }
                    }
                    else{
                        warning("Integrity check failed for "+data_name);
                        status = NOT_FOUND;
                        #ifdef MIE_DEBUG
                        raise(SIGABRT);
                        #endif
                    }
                }
                else{
                    #ifdef MIE_DEBUG
                    printf("BACKEND: %s not found\n", data_name.c_str());
                    fflush(stdin);
                    #endif
                    status = NOT_FOUND;
                }
            }
            else{
                if(data->client.remove(data->table_id, data_name) != NO_ERRORS)
                    status = RAMCLOUD_EXCEPTION;
            }
            pos1 = pos2+1;
        }
    }
    else{ 
        if(0 <= status)
            warning("Integrity check failed for "+metadata_name);
        if(data->fragments == NULL && 0 > status)
            status = NO_ERRORS;//we treat removal of files that dont exist as success
        else
            status = NOT_FOUND;
        getCurrentVersion(threadData, b);
    }
    end:
    #ifdef MIE_DEBUG
    if(0 > status && NULL == data->fragments)
        raise(SIGABRT);
    #endif
    data->shared_lock->lock();
    if(0 > status)
        *(data->failed) += 1;
    else
        *(data->done) += 1;
    /*if(0 > status && NULL != data->fragments && data->name->compare("blobs.blob") != 0 &&
        *data->failed > 1){
        map<int, unsigned>* tt = data->aVersionControl.get();
        raise(SIGABRT);
    }*/
    if(data->n_threads <= *(data->done) || data->total_threads - data->n_threads < *data->failed ||
        (NULL != data->fragments && data->fragments->size() >= data->total_threads - data->n_threads
        + 1)){
        data->ready->notify_all();
    }
    #ifdef MIE_DEBUG
    printf("%u %lu %u %lu\n", *data->done, data->n_threads, *data->failed, data->total_threads - 
        data->n_threads);
    fflush(stdin);
    #endif
    data->shared_lock->unlock();
    if(data->op_mode == PARALLEL){
        data->storage->threads--;
        data->storage->releaseConnection(data->client);
    }
    delete data;
}

bool RamCloudStorage::getReedSolMetadata(const std::vector<std::string>& metas, int nThreads, int& k,
    int& m, int& w, int& originalSize)
{
    map<string,map<int, int>> metadata_values;
    ///get all values different metadatas propose
    for(vector<string>::const_iterator it = metas.begin(); it != metas.end(); ++it){
        const string *metadata = &*it;
        //cout<<metadata<<endl;
        int pos1 = metadata->find_first_of('\n');
        int tmp;
        tmp = stoi(metadata->substr(0, pos1));
        map<int,int>::iterator it2 = metadata_values["sizes"].find(tmp);
        if(metadata_values["sizes"].end() != it2)
            it2->second++;
        else
            metadata_values["sizes"][tmp] = 1;

        int pos2 = metadata->find_first_of(' ', pos1);
        tmp = stoi(metadata->substr(pos1 + 1, pos2 - pos1 - 1));
        it2 = metadata_values["ks"].find(tmp);
        if(metadata_values["ks"].end() != it2)
            it2->second++;
        else
            metadata_values["ks"][tmp] = 1;

        pos1 = metadata->find_first_of(' ', pos2 + 1);
        tmp = stoi(metadata->substr(pos2 + 1, pos1 - pos2 - 1));
        it2 = metadata_values["ms"].find(tmp);
        if(metadata_values["ms"].end() != it2)
            it2->second++;
        else
            metadata_values["ms"][tmp] = 1;

        pos2 = metadata->find_first_of(' ', pos1 + 1);
        tmp = stoi(metadata->substr(pos1 + 1, pos2 - pos1 - 1));
        it2 = metadata_values["ws"].find(tmp);
        if(metadata_values["ws"].end() != it2)
            it2->second++;
        else
            metadata_values["ws"][tmp] = 1;
    }
    ///get the majority
    bool status = true;
    for(map<string,map<int,int>>::iterator it = metadata_values.begin(); it != metadata_values.end(); ++it){
        string key = it->first;
        int count = 0;
        int maj = 0;
        for(map<int,int>::iterator it2 = it->second.begin(); it2 != it->second.end(); ++it2)
            if(it2->second > count){
                maj = it2->first;
                count = it2->second;
            }
        if(count >= nThreads){
            if(key.compare("sizes") == 0)
                originalSize = maj;
            else if(key.compare("ks") == 0)
                k = maj;
            else if(key.compare("ms") == 0)
                m = maj;
            else if(key.compare("ws") == 0)
                w = maj;
        }
        else{
            status = false;
        }
    }
    return status;
}

bool RamCloudStorage::write(const string& name, const char* buffer, unsigned bufferSize)
{
    if(terminate)
        return OP_FAILED;
    shared_ptr<map<string, shared_ptr<vector<char>>>> fragments
            (new map<string, shared_ptr<vector<char>>>());
    int k = reed_sol_k;
    int w = reed_sol_w;
    encode((char*)buffer, bufferSize, k, reed_sol_m, w, *fragments);
    shared_ptr<string> metadata = shared_ptr<string>(new string(to_string(bufferSize) + "\n" +
        to_string(k) + " " + to_string(reed_sol_m) + " " + to_string(w)));
    ///split fragments between tables
    map<unsigned, shared_ptr<vector<string>>> thread_assignments;
    int used_tables = 0;
    bool all_used = false;
    map<unsigned, uint64_t>::iterator tables_it = tableIds.begin();
    for(map<string, shared_ptr<vector<char>>>::iterator it = fragments->begin();
            it != fragments->end(); ++it){
        shared_ptr<vector<string>> tmp = thread_assignments[tables_it->first];
        if(tmp == NULL){
            tmp = make_shared<vector<string>>();
            thread_assignments[tables_it->first] = tmp;
        }
        tmp->push_back(it->first);
        if(!all_used){
            used_tables++;
        }
        ++tables_it;
        if(tables_it == tableIds.end()){
            all_used = true;
            tables_it = tableIds.begin();
        }
    }
    shared_ptr<mutex> shared_lock(new mutex());
    shared_ptr<condition_variable> ready(new condition_variable());
    shared_ptr<map<int,unsigned>> version_control(new map<int,unsigned>);
    shared_ptr<int> version(new int(INVALID_STATE));
    shared_ptr<unsigned> threads_version(new unsigned(0));
    shared_ptr<unsigned> done(new unsigned(0));
    shared_ptr<unsigned> failed(new unsigned(0));
    size_t max_failures = (tableIds.size() - 1) / 3;
    size_t n_threads = tableIds.size() - max_failures;
    shared_ptr<string> tmp_name (new string(name));
    queue<RamCloudClient> clients;
    if(parallel_requests)
        clients = adquireConnections(thread_assignments.size());
    up_lock.lock();
    timespec start = getTime();
    if(0 == uploaders)
        upload_start = start;
    uploaders++;
    up_lock.unlock();
    unique_lock<mutex> managers_lock(aManagersGlobalLock);
    for(map<unsigned, shared_ptr<vector<string>>>::iterator 
            it = thread_assignments.begin(); it != thread_assignments.end(); ++it){
        RamCloudRequestData *request_data = new RamCloudRequestData;
        request_data->table_id = tableIds[it->first];
        request_data->total_threads = tableIds.size();
        request_data->n_threads = n_threads;
        request_data->fragments = fragments;
        request_data->list = it->second;
        request_data->name = tmp_name;
        request_data->storage = this;
        request_data->metadata = metadata;
        request_data->shared_lock = shared_lock;
        request_data->done = done;
        request_data->ready = ready;
        request_data->failed = failed;
        request_data->aVersionControl = version_control;
        request_data->aVersion = version;
        request_data->aThreadsVersion = threads_version;
        if(parallel_requests){
            request_data->client = clients.front();
            clients.pop();
            request_data->op_mode = PARALLEL;
            thread tmp(writeRequest, (void*)request_data);
            tmp.detach();
            threads++;
        }
        else{
            request_data->op_mode = WRITE;
            shared_ptr<mutex> queue_lock = thread_locks[it->first].first;
            shared_ptr<condition_variable> request_ready = thread_locks[it->first].second;
            unique_lock<mutex> tmp(*queue_lock);
            requests[it->first]->push((void*)request_data);
            request_ready->notify_all();
        }
    }
    managers_lock.unlock();
    unique_lock<mutex> phase_lock(*shared_lock);
    while(n_threads > *done && max_failures >= *failed)
        ready->wait(phase_lock);
    phase_lock.unlock();
    ///*********************************************************************************///
    timespec end = getTime();
    double time_total = diffSec(start, end);
    up_lock.lock();
    double time_parallel = diffSec(upload_start, end);
    netUploadTotalTime += time_total;
    netUploadParallelTime += time_parallel;
    uploaders--;
    upload_start = end;
    up_lock.unlock();
    if(n_threads <= *done)
        return NO_ERRORS;
    else
        return OP_FAILED;
}

void RamCloudStorage::writeRequest(void* threadData)
{
    RamCloudRequestData* data = (RamCloudRequestData*)threadData;
    bool status = NO_ERRORS;
    //get file version
    string meta_name(*(data->name)+".metadata");
    vector<char> old_metadata;
    int version = 0;
    data->client.read(data->table_id, meta_name, old_metadata);
    version = getCurrentVersion(data, old_metadata);
    string fragment_metadata = *data->metadata;
    if(NOT_FOUND == version){
        status = OP_FAILED;
        goto end; //line 690
    }
    version++;
    fragment_metadata += " " + to_string(version) + "\nids\n" + to_string(data->table_id) + " ";
    //write fragments
    for(vector<string>::iterator it = data->list->begin(); it != data->list->end(); ++it){
        string data_name(*(data->name)+"."+*it+".datavalue");
        shared_ptr<vector<char>> fragData = (*data->fragments)[*it];
        if(fragData->size() == 0)
            assert(false);
        ///get hash of fragment
        vector<char> hash;
        int hash_size = data->storage->getHash(fragData->data(), fragData->size(), hash);
        string sHash;
        convertHashToHex(hash.data(), hash_size, sHash);
        if(data->client.write(data->table_id, data_name, fragData->data(), fragData->size()) 
            != NO_ERRORS){
            status = OP_FAILED;
            break;
        }
        fragment_metadata += *it + " " + sHash + ";";
    }
    if(status){
        ///sign it
        vector<char> signature;
        data->storage->sign((char*)fragment_metadata.data(), (int)fragment_metadata.size(), signature);
        fragment_metadata += "signature\n";
        fragment_metadata.append(signature.data(), signature.size());
        if(data->client.write(data->table_id, meta_name, (const char*)fragment_metadata.data(),
            fragment_metadata.size())
            != NO_ERRORS)
            status = OP_FAILED;
    }
    end:
    data->shared_lock->lock();
    if(status)
        *(data->done) += 1;
    else
        *(data->failed) += 1;
    if(*(data->done) == data->n_threads || data->total_threads - data->n_threads < *data->failed){
        data->ready->notify_all();
    }
    #ifdef MIE_DEBUG
    printf("%u %lu %u %lu\n", *data->done, data->n_threads, *data->failed, data->total_threads - 
        data->n_threads);
    fflush(stdin);
    #endif
    data->shared_lock->unlock();
    #ifdef MIE_DEBUG
    if(status){
        printf("BACKEND: write %s version %d\n", data->name->c_str(), version);
        fflush(stdin);
    }
    else{
        printf("BACKEND: failed to write %s version %d\n", data->name->c_str(), version);
        fflush(stdin);
        raise(SIGABRT);
    }
    #endif
    if(data->op_mode == PARALLEL){
        data->storage->threads--;
        data->storage->releaseConnection(data->client);
    }
    delete data;
}

int RamCloudStorage::checkVersion(void* threadData, int version)
{
    RamCloudRequestData* data = (RamCloudRequestData*)threadData;
    unique_lock<mutex> tmp(*data->shared_lock);
    map<int,unsigned>::iterator it = data->aVersionControl->find(version);
    if(data->aVersionControl->end() != it)
        it->second++;
    else
        (*data->aVersionControl)[version] = 1;
    (*data->aThreadsVersion)++;
    if(*data->aThreadsVersion < data->n_threads){
        return NO_QUORUM;
    }
    else{
        int g_version = calculateVersion(data->aVersionControl, data->n_threads);
        if(NOT_FOUND != g_version || *data->aThreadsVersion == data->total_threads){
            *data->aVersion = g_version;
            data->ready->notify_all();
            if((*data->aRetrievedFragments)[g_version] >= data->total_threads - data->n_threads + 1)
                return OP_FINISHED;
            else
                return *data->aVersion;
        }
        else{
            return NO_QUORUM;
        }
    }
}

bool RamCloudStorage::getQuorum(void* threadData, int version)
{
    RamCloudRequestData* data = (RamCloudRequestData*)threadData;
    unique_lock<mutex> tmp(*data->shared_lock);
    (*data->aTotalFragments)++;
    map<int,unsigned>::iterator it = data->aRetrievedFragments->find(version);
    if(data->aRetrievedFragments->end() != it)
        it->second++;
    else
        (*data->aRetrievedFragments)[version] = 1;
    //wait until f + 1 fragments are retrieved
    bool wait = false;
    while(((*data->aRetrievedFragments)[version] < data->total_threads - data->n_threads + 1 || 
        *data->aVersion == INVALID_STATE) && data->total_threads > *data->aTotalFragments){
        wait = true;
        data->ready->wait(tmp);
    }
    if(!wait && (*data->aRetrievedFragments)[version] >= data->total_threads - data->n_threads + 1)
        data->ready->notify_all();
    if(*data->aVersion == version)
        return true;
    else
        return false;
}

int RamCloudStorage::getCurrentVersion(void* threadData, const vector<char>& oldMetadata)
{
    RamCloudRequestData* data = (RamCloudRequestData*)threadData;
    int version = extractVersion(oldMetadata);
    unique_lock<mutex> tmp(*data->shared_lock);
    map<int,unsigned>::iterator it = data->aVersionControl->find(version);
    if(data->aVersionControl->end() != it)
        it->second++;
    else
        (*data->aVersionControl)[version] = 1;
    (*data->aThreadsVersion)++;
    while(*data->aThreadsVersion < data->n_threads){
        data->ready->wait(tmp);
    }
    if(INVALID_STATE == *data->aVersion){
        version = calculateVersion(data->aVersionControl, data->n_threads);
        if(NOT_FOUND != version || *data->aThreadsVersion == data->total_threads){
            *data->aVersion = version;
            data->ready->notify_all();
        }
        else{
            while(*data->aThreadsVersion < data->total_threads)
                data->ready->wait(tmp);
        }
    }
    return *data->aVersion;
}

int RamCloudStorage::extractVersion(const vector<char>& metadata)
{
    int version = 0;
    unsigned n = 0;
    unsigned i_n = 0;
    unsigned i_s = 0;
    bool done = false;
    while(!done && metadata.size() > i_n){
        if('\n' == metadata[i_n]){
            n++;
            if(2 == n){
                version = stoi(string(metadata.data() + i_s + 1, i_n - i_s - 1));
                done = true;
            }
        }
        else if(' ' == metadata[i_n]){
            i_s = i_n;
        }
        i_n++;
    }
    return version;
}

int RamCloudStorage::calculateVersion(shared_ptr<map<int, unsigned>> versionControl, unsigned nThreads)
{
    int version = NOT_FOUND;
    int candidate = -1;
    unsigned count = 0;
    for(map<int,unsigned>::iterator it = versionControl->begin(); it != versionControl->end(); ++it)
        if(it->second > count){
            candidate = it->first;
            count = it->second;
        }
    if(count >= nThreads)
        version = candidate;
    return version;
}

bool RamCloudStorage::sign(const char* data, int data_len, vector<char>& signature){
    EVP_MD_CTX* ctx = EVP_MD_CTX_create();
    //cout<<"DigestInit"<<endl;
    if(!EVP_DigestSignInit(ctx, NULL, digest, NULL, rsa_key)){
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"DigestUpdate"<<endl;
    if(!EVP_DigestSignUpdate(ctx, data, data_len)){
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"DigestFinalLen"<<endl;
    size_t sig_len = 0;
    if(!EVP_DigestSignFinal(ctx, NULL, &sig_len)){
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"DigestFinal "<<sig_len<<endl;
    unsigned char* tmp_buffer = new unsigned char[sig_len];
    if(!EVP_DigestSignFinal(ctx, tmp_buffer, &sig_len)){
        //cout<<"DigestFinal failed"<<endl;
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"DigestFinal done"<<endl;
    signature.reserve(sig_len);
    for(size_t i = 0; i < sig_len; i++){
        signature.push_back(tmp_buffer[i]);
    }
    delete[] tmp_buffer;
    EVP_MD_CTX_destroy(ctx);
    //cout<<"Signing finished"<<endl;
    return true;
}

bool RamCloudStorage::verify(const char* data, int data_len, const unsigned char* signature,
    int sig_len)
{
    EVP_MD_CTX* ctx = EVP_MD_CTX_create();
    //cout<<"VerifyInit"<<endl;
    if(!EVP_DigestVerifyInit(ctx, NULL, digest, NULL, rsa_key)){
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"VerifyUpdate"<<endl;
    if(!EVP_DigestVerifyUpdate(ctx, data, data_len)){
        ERR_print_errors_fp(stderr);
        EVP_MD_CTX_destroy(ctx);
        return false;
    }
    //cout<<"VerifyFinal"<<endl;
    int valid = EVP_DigestVerifyFinal(ctx, signature, sig_len);
    EVP_MD_CTX_destroy(ctx);
    //cout<<"Verify finished "<<valid<<endl;
    if(valid){
        return true;
    }
    else{
        return false;
    }
}

int RamCloudStorage::getHash(const char* data, int data_size, vector<char>& hash){
    hash.resize(EVP_MAX_MD_SIZE);
    ///create context and initialize variables
    EVP_MD_CTX* ctx = EVP_MD_CTX_create();
    unsigned hash_size = 0;
    if(!EVP_DigestInit_ex(ctx, digest, NULL)){
        ERR_print_errors_fp(stderr);
    }
    ///do digest
    if(!EVP_DigestUpdate(ctx, data, data_size)){
        ERR_print_errors_fp(stderr);   
    }
    if(!EVP_DigestFinal_ex(ctx, (unsigned char*)hash.data(), &hash_size)){
        ERR_print_errors_fp(stderr);
    }
    ///cleanup
    EVP_MD_CTX_destroy(ctx);
    return hash_size;
}

void RamCloudStorage::convertHashToHex(const char* hash, int hash_size, string& sHash){
    ostringstream hs;
    hs.fill('0');
    hs<<hex;
    for(const char * ptr = hash ; ptr < hash+hash_size; ptr++){
        hs<<std::setw(2)<<(unsigned)*ptr;
    }
    sHash = hs.str();
}

double RamCloudStorage::getTotalNetUp(){
    unique_lock<mutex> tmp(up_lock);
    return netUploadTotalTime;
}

double RamCloudStorage::getTotalNetDown(){
    unique_lock<mutex> tmp(down_lock);
    return netDownloadTotalTime;
}

double RamCloudStorage::getParallelNetUp(){
    unique_lock<mutex> tmp(down_lock);
    return netUploadParallelTime;
}

double RamCloudStorage::getParallelNetDown(){
    unique_lock<mutex> tmp(down_lock);
    return netDownloadParallelTime;
}

void RamCloudStorage::resetTimes(){
    unique_lock<mutex> up(up_lock);
    unique_lock<mutex> down(down_lock);
    netUploadTotalTime = 0;
    netDownloadTotalTime = 0;
    netUploadParallelTime = 0;
    netDownloadParallelTime = 0;
}

}//end namespace MIE::RC
