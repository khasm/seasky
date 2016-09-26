//
//  main.cpp
//  Server
//
//  Created by Bernardo Ferreira on 06/03/15.
//  Copyright (c) 2015 NovaSYS. All rights reserved.
//

#include "MIEServerMT.h"
#include "Status.h"

namespace MIE{

using namespace std;
using namespace cv;

//needed for linker because they are static
double MIEServerMT::aNetworkAddTotalTime;
double MIEServerMT::aNetworkAddParallelTime;
timespec MIEServerMT::aNetworkAddStart;
unsigned MIEServerMT::aUploaders;
double MIEServerMT::aNetworkGetTotalTime;
double MIEServerMT::aNetworkGetParallelTime;
timespec MIEServerMT::aNetworkGetStart;
unsigned MIEServerMT::aDownloaders;
mutex MIEServerMT::aNetworkAddTimeLock;
mutex MIEServerMT::aNetworkGetTimeLock;
Storage* MIEServerMT::storage;
MIE* MIEServerMT::mie;
size_t MIEServerMT::aResultsSize;
bool MIEServerMT::aTerminate;
bool MIEServerMT::aShutdown;
atomic<unsigned> MIEServerMT::aRequests;
mutex MIEServerMT::aTerminateLock;
condition_variable MIEServerMT::aNoRequests;

MIEServerMT::MIEServerMT(int backend, bool cache, int model)
{
    readConfig();
    storage = new Storage(backend, cache, model);
    mie = new MIE(storage);
    aResultsSize = getResultsSize();
    aTerminate = false;
    aShutdown = false;
    aRequests = 0;
}

MIEServerMT::~MIEServerMT() {
    aTerminate = true;
    unique_lock<mutex> tmp(aTerminateLock);
    while(0 < aRequests){
        aNoRequests.wait(tmp);
    }
    aShutdown = true;
    delete storage;
    delete mie;
}

void MIEServerMT::startServer() {
    aNetworkAddTotalTime = 0;
    aNetworkGetTotalTime = 0;
    aNetworkAddParallelTime = 0;
    aNetworkGetParallelTime = 0;
    aUploaders = 0;
    aDownloaders = 0;

    int n_threads = sysconf(_SC_NPROCESSORS_ONLN);

    int sockfd = initServer();
    ThreadPool pool(n_threads);
    printf("mieMT server started!\n");
    while (!aShutdown) {
        struct sockaddr_in cli_addr;
        socklen_t clilen = sizeof(cli_addr);
        int newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);
        pool.enqueue(clientThread, newsockfd);
    }
}

void MIEServerMT::clientThread(int newsockfd) {
    if (newsockfd < 0){
        error("ERROR on accept");
        return;
    }
    char buffer[1];
    bzero(buffer,1);
    aRequests++;
    if (read(newsockfd,buffer,1) < 0){
        error("ERROR reading from socket");
        goto end;
    }
    if(aTerminate){
        char status = OP_FAILED;
        socketSend(newsockfd, &status, sizeof(char));
        goto end;
    }
    //printf("received %c cmd",buffer[0]);
    switch (buffer[0]) {
        case 'a':
            addDoc(newsockfd);
            break;
        case 'i':
            index(newsockfd);
            break;
        case 's':
            search(newsockfd);
            break;
        case 'g':
            sendDoc(newsockfd);
            break;
        case 'p':
            printTimes(newsockfd);
            break;
        case 'c':
            clear(newsockfd);
            break;
        case 'r':
            resetCache(newsockfd);
            break;
        default:
            warning("unkonwn command!\n");
    }
    end:
    unique_lock<mutex> tmp(aTerminateLock);
    aRequests--;
    if(0 == aRequests && aTerminate)
        aNoRequests.notify_all();
    //ack(newsockfd);
    close(newsockfd);
}

