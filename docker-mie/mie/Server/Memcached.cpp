#include <sys/socket.h>
#include <netdb.h>
#include <memory>
#include "Memcached.h"
#include "ServerUtil.h"
#include "Config.h"

namespace MIE::CACHE{

using namespace std;

enum MEMCACHED_CODES{
    ///magic bytes
    MB_REQ                      = 0x80,
    MB_ANS                      = 0x81,
    ///answer codes
    ANS_NOERROR                 = 0x0000,
    ANS_NOTFOUND                = 0x0001,
    ANS_KEYEXISTS               = 0x0002,
    ANS_TOOLARGE                = 0x0003,
    ANS_INVALIDARGS             = 0x0004,
    ANS_ITEMNOTSTORED           = 0x0005,
    ANS_INCDECVALUE             = 0x0006,
    ANS_UNKCOM                  = 0x0081,
    ANS_NOMEMORY                = 0x0082,
    ///request commands
    REQ_GET                     = 0x00,
    REQ_SET                     = 0x01,
    REQ_ADD                     = 0x02,
    REQ_REPLACE                 = 0x03,
    REQ_DEL                     = 0x04,
    REQ_INC                     = 0x05,
    REQ_DEC                     = 0X06,
    REQ_QUIT                    = 0X07,
    REQ_FLUSH                   = 0X08,
    REQ_GETQ                    = 0X09,
    REQ_NOOP                    = 0X0A,
    REQ_VER                     = 0X0B,
    REQ_GETK                    = 0X0C,
    REQ_GETKQ                   = 0X0D,
    REQ_APPEND                  = 0X0E,
    REQ_PREPEND                 = 0X0F,
    REQ_STAT                    = 0X10,
    REQ_SETQ                    = 0X11,
    REQ_ADDQ                    = 0X12,
    REQ_REPLACEQ                = 0X13,
    REQ_DELQ                    = 0X14,
    REQ_INCQ                    = 0X15,
    REQ_DECQ                    = 0X16,
    REQ_QUITQ                   = 0X17,
    REQ_FLUSHQ                  = 0X18,
    REQ_APPENDQ                 = 0X19,
    REQ_PREPENDQ                = 0X1A,
    ///data types
    TYPE_RAW                    = 0x00,
};

enum MEMCACHED_CONSTS{
    MAGIC                           = 0,
    OPCODE                          = 1,
    STATUS                          = 7, //its actually 6-7, but 6 is never used
    EXTRAS_LENGTH_OFFSET            = 4,
    EXTRAS_LENGTH_SIZE              = 1,
    HEADER_SIZE                     = 24,
    FILENAME_LENGTH_OFFSET          = 2,
    FILENAME_LENGTH_SIZE            = 2,
    BODY_LENGTH_OFFSET              = 8,
    BODY_LENGTH_SIZE                = 4,
    WRITE_EXTRAS_LENGTH             = 8,
    REMOVE_EXTRAS_LENGTH            = 4,
    REQUEST_EXTRAS_LENGTH_OFFSET    = 4,
    EXPIRE_TIME_OFFSET              = 28,
    EXPIRE_TIME_SIZE                = 4,
};

class MemcachedClient::MemcachedConnection{
    MemcachedClient* client;
    int sockfd;
  public:
    const int getSocket();
    MemcachedConnection(MemcachedClient* inClient);
    ~MemcachedConnection();
};

MemcachedClient::MemcachedConnection::MemcachedConnection(MemcachedClient* inClient) : client(inClient)
{
    sockfd = client->getConnection();
}

MemcachedClient::MemcachedConnection::~MemcachedConnection()
{
    client->releaseConnection(sockfd);
}

const int MemcachedClient::MemcachedConnection::getSocket()
{
    return sockfd;
}

MemcachedClient::MemcachedClient(unsigned int maxConnections, bool open) : aActiveConnections(0),
    aMaxConnections(maxConnections), aTotalReads(0), aTotalHits(0)
{
    aMemcachedHost = getMemcachedHost();
    aMemcachedPort = getMemcachedPort();
    aExpireTime = getExpireTime();
    if(open)
        openConnections(aMaxConnections);
}

MemcachedClient::~MemcachedClient()
{
    
}

unsigned int MemcachedClient::openConnections(unsigned int numberOfConnections){
    unsigned int opened = 0;
    struct addrinfo *host = NULL;
    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;
    int status = getaddrinfo(aMemcachedHost.c_str(), aMemcachedPort.c_str(), &hints, &host);
    if(status != 0){
        error("Error getting memcached host info in MemcachedClient::openConnections");
    }
    else{
        //try to lock, if it cant we assume caller already owns the lock
        unique_lock<recursive_mutex> tmp(aConnectionsLock, try_to_lock);
        for(unsigned int i = 0; i < numberOfConnections; i++){
            int sockfd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
            if(sockfd < 0){
                error("Error opening socket in MemcachedClient::openConnections");
                continue;
            }
            else if(connect(sockfd, host->ai_addr, (int)host->ai_addrlen) < 0){
                error("Error connecting to memcached server in MemcachedClient::openConnections");
                continue;
            }
            aConnections.push(sockfd);
            opened++;
        }
        aActiveConnections += opened;
    }
    return opened;
}

int MemcachedClient::getConnection()
{
    unique_lock<recursive_mutex> tmp(aConnectionsLock);
    while(aConnections.empty()){
        int available = 0;
        if(aActiveConnections < aMaxConnections || aMaxConnections == 0)
            available = openConnections(1);
        if(available <= 0)
            aNoConnections.wait(tmp);
    }
    int c = aConnections.front();
    aConnections.pop();
    return c;
}

void MemcachedClient::releaseConnection(int connection)
{
    unique_lock<recursive_mutex> tmp(aConnectionsLock);
    aConnections.push(connection);
    aNoConnections.notify_one();
}

int MemcachedClient::read(const std::string& name, std::vector<char>& buffer){
    unique_lock<mutex> stats_lock(aStatsLock);
    aTotalReads++;
    stats_lock.unlock();
    const int fns = name.length();
    const int req_size = HEADER_SIZE+fns;
    const unique_ptr<char[]> req_buffer(new char[req_size]());
    ///setup buffer bytes for request
    req_buffer[MAGIC] = MB_REQ;
    req_buffer[OPCODE] = REQ_GET;
    for(int i = 0; i < FILENAME_LENGTH_SIZE; i++)
        req_buffer[FILENAME_LENGTH_OFFSET + i] = *(((char*)&fns) + 1 - i);
    /*req_buffer[2] = *(((char*)&fns) + 1);
    req_buffer[3] = *(((char*)&fns));*/
    for(int i = 0; i < BODY_LENGTH_SIZE; i++)
        req_buffer[BODY_LENGTH_OFFSET + i] = *(((char*)&fns) + 3 - i);
    for(int i = 0; i < fns; i++)
        req_buffer[HEADER_SIZE + i] = name[i];
    ///send request
    MemcachedConnection tmp(this);
    const int sockfd = tmp.getSocket();
    int socket_processed = 0;
    int n;
    while(socket_processed < req_size){
        n = send(sockfd, req_buffer.get() + socket_processed, req_size - socket_processed, 0);
        if(-1 == n){
            error("MemcachedClient: Error sending read request");
            return NETWORK_ERROR;
        }
        socket_processed += n;
    }

    ///receive answer header
    unsigned char ans_header[HEADER_SIZE];
    memset(ans_header, 0, HEADER_SIZE);
    socket_processed = 0;
    while(socket_processed < HEADER_SIZE){
        n = recv(sockfd, ans_header + socket_processed, HEADER_SIZE - socket_processed,
            0);
        if(-1 == n){
            error("MemcachedClient: Error receiving read answer header");
            return NETWORK_ERROR;
        }
        socket_processed += n;
    }
    if(ans_header[MAGIC] != MB_ANS){
        error("MemcachedClient: Unrecognized magic byte in read answer header");
        return MEMCACHED_ERROR;
    }
    ///get total length
    int total = 0;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++){
        *(((char*)&total) + i) = ans_header[BODY_LENGTH_OFFSET + 3 - i];
    }

