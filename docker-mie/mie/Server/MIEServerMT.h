//
//  Server.h
//  MIE
//
//  Created by Bernardo Ferreira on 02/05/15.
//  Copyright (c) 2015 NovaSYS. All rights reserved.
//

#ifndef __MIE__MIEServerMT__
#define __MIE__MIEServerMT__

#include <vector>
#include <map>
#include <set>
#include <iostream>
#include <fstream>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include "Server.h"
#include "ServerUtil.h"
#include "ThreadPool.h"
#include <mutex>

#include "Storage.h"
#include "Mie.h"

namespace MIE{

class MIEServerMT : public Server {

    //statistics
    static double aNetworkAddTotalTime;
    static double aNetworkAddParallelTime;
    static timespec aNetworkAddStart;
    static unsigned aUploaders;
    static std::mutex aNetworkAddTimeLock;
    static double aNetworkGetTotalTime;
    static double aNetworkGetParallelTime;
    static timespec aNetworkGetStart;
    static unsigned aDownloaders;
    static std::mutex aNetworkGetTimeLock;
    
    static MIE* mie;
    static Storage* storage;
    static size_t aResultsSize;
    static bool aTerminate;
    static bool aShutdown;
    static std::atomic<unsigned> aRequests;
    static std::mutex aTerminateLock;
    static std::condition_variable aNoRequests;
    
    void startServer();
    static void clientThread(int newsockfd);
    static int receiveDoc(int newsockfd, std::shared_ptr<char>& data, std::string* name = NULL);
    static void sendQueryResponse(int newsockfd, std::set<QueryResult,cmp_QueryResult>& mergedResults, 
        size_t resultsSize);
    static void addDoc(int newsockfd);
    static void index(int newsockfd);
    static void search(int newsockfd);
    static void sendDoc(int newsockfd);
    static void printTimes(int newsockfd);
    static void clear(int newsockfd);
    static void resetCache(int newsockfd);
    
public:
    MIEServerMT(int backend, bool cache = true, int model = 0);
    ~MIEServerMT();
    
};

}//end namespace
#endif