void MIEServerMT::addDoc(int newsockfd)
{    
    shared_ptr<char> data;
    string name;
    int pos = receiveDoc(newsockfd, data, &name);
    int cipher_size = readIntFromArr(data.get(), &pos);
    aNetworkAddTimeLock.lock();
    timespec start = getTime();
    if(0 == aUploaders){
        aNetworkAddStart = start; ///network time
    }
    aUploaders++;
    aNetworkAddTimeLock.unlock();
    storage->write(name+".data", data.get() + pos, cipher_size);
    timespec end = getTime();
    aNetworkAddTimeLock.lock();
    double time_parallel = diffSec(aNetworkAddStart, end);
    double time_total = diffSec(start, end);
    aNetworkAddTotalTime += time_total;
    aNetworkAddParallelTime += time_parallel;
    aNetworkAddStart = end;
    aUploaders--;
    aNetworkAddTimeLock.unlock();
    pos += cipher_size;
    mie->addDoc(data.get() + pos, name);
}

void MIEServerMT::index(int newsockfd)
{
    char status = NO_ERRORS;
    char buff[1];
    socketReceive(newsockfd, buff, 1);
    socketSend(newsockfd, &status, sizeof(char));
    if('f' == buff[0])
        mie->index(true);
    else
        mie->index();
    printf("finished indexing!\n");
}

void MIEServerMT::search(int newsockfd)
{
    shared_ptr<char> data;
    int pos = receiveDoc(newsockfd, data);
    size_t n_results = 0;
    set<QueryResult,cmp_QueryResult> mergedResults = mie->search(data.get() + pos, n_results);
    sendQueryResponse(newsockfd, mergedResults, n_results);
}

int MIEServerMT::receiveDoc(int newsockfd, shared_ptr<char>& data, string* name)
{
    //receive and unzip data
    char buff[2*sizeof(uint64_t)];
    socketReceive(newsockfd, buff, 2*sizeof(uint64_t));
    unsigned long zipSize, dataSize;
    memcpy(&zipSize, buff, sizeof(uint64_t));
    memcpy(&dataSize, buff + sizeof(uint64_t), sizeof(uint64_t));
    zipSize = be64toh(zipSize);
    dataSize = be64toh(dataSize);
    data = shared_ptr<char>(new char[dataSize], default_delete<char[]>());
    receiveAndUnzip(newsockfd, data.get(), &dataSize, zipSize);
    char status = NO_ERRORS;
    socketSend(newsockfd, &status, sizeof(char));
    int pos = 0;
    if(NULL != name){
        const int name_size = readIntFromArr(data.get(), &pos);
        name->assign(data.get() + pos, name_size);
        pos += name_size;
    }
    return pos;
}

void MIEServerMT::sendQueryResponse(int newsockfd, std::set<QueryResult,cmp_QueryResult>& mergedResults, 
    size_t resultsSize)
{
    if (0 == resultsSize)
        resultsSize = aResultsSize;
    if (mergedResults.size() < resultsSize)
        resultsSize = (int)mergedResults.size();
    int totalNameSize = 0;
    for (std::set<QueryResult,cmp_QueryResult>::iterator it = mergedResults.begin(); it != 
        mergedResults.end(); ++it) {
        totalNameSize += it->docId.size();
    }
    long size = 2*sizeof(int) + totalNameSize + resultsSize * (sizeof(int) + sizeof(uint64_t));
    unique_ptr<char[]> buffer(new char[size]);
    int pos = 0;
    addIntToArr (resultsSize, buffer.get(), &pos);
    addIntToArr (size - 2*sizeof(int), buffer.get(), &pos);
    size_t i = 1;
    for (std::set<QueryResult,cmp_QueryResult>::iterator it = mergedResults.begin(); it != 
        mergedResults.end(); ++it) {
        double score = it->score;
        addIntToArr(it->docId.size(), buffer.get(), &pos);
        addToArr((void*)it->docId.c_str(), it->docId.size(), buffer.get(), &pos);
        addDoubleToArr(score, buffer.get(), &pos);
        if (i == resultsSize)
            break;
        else
            i++;
    }
    socketSend (newsockfd, buffer.get(), size);
}