    if(ans_header[STATUS] != ANS_NOERROR){
        ///there was an error
        if(ans_header[STATUS] == ANS_NOTFOUND){
            ///clear socket
            const unique_ptr<char[]> garbage_collector(new char[total]);
            socket_processed = 0;
            while(socket_processed < total){
                n = recv(sockfd, garbage_collector.get(), total, 0);
                if(-1 == n){
                    error("MemcachedClient: Error flushing socket on read");
                    return NOT_FOUND;
                }
                socket_processed+=n;
            }
        }
        else{
            warning("MemcachedClient: Unhandled error on read. Ignored");
            return NOT_IMPLEMENTED;
        }
    }
    else{
        ///the file was found on the cache
        ///get extras length
        int extras = 0;
        memcpy(&extras, ans_header+EXTRAS_LENGTH_OFFSET, EXTRAS_LENGTH_SIZE);
        const unique_ptr<char[]> ans_body(new char[total]);
        socket_processed = 0;
        while(socket_processed < total){
            n = recv(sockfd, ans_body.get()+socket_processed, total-socket_processed, 0);
            if(-1 == n){
                error("MemcachedClient: Error receiving read answer body");
                return NETWORK_ERROR;
            }
            socket_processed += n;
        }
        ///alocate buffer for value
        const int value_size = total-extras;
        buffer.reserve(value_size);
        buffer.insert(buffer.begin(), ans_body.get()+extras, ans_body.get()+total);
        stats_lock.lock();
        aTotalHits++;
        return value_size;
    }
    return NOT_FOUND;
}

