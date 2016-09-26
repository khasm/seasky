#include <iostream>
#include "RamCloudClient.h"
#include "MultiWrite.h"
#include "ServerUtil.h"

namespace MIE::RC{
using namespace RAMCloud;
using namespace std;

const char *RAMCLOUD_NAME_FORMAT = "%s.%d.mieramcloud";
const unsigned int MAX_RAMCLOUD_OBJECT_SIZE  = 1048576; //1MB

RamCloudClient::RamCloudClient(string host, string port, string cluster)
{
    try{
        aClient = make_shared<RamCloud>(("zk:"+host+":"+port).c_str(), cluster.c_str());
    } 
    catch(Exception e){}
}

RamCloudClient::RamCloudClient(const RamCloudClient& other)
{
    aClient = other.aClient;
}

RamCloudClient& RamCloudClient::operator=(RamCloudClient other)
{
    swap(*this, other);
    return *this;
}

void swap(RamCloudClient& first, RamCloudClient& second)
{
    using std::swap;
    swap(first.aClient, second.aClient);
}

RamCloudClient::RamCloudMetadata RamCloudClient::readMetadata(Buffer& buffer)
{
    RamCloudMetadata metadata;
    if(buffer.size() >= sizeof(unsigned int) + sizeof(size_t)){
        metadata.n_chunks = *(buffer.getOffset<unsigned int>(0));
        metadata.original_size = *(buffer.getOffset<size_t>
            (sizeof(unsigned int)));
    }
    else{
        metadata.n_chunks = 0; //minimum legal value is 2
        metadata.original_size = 0; //ignored if n_chunks is 0
    }
    return metadata;
}

bool RamCloudClient::isValid()
{
    return aClient != nullptr;
}

int RamCloudClient::createTable(const string& name, const int split, uint64_t& tableId)
{
    try{
        tableId = aClient->createTable(name.c_str(), split);
        return NO_ERRORS;
    }
    catch(ClientException& e){
        return RAMCLOUD_EXCEPTION;
    }
}

int RamCloudClient::getTableId(const string& name, uint64_t& tableId)
{
    try{
        try{
            tableId = aClient->getTableId(name.c_str());
            return NO_ERRORS;
        }
        catch(TableDoesntExistException e){
            return OP_FAILED;
        }
    }
    catch(ClientException& e){
        return RAMCLOUD_EXCEPTION;
    }
}

int RamCloudClient::read(const uint64_t tableId, const string& filename, vector<char>& readBuffer)
{
    if(!aClient)
        return INVALID_STATE;
    int status = NOT_FOUND;
    try{
        try{
            MultiReadObject* requests[2];
            unique_ptr<Tub<ObjectBuffer>> tmp_buffers[2];
            unique_ptr<MultiReadObject> tmp_requests[2];
            unique_ptr<char[]> tmp_names[2];
            for(unsigned int i = 0; i < 2; i++){
                tmp_names[i] = unique_ptr<char[]>(new char[200]);
                memset(tmp_names[i].get(), 0, 200);
                if(0 == i){
                    sprintf(tmp_names[i].get(), "%s", filename.c_str());    
                }
                else{
                    sprintf(tmp_names[i].get(), RAMCLOUD_NAME_FORMAT, filename.c_str(), 0);
                }
                int ns = strlen(tmp_names[i].get());
                tmp_buffers[i] = unique_ptr<Tub<ObjectBuffer>>(new Tub<ObjectBuffer>());
                tmp_requests[i] = unique_ptr<MultiReadObject>(new MultiReadObject(tableId,
                    tmp_names[i].get(), ns, tmp_buffers[i].get()));
                requests[i] = tmp_requests[i].get();
            }
            aClient->multiRead(requests, 2);
            if(*requests[0]->value){
                uint32_t size;
                const void* value = requests[0]->value->get()->getValue(&size);
                readBuffer.reserve(size);
                readBuffer.insert(readBuffer.end(), (char*)value, (char*)value+size);
                status = size;
            }
            else if(*requests[1]->value){
                status = readMultipleSegments(tableId, filename, readBuffer, *requests[1]);
            }
            /*Buffer buffer;
            aClient->read(tableId, filename.c_str(), filename.size(), &buffer);
            int size = buffer.size();
            #ifdef MIE_DEBUG
            printf("read %s %ld %d\n", filename.c_str(), filename.size(), size);
            #endif
            readBuffer.resize(size);
            buffer.copy(0, size, readBuffer.data());
            status = size;*/
        }
        catch(ObjectDoesntExistException e){
            //status = readMultipleSegments(tableId, filename, readBuffer);
        }
    }
    catch(ClientException& e){
        status = RAMCLOUD_EXCEPTION;
    }
    return status;
}

int RamCloudClient::readMultipleSegments(const uint64_t tableId, const string& filename,
    vector<char>& readBuffer, const MultiReadObject& metadataBuffer)
{
    int status = NOT_FOUND;
    try{
        //read auxiliary file
        /*Buffer buffer;
        char name[200];
        sprintf(name, RAMCLOUD_NAME_FORMAT, filename.c_str(), 0);
        int ns = strlen(name);
        aClient->read(tableId, name, ns, &buffer);
        RamCloudMetadata metadata = readMetadata(buffer);*/
        uint32_t size;
        const void* value = metadataBuffer.value->get()->getValue(&size);

        RamCloudMetadata metadata;
        if(size >= sizeof(unsigned int) + sizeof(size_t)){
            int pos = 0;
            readFromArr(&metadata.n_chunks, sizeof(unsigned), (const char*)value, &pos);
            readFromArr(&metadata.original_size, sizeof(size_t), (const char*)value, &pos);
        }
        else{
            metadata.n_chunks = 0; //minimum legal value is 2
            metadata.original_size = 0; //ignored if n_chunks is 0
        }

        //read all chunks of the file
        MultiReadObject* requests[metadata.n_chunks];
        unique_ptr<Tub<ObjectBuffer>> tmp_buffers[metadata.n_chunks];
        unique_ptr<MultiReadObject> tmp_requests[metadata.n_chunks];
        unique_ptr<char[]> tmp_names[metadata.n_chunks];
        for(unsigned int i = 0; i< metadata.n_chunks; i++){
            tmp_names[i] = unique_ptr<char[]>(new char[200]);
            memset(tmp_names[i].get(), 0, 200);
            sprintf(tmp_names[i].get(), RAMCLOUD_NAME_FORMAT, filename.c_str(), i+1);
            int ns = strlen(tmp_names[i].get());
            tmp_buffers[i] = unique_ptr<Tub<ObjectBuffer>>(new Tub<ObjectBuffer>());
            tmp_requests[i] = unique_ptr<MultiReadObject>(new MultiReadObject(tableId,
                tmp_names[i].get(), ns, tmp_buffers[i].get()));
            requests[i] = tmp_requests[i].get();
        }
        aClient->multiRead(requests, metadata.n_chunks);
        bool read = true;
        for(unsigned int i = 0; i < metadata.n_chunks; i++){
            if(!*requests[i]->value){
                read = false;
                char key[200];
                memcpy(key, requests[i]->key, requests[i]->keyLength);
                key[requests[i]->keyLength] = '\0';
                #ifdef MIE_DEBUG
                printf("not found %s, %lu\n", key, requests[i]->version);
                #endif
                break;
            }
            else{
                char key[200];
                memcpy(key, requests[i]->key, requests[i]->keyLength);
                key[requests[i]->keyLength] = '\0';
                #ifdef MIE_DEBUG
                printf("read %s, %lu\n", key, requests[i]->version);
                #endif
            }
        }
        if(read){
            readBuffer.reserve(metadata.original_size);
            size_t remaining = metadata.original_size;
            for(unsigned int i = 0; i < metadata.n_chunks; i++){
                uint32_t size;
                const void* value = requests[i]->value->get()->getValue(&size);
                unsigned int toWrite;
                if(remaining >= size)
                    toWrite = size;
                else
                    toWrite = remaining;
                readBuffer.insert(readBuffer.end(), (char*)value, (char*)value+toWrite);
                remaining -= toWrite;
            }
            status = metadata.original_size;
        }
    }
    catch(ObjectDoesntExistException e){}
    return status;
}

int RamCloudClient::write(const uint64_t tableId, const std::string& filename, const char* buffer,
    const size_t bufferSize)
{
    if(!aClient)
        return INVALID_STATE;
    //try to read and delete ramcloud metadata from previous file version
    int status = NO_ERRORS;
    try{
        RamCloudMetadata oldMetadata;
        Buffer read_buffer;
        char name[200];
        sprintf(name, RAMCLOUD_NAME_FORMAT, filename.c_str(), 0);
        size_t ns = strlen(name);
        ReadRpc read_rpc(aClient.get(), tableId, name, ns, &read_buffer);
        if(bufferSize > MAX_RAMCLOUD_OBJECT_SIZE){
            status = writeMultipleSegments(tableId, filename, buffer, bufferSize, oldMetadata,
                &read_rpc, read_buffer);
        }
        else{
            //aClient->write(tableId, filename.c_str(), filename.size(), buffer, bufferSize);
            WriteRpc write_rpc(aClient.get(), tableId, filename.c_str(), filename.size(), buffer,
                bufferSize);
            try{
                //aClient->read(tableId, name, ns, &buffer);
                read_rpc.wait();
                oldMetadata = readMetadata(read_buffer);
                removeMultipleSegments(tableId, filename, 0, oldMetadata.n_chunks);
            } catch(ObjectDoesntExistException e){}
            write_rpc.wait();
            if(aClient->status != STATUS_OK)
                status = OP_FAILED;
            #ifdef MIE_DEBUG
            else{
                printf("Wrote %s\n", filename.c_str());
            }
            #endif
        }
    }
    catch(ClientException& e){
        status = RAMCLOUD_EXCEPTION;
    }
    return status;
}

int RamCloudClient::writeMultipleSegments(const uint64_t tableId, const std::string& filename,
    const char* buffer, size_t bufferSize, RamCloudMetadata& oldMetadata, ReadRpc* readRpc,
    Buffer& readBuffer)
{
    int status = NO_ERRORS;
    unsigned int total_chunks = bufferSize%MAX_OBJECT_SIZE == 0 ? bufferSize/MAX_OBJECT_SIZE : 
        bufferSize/MAX_OBJECT_SIZE + 1;

    size_t chunks_size = bufferSize%total_chunks == 0 ? bufferSize/total_chunks : 
        bufferSize/total_chunks + 1;
    MultiWriteObject* requests[total_chunks+1];
    unique_ptr<MultiWriteObject> tmp_requests[total_chunks];
    unique_ptr<char[]> tmp_names[total_chunks];
    unique_ptr<char[]> padding;
    unsigned int offset = 0;
    for(unsigned int i = 0; i < total_chunks; i++){
        tmp_names[i] = unique_ptr<char[]>(new char[200]);
        sprintf(tmp_names[i].get(), RAMCLOUD_NAME_FORMAT, filename.c_str(), i+1);
        size_t ns = strlen(tmp_names[i].get());
        const void* value;
        if(bufferSize - offset > chunks_size){
            value = buffer + offset;
        }
        else{
            padding = unique_ptr<char[]>(new char[chunks_size]);
            unsigned int remaining = bufferSize - offset;
            memcpy(padding.get(), buffer + offset, remaining);
            if(!RAND_bytes((unsigned char*)padding.get()+remaining, chunks_size - remaining))
                error(string("Error generating random bytes"));
            value = padding.get();
        }
        tmp_requests[i] = unique_ptr<MultiWriteObject>(new MultiWriteObject(tableId,
            tmp_names[i].get(), ns, value, chunks_size));
        requests[i+1] = tmp_requests[i].get();
        offset += chunks_size;
    }
    int pos = 0;
    char name[200];
    char tb[sizeof(unsigned int)+sizeof(size_t)];
    addToArr(&total_chunks, sizeof(unsigned int), tb, &pos);
    addToArr(&bufferSize, sizeof(size_t), tb, &pos);
    sprintf(name, RAMCLOUD_NAME_FORMAT, filename.c_str(), 0);
    size_t ns = strlen(name);
    MultiWriteObject metadata(tableId, name, ns, tb, sizeof(unsigned int)+sizeof(size_t));
    requests[0] = &metadata;
    //aClient->multiWrite(requests, total_chunks+1);
    MultiWrite write_rpc(aClient.get(), requests, total_chunks + 1);
    try{
        //aClient->read(tableId, name, ns, &buffer);
        readRpc->wait();
        oldMetadata = readMetadata(readBuffer);
    } catch(ObjectDoesntExistException e){
        oldMetadata.original_size = 0;
        oldMetadata.n_chunks = 0;
    }
    if(total_chunks < oldMetadata.n_chunks)
        removeMultipleSegments(tableId, filename, total_chunks + 1,
            oldMetadata.n_chunks - total_chunks);

    write_rpc.wait();
    for(unsigned int i = 0; i <= total_chunks; i++){
        if(requests[i]->status != STATUS_OK)
            status = OP_FAILED;
        else{
            char key[200];
            memcpy(key, requests[i]->key, requests[i]->keyLength);
            key[requests[i]->keyLength] = '\0';
            #ifdef MIE_DEBUG
            printf("wrote %s, %lu\n", key, requests[i]->version);
            #endif
        }
    }
    return status;
}

int RamCloudClient::remove(const uint64_t tableId, const std::string& filename)
{
    if(!aClient)
        return INVALID_STATE;
    RejectRules reject;
    reject.givenVersion = 0;
    reject.doesntExist = 1;
    reject.exists = 0;
    reject.versionLeGiven = 0;
    reject.versionNeGiven = 0;
    int status = NO_ERRORS;
    try{
        try{
            aClient->remove(tableId, filename.c_str(), filename.size(), &reject);
            #ifdef MIE_DEBUG
            printf("removed %s\n", filename.c_str());
            #endif
        } catch (ObjectDoesntExistException e){
            ///read auxiliary file
            Buffer buffer;
            char name[200];
            sprintf(name, RAMCLOUD_NAME_FORMAT, filename.c_str(), 0);
            int ns = strlen(name);
            try{
                aClient->read(tableId, name, ns, &buffer);
                RamCloudMetadata metadata = readMetadata(buffer);
                if(metadata.n_chunks > 0){
                    removeMultipleSegments(tableId, filename, 0, metadata.n_chunks);
                }
            }catch (ObjectDoesntExistException e){/*do nothing*/}
        }
    }
    catch(ClientException& e){
        status = RAMCLOUD_EXCEPTION;
        fprintf(stderr, "RAMCloud exception: %s\n", e.str().c_str());
    }
    return status;
}

void RamCloudClient::removeMultipleSegments(const uint64_t tableId, const std::string& filename,
    const unsigned int firstSegment, const unsigned int numberOfSegments)
{
    unique_ptr<char[]> tmp_names[numberOfSegments];
    unique_ptr<MultiRemoveObject> tmp_requests[numberOfSegments];
    MultiRemoveObject* requests[numberOfSegments];
    for(unsigned int i = 0; i < numberOfSegments; i++){
        tmp_names[i] = unique_ptr<char[]>(new char[200]);
        sprintf(tmp_names[i].get(), RAMCLOUD_NAME_FORMAT, filename.c_str(), i + 
            firstSegment);
        int ns = strlen(tmp_names[i].get());
        tmp_requests[i] = unique_ptr<MultiRemoveObject>(new MultiRemoveObject
            (tableId, tmp_names[i].get(), ns));
        requests[i] = tmp_requests[i].get();
    }
    aClient->multiRemove(requests, numberOfSegments);
    for(unsigned int i = 0; i <= numberOfSegments; i++){
        if(requests[i]->status != STATUS_OK){

        }
        else{
            char key[200];
            memcpy(key, requests[i]->key, requests[i]->keyLength);
            key[requests[i]->keyLength] = '\0';
            #ifdef MIE_DEBUG
            printf("removed %s, %lu\n", key, requests[i]->version);
            #endif
        }
    }
}

}//end namespace mie::rc