void MIEServerMT::sendDoc(int newsockfd){
    char buff[1 + sizeof(uint32_t)];
    socketReceive(newsockfd, buff, 1 + sizeof(uint32_t));
    int pos = 1;
    bool use_cache = true;
    if('f' == buff[1])
        use_cache = false;
    int name_size = readIntFromArr(buff, &pos);
    unique_ptr<char[]> name_buffer(new char[name_size]);
    socketReceive(newsockfd, name_buffer.get(), name_size);
    
    string name(name_buffer.get(), name_size);
    name += ".data";

    vector<char> buffer;
    aNetworkGetTimeLock.lock();
    timespec start = getTime();
    if(0 == aDownloaders){
        aNetworkGetStart = start;
    }
    aDownloaders++;
    aNetworkGetTimeLock.unlock();
    int size = 0;
    if(use_cache)
        size = storage->read(name, buffer);
    else
        size = storage->forceRead(name, buffer);
    timespec end = getTime();
    aNetworkGetTimeLock.lock();
    double time_parallel = diffSec(aNetworkGetStart, end);
    double time_total = diffSec(start, end);
    aNetworkGetTotalTime += time_total;
    aNetworkGetParallelTime += time_parallel;
    aNetworkGetStart = end;
    aDownloaders--;
    aNetworkGetTimeLock.unlock();
    ///send data
    if(0 <= size){
        char status = NO_ERRORS;
        socketSend(newsockfd, &status, sizeof(char));
        zipAndSend(newsockfd, buffer.data(), size);
    }
    else{
        char status = OP_FAILED;
        socketSend(newsockfd, &status, sizeof(char));
    }
}

void MIEServerMT::printTimes(int newsockfd)
{
    double net_total_up = storage->getTotalNetUp();
    double net_total_down = storage->getTotalNetDown();
    double net_parallel_up = storage->getParallelNetUp();
    double net_parallel_down = storage->getParallelNetDown();
    double network_feature_time = mie->networkFeatureTime();
    double network_index_time = mie->networkIndexTime();
    //printf("%lu %.6f %lu %.6f\n", nu, netUp, nd, netDown);
    double network_time = network_feature_time + network_index_time + aNetworkAddParallelTime + aNetworkGetParallelTime;
    char* buffer;
    asprintf(&buffer, 
    "Index time: %.6f\nTrain time: %.6f\nSearch time: %.6f\nNetwork time: %.6f\nNetwork feature time: %.6f\nNetwork index time: %.6f\nNetwork add time: %.6f\nNetwork get time: %.6f\nNetwork parallel add: %.6f\nNetwork parallel get: %.6f\nNetwork upload time: %.6f\nNetwork download time: %.6f\nNetwork parallel upload: %.6f\nNetwork parallel download: %.6f\nHit Ratio: %.6f\n",
    mie->indexTime(), mie->trainTime(), mie->searchTime(), network_time, network_feature_time, 
    network_index_time, aNetworkAddTotalTime, aNetworkGetTotalTime, aNetworkAddParallelTime, 
    aNetworkGetParallelTime, net_total_up, net_total_down, net_parallel_up, net_parallel_down,
    storage->getHitRatio());
    printf("%s", buffer);
    size_t size = strlen(buffer);
    //printf("Sending %lu\n", size);
    char b[sizeof(int)];
    int pos = 0;
    addIntToArr((int)size, b, &pos);
    char status = NO_ERRORS;
    socketSend(newsockfd, &status, sizeof(char));
    zipAndSend(newsockfd, buffer, size);
    free(buffer);
}

void MIEServerMT::clear(int newsockfd)
{
    char status = NO_ERRORS;
    socketSend(newsockfd, &status, sizeof(char));
    unique_lock<mutex> add_lock(aNetworkAddTimeLock);
    aNetworkAddTotalTime = 0;
    aNetworkAddParallelTime = 0;
    add_lock.unlock();
    unique_lock<mutex> get_lock(aNetworkGetTimeLock);
    aNetworkGetTotalTime = 0;
    aNetworkGetParallelTime = 0;
    get_lock.unlock();
    storage->resetTimes();
    mie->resetTimes();
}

void MIEServerMT::resetCache(int newsockfd)
{
    char status = NO_ERRORS;
    socketSend(newsockfd, &status, sizeof(char));
    storage->resetCache();
}

}//end namespace