int MemcachedClient::write(const std::string& name, const char* buffer, size_t bufferSize)
{
    const int fns = name.length();
    ///filename size+header size+extras size+value size
    const int req_size = fns + HEADER_SIZE + WRITE_EXTRAS_LENGTH + bufferSize;
    const unique_ptr<char[]> req_buffer (new char[req_size]());
    ///setup buffer bytes for request
    req_buffer[MAGIC] = MB_REQ;
    req_buffer[OPCODE] = REQ_SET;
    for(int i = 0; i < FILENAME_LENGTH_SIZE; i++)
        req_buffer[FILENAME_LENGTH_OFFSET + i] = *(((char*)&fns) + 1 - i);
    /*req_buffer[2] = *(((char*)&fns) + 1);
    req_buffer[3] = *(((char*)&fns));*/
    req_buffer[REQUEST_EXTRAS_LENGTH_OFFSET] = WRITE_EXTRAS_LENGTH;
    const int body_length = WRITE_EXTRAS_LENGTH + bufferSize + fns;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++)
        req_buffer[BODY_LENGTH_OFFSET + i] = *(((char*)&body_length) + 3 - i);
    for(int i = 0; i < EXPIRE_TIME_SIZE; i++)
        req_buffer[EXPIRE_TIME_OFFSET + i] = *(((char*)&aExpireTime) + 3 - i);
    for(int i = 0; i < fns; i++){
        req_buffer[HEADER_SIZE + WRITE_EXTRAS_LENGTH + i] = name[i];
    }
    memcpy(req_buffer.get() + HEADER_SIZE + WRITE_EXTRAS_LENGTH + fns, buffer, bufferSize);
    ///send request
    MemcachedConnection tmp(this);
    const int sockfd = tmp.getSocket();
    int socket_processed = 0;
    int n;
    while(socket_processed < req_size){
        n = send(sockfd, req_buffer.get() + socket_processed, req_size - socket_processed, 0);
        if(-1 == n){
            error("MemcachedClient: Error sending write request");
            return NETWORK_ERROR;
        }
        socket_processed+=n;
    }
    ///receive reply
    unsigned char ans_header[HEADER_SIZE];
    memset(ans_header, 0, HEADER_SIZE);
    socket_processed = 0;
    while(socket_processed < HEADER_SIZE){
        n = recv(sockfd, ans_header + socket_processed, HEADER_SIZE - socket_processed, 0);
        if(-1 == n){
            error("MemcachedClient: Error receiving write answer header");
            return NETWORK_ERROR;
        }
        socket_processed += n;
    }
    if(ans_header[MAGIC] != MB_ANS){
        error("MemcachedClient: Unrecognized magic byte in write answer header");
        return MEMCACHED_ERROR;
    }
    ///get total length
    int total = 0;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++){
        *(((char*)&total)+i) = ans_header[BODY_LENGTH_OFFSET + 3 - i];
    }
    if(ans_header[STATUS] != ANS_NOERROR){
        ///there was an error
        if(ans_header[STATUS] == ANS_TOOLARGE){
            ///too large to fit on cache
            ///clear socket
            const unique_ptr<char[]> garbage_collector(new char[total]);
            socket_processed = 0;
            while(socket_processed < total){
                n = recv(sockfd, garbage_collector.get(), total, 0);
                if(-1 == n){
                    error("MemcachedClient: Error flushing socket on write");
                    return NETWORK_ERROR;
                }
                socket_processed+=n;
            }
        }
        else
            warning("MemcachedClient: Unhandled error on write. Ignored");
        return OP_FAILED;
    }
    else{
        return NO_ERRORS;
    }
}

