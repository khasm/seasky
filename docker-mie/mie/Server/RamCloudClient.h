#ifndef RAMCLOUD_CLIENT_H
#define RAMCLOUD_CLIENT_H

#include <vector>
#include "RamCloud.h"
#include "Status.h"

namespace MIE::RC{

class RamCloudClient{
    struct RamCloudMetadata{
        unsigned int n_chunks;
        size_t original_size;
    };
    
    RamCloudMetadata readMetadata(RAMCloud::Buffer& buffer);
    int readMultipleSegments(const uint64_t tableId, const std::string& filename,
        std::vector<char>&buffer, const RAMCloud::MultiReadObject& metadataBuffer);
    int writeMultipleSegments(const uint64_t tableId, const std::string& filename, const char* buffer,
        size_t bufferSize, RamCloudMetadata& oldMetadata, RAMCloud::ReadRpc* readRpc,
        RAMCloud::Buffer& readBuffer);
    void removeMultipleSegments(const uint64_t tableId, const std::string& filename,
        const unsigned int firstSegment, const unsigned int numberOfSegments);
  public:
    bool isValid();
    int createTable(const std::string& name, const int split, uint64_t& tableId);
    int getTableId(const std::string& name, uint64_t& tableId);
    int read(const uint64_t tableId, const std::string& filename, std::vector<char>& readBuffer);
    int write(const uint64_t tableId, const std::string& filename, const char* buffer,
        const size_t bufferSize);
    int remove(const uint64_t tableId, const std::string& filename);

    RamCloudClient(){};
    RamCloudClient(std::string host, std::string port, std::string cluster);
    RamCloudClient(const RamCloudClient& other);
    ~RamCloudClient(){};
    RamCloudClient& operator=(RamCloudClient other);

    std::shared_ptr<RAMCloud::RamCloud> aClient;

    friend void swap(RamCloudClient& first, RamCloudClient& second);
};

}//end namespace mie::mie_rc
#endif