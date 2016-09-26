#ifndef MIE_MEMCACHED_H
#define MIE_MEMCACHED_H

#include <queue>
#include <mutex>
#include <thread>
#include <condition_variable>
#include "Cache.h"
#include "Status.h"

namespace MIE::CACHE{

class MemcachedClient : public MIE::Cache{
    class MemcachedConnection;
    unsigned int aActiveConnections;
    unsigned int aMaxConnections;
    unsigned int aExpireTime;
    double aTotalReads;
    double aTotalHits;
    std::mutex aStatsLock;
    std::queue<int> aConnections;
    std::recursive_mutex aConnectionsLock;
    std::condition_variable_any aNoConnections;
    std::string aMemcachedHost;
    std::string aMemcachedPort;

    int getConnection();
    void releaseConnection(int connection);
    unsigned int openConnections(unsigned int numberOfConnections);

  public:
    int read(const std::string& name, std::vector<char>& buffer);
    int write(const std::string& name, const char* buffer, size_t bufferSize);
    int remove(const std::string& name);
    int clear();
    double getHitRatio();
    void resetStats();
    MemcachedClient(unsigned int maxConnections = 0, bool open = false);
    ~MemcachedClient();
};

}//end namespace mie::mie_cache
#endif