int MemcachedClient::remove(const string& filename){
    ///allocate buffer for request
    const int fns = filename.length();
    const int req_size = fns+HEADER_SIZE; ///header + key length
    unique_ptr<char[]> req_buffer(new char[req_size]());
    ///setup buffer header bytes
    req_buffer[MAGIC] = MB_REQ;
    req_buffer[OPCODE] = REQ_DEL;
    for(int i = 0; i < FILENAME_LENGTH_SIZE; i++)
        req_buffer[FILENAME_LENGTH_OFFSET + i] = *(((char*)&fns) + 1 - i);
    /*req_buffer[2] = *(((char*)&fns) + 1);
    req_buffer[3] = *(((char*)&fns));*/
    for(int i = 0; i < BODY_LENGTH_SIZE; i++)
        req_buffer[BODY_LENGTH_OFFSET + i] = *(((char*)&fns) + 3 - i);
    for(int i = 0; i < fns; i++){
        req_buffer[HEADER_SIZE + i] = filename[i];
    }
    ///send request
    MemcachedConnection tmp(this);
    const int sockfd = tmp.getSocket();
    int socket_processed = 0;
    int n;
    while(socket_processed < req_size){
        n = send(sockfd, req_buffer.get()+socket_processed, req_size-socket_processed, 0);
        if(n == -1){
            error("MemcachedClient: Error sending remove request");
            return NETWORK_ERROR;
        }
        socket_processed+=n;
    }
    ///receive reply
    unsigned char ans_header[HEADER_SIZE];
    memset(ans_header, 0, HEADER_SIZE);
    socket_processed = 0;
    while(socket_processed < HEADER_SIZE){
        n = recv(sockfd, ans_header+socket_processed, HEADER_SIZE-socket_processed, 0);
        if(n == -1){
            error("MemcachedClient: Error receiving remove answer header");
            return NETWORK_ERROR;
        }
        socket_processed+=n;
    }
    if(ans_header[MAGIC] != MB_ANS){
        error("MemcachedClient: Unrecognized magic byte in remove answer header");
        return MEMCACHED_ERROR;
    }
    ///get total length
    int total = 0;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++){
        *(((char*)&total)+i) = ans_header[BODY_LENGTH_OFFSET + 3 - i];
    }
    if(ans_header[STATUS] != ANS_NOERROR){
        ///ignore not found errors on delete request
        if(ans_header[STATUS] != ANS_NOTFOUND){
            error("MemcachedClient: Unhandled error on remove. Ignored");
            return MEMCACHED_ERROR;
        }
        else{
            ///clear socket
            unique_ptr<char[]> garbage_collector (new char[total]);
            socket_processed = 0;
            while(socket_processed < total){
                n = recv(sockfd, garbage_collector.get(), total - socket_processed, 0);
                if(-1 == n){
                    error("MemcachedClient: Error flushing socket on remove");
                    return NETWORK_ERROR;
                }
                socket_processed+=n;
            }
        }
    }
    return NO_ERRORS;
}

int MemcachedClient::clear()
{
    ///allocate buffer for request
    const int req_size = HEADER_SIZE+EXPIRE_TIME_SIZE; ///header + key length
    unique_ptr<char[]> req_buffer(new char[req_size]());
    ///setup buffer header bytes
    req_buffer[MAGIC] = MB_REQ;
    req_buffer[OPCODE] = REQ_FLUSH;
    req_buffer[REQUEST_EXTRAS_LENGTH_OFFSET] = REMOVE_EXTRAS_LENGTH;
    const int body_length = REMOVE_EXTRAS_LENGTH;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++)
        req_buffer[BODY_LENGTH_OFFSET + i] = *(((char*)&body_length) + 3 - i);
    //not setting expire time as we intend to flush now and request header is zero'd
    ///send request
    MemcachedConnection tmp(this);
    const int sockfd = tmp.getSocket();
    int socket_processed = 0;
    int n;
    while(socket_processed < req_size){
        n = send(sockfd, req_buffer.get()+socket_processed, req_size-socket_processed, 0);
        if(n == -1){
            error("MemcachedClient: Error sending clear request");
            return NETWORK_ERROR;
        }
        socket_processed+=n;
    }
    ///receive reply
    unsigned char ans_header[HEADER_SIZE];
    memset(ans_header, 0, HEADER_SIZE);
    socket_processed = 0;
    while(socket_processed < HEADER_SIZE){
        n = recv(sockfd, ans_header+socket_processed, HEADER_SIZE-socket_processed, 0);
        if(n == -1){
            error("MemcachedClient: Error receiving remove clear header");
            return NETWORK_ERROR;
        }
        socket_processed+=n;
    }
    if(ans_header[MAGIC] != MB_ANS){
        error("MemcachedClient: Unrecognized magic byte in clear answer header");
        return MEMCACHED_ERROR;
    }
    ///get total length
    int total = 0;
    for(int i = 0; i < BODY_LENGTH_SIZE; i++){
        *(((char*)&total)+i) = ans_header[BODY_LENGTH_OFFSET + 3 - i];
    }
    if(ans_header[STATUS] != ANS_NOERROR){
        error("MemcachedClient: Unhandled error on clear. Ignored");
        return MEMCACHED_ERROR;
    }
    return NO_ERRORS;
}

double MemcachedClient::getHitRatio()
{
    unique_lock<mutex> tmp(aStatsLock);
    return aTotalHits / aTotalReads * 100;
}

void MemcachedClient::resetStats()
{
    unique_lock<mutex> tmp(aStatsLock);
    aTotalHits = 0;
    aTotalReads = 0;
}

}//end namespace mie:: mie_cache