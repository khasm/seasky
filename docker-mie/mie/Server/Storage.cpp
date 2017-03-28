#include <sys/socket.h>
#include <netdb.h>
#include <time.h>
#include "Storage.h"
#include "RamCloudStorage.h"
#include "DepskyStorage.h"
#include "Memcached.h"
#include "ServerUtil.h"
#include "Status.h"
#include <signal.h>

#include <iostream>
#include <ctime>
#include <sstream>

namespace MIE{

using namespace std;
using CACHE::MemcachedClient;
using RC::RamCloudStorage;
using DEPSKY::DepskyStorage;

Storage::Storage(int backend, bool cache, int model, const vector<string>& ips, int cid):
    aUseCache(cache), aClientCacheHint(true)
{

    #ifdef MIE_DEBUG
    logger.open("storage.log");
    #endif
    aMaxFileSize = getMaxFileSize();
    aCache = unique_ptr<Cache>(new MemcachedClient());
    aPNRGWaitSec = 0;
    aPNRGWaitNSec = 500000000L;
    ///initialize storage client
    if(backend == BACKEND_DEPSKY){
        aBackend = unique_ptr<BackendStorage>(new DepskyStorage(model, cid, ips));
    }
    else if(backend == BACKEND_RAMCLOUD){
        aBackend = unique_ptr<BackendStorage>(new RamCloudStorage(model));
    }
    //cout<<main_thread<<endl;
    readBlobs();
}

Storage::~Storage()
{
    #ifdef MIE_DEBUG
    logger.close();
    #endif
}

void Storage::log(string msg)
{
    logger<<msg<<endl<<flush;
}

bool Storage::createEntry(const std::string& filename, shared_ptr<File>& file, size_t totalSize,
    bool write)
{
    unique_lock<mutex> tmp(aOpenFilesLock);
    file = aOpenFiles[filename];
    if(!file){
        file = make_shared<File>();
        file->aWaitingReaders = 0;
        file->aWaitingWriters = 0;
        file->aPotencialWaitingReaders = 0;
        file->aAllowedReaders = true;
        file->aDirty = false;
        fillFileMetadata(filename, file);
        if(write){
            file->aReaders = 0;
            file->aIsWriting = 1;
            file->aOwner = this_thread::get_id();
            setupWriteBuffer(file, totalSize);
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<file->aOwner<<" created file entry for "<<filename;
            log(ss.str());
            #endif
        }
        else{
            file->aReadBuffer[this_thread::get_id()] = ReadBuffer();
            file->aReaders = 1;
            file->aIsWriting = 0;
            file->aOwner = thread::id();
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" created file entry for "<<filename
                <<"(read)";
            log(ss.str());
            #endif
        }
        aOpenFiles[filename] = file;
        return true;
    }
    else{
        unique_lock<mutex> file_lock(file->aFileLock);
        if(write){
            if(this_thread::get_id() != file->aOwner){
                file->aWaitingWriters++;
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" waiting to write on file "<<
                    filename<<"("<<file->aWaitingWriters<<")";
                log(ss.str());
                #endif
            }
        }
        else{
            map<thread::id, ReadBuffer>::iterator it = file->aReadBuffer.find(this_thread::get_id());
            if(file->aReadBuffer.end() == it){
                file->aPotencialWaitingReaders++;
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" waiting to read on file "<<filename
                    <<"("<<file->aWaitingReaders<<")";
                log(ss.str());
                #endif
            }
        }
        return false;
    }   
}

bool Storage::getEntry(const std::string& filename, shared_ptr<File>& file)
{
    unique_lock<mutex> tmp(aOpenFilesLock);
    map<string, shared_ptr<File>>::iterator it = aOpenFiles.find(filename);
    if(aOpenFiles.end() != it){
        file = it->second;
        return true;
    }
    else{
        return false;
    }
}

void Storage::removeEntry(const std::string& filename)
{
    unique_lock<mutex> tmp(aOpenFilesLock);
    map<string, shared_ptr<File>>::iterator it = aOpenFiles.find(filename);
    if(aOpenFiles.end() == it)
        return;
    shared_ptr<File> file = it->second;
    unique_lock<mutex> file_lock(file->aFileLock);
    if(0 == file->aWaitingWriters && 0 == file->aWaitingReaders &&
        0 == file->aIsWriting && 0 == file->aReaders && 0 == file->aPotencialWaitingReaders){
        aOpenFiles.erase(filename);
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" removed file entry "<<filename;
        log(ss.str());
        #endif
    }   
}

void Storage::fillFileMetadata(const string& filename, shared_ptr<File> file)
{
    unique_lock<mutex> blobs_lock(aBlobsLock);
    map<string, string>::iterator it = aBlobList.find(filename);
    if(aBlobList.end() != it){
        file->aOldMetadata = it->second;
        blobs_lock.unlock();
        file->aInitialSeqIndex = getBlobSizes(file->aOldMetadata, file->aReadTotalFileSize,
            file->aReadChunkSize);
    }
    else{
        blobs_lock.unlock();
        file->aInitialSeqIndex = 0;
        file->aReadChunkSize = 0;
        file->aReadTotalFileSize = 0;
    }
}

void Storage::prepareWrite(const string& filename, size_t totalSize)
{
    shared_ptr<File> file;
    if(!createEntry(filename, file, totalSize, true) && this_thread::get_id() != file->aOwner){
        takeWriteEntryOwnership(filename, file);
        setupWriteBuffer(file, totalSize);
    }
}

void Storage::takeWriteEntryOwnership(const string& filename, std::shared_ptr<File>& file)
{
    unique_lock<mutex> file_lock(file->aFileLock);
    while(0 < file->aIsWriting){
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" blocked on aNoWriters lock "
            <<"("<<file->aWaitingWriters<<")";
        log(ss.str());
        #endif
        file->aNoWriters.wait(file_lock);
    }
    file->aWaitingWriters--;
    file->aIsWriting++;
    assert(file->aIsWriting == 1);
    file->aOwner = this_thread::get_id();
    #ifdef MIE_DEBUG
    stringstream ss;
    ss<<clock()<<": Thread "<<this_thread::get_id()<<" took ownership "
        <<"("<<file->aWaitingWriters<<")";
    log(ss.str());
    #endif
    //make sure to have the metadata from the last write
    unique_lock<mutex> blobs_lock(aBlobsLock);
    map<string, string>::iterator it = aBlobList.find(filename);
    if(aBlobList.end() != it)
        file->aOldMetadata = it->second;
    else
        file->aOldMetadata = "";
}

void Storage::setupWriteBuffer(shared_ptr<File>& file, size_t totalSize)
{
    //force the vector to reset capacity if another thread already had it setup
    file->aWriteBuffer = vector<char>();
    if(totalSize <= aMaxFileSize){
        file->aWriteBuffer.reserve(totalSize);
        file->aWriteChunkSize = totalSize;
        file->aWriteTotalFileSize = totalSize;
        file->aNewMetadata.clear();
    }
    else{
        unsigned n_chunks = totalSize % aMaxFileSize == 0 ? totalSize / aMaxFileSize :
            totalSize / aMaxFileSize + 1;
        unsigned chunks_size = totalSize % n_chunks == 0 ? totalSize / n_chunks :
            totalSize/n_chunks + 1;
        /*unsigned padding = n_chunks * chunks_size - totalSize;
        printf("Preparing splitting %s into %d chunks of %d bytes with %d padding\n",
            filename.c_str(), n_chunks, chunks_size, padding);*/
        file->aWriteBuffer.reserve(chunks_size);
        file->aWriteChunkSize = chunks_size;
        file->aWriteTotalFileSize = totalSize;
        file->aNewMetadata = to_string(totalSize)+" "+to_string(chunks_size);
    }
}

bool Storage::remove(const std::string& filename)
{
    shared_ptr<File> file;
    if(!createEntry(filename, file, 0, true)){
        takeWriteEntryOwnership(filename, file);
    }

    bool status = removeFile(filename, file);

    removeEntry(filename);
    return status;
}

bool Storage::removeFile(const string& filename, shared_ptr<File>& file)
{
    unique_lock<mutex> file_lock(file->aFileLock);
    enterCommitZone(filename, file, file_lock);

    unique_lock<mutex> blobs_lock(aBlobsLock);
    bool status = NO_ERRORS;
    map<string, string>::iterator it = aBlobList.find(filename);
    if(aBlobList.end() != it){
        blobs_lock.unlock();
        status = updateBlobsRemove(filename);
    }
    else{
        blobs_lock.unlock();
        if(aUseCache && aClientCacheHint){
            aCache->remove(filename);
        }
        status = aBackend->remove(filename);
        #ifdef MIE_DEBUG
        if(status){
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" removed "
                <<filename;
            log(ss.str());
        }
        #endif
    }

    leaveCommitZone(filename, file);
    return status;
}

bool Storage::write(const string& filename, const char* buffer, size_t bufferSize)
{
    shared_ptr<File> file;
    if(createEntry(filename, file, bufferSize, true))
        return singleWrite(filename, buffer, bufferSize, file);
    else
        return partialWrite(filename, buffer, bufferSize, file);
    
}

bool Storage::singleWrite(const string& filename, const char* buffer, size_t bufferSize,
    shared_ptr<File>& file)
{
    //this method avoids a copy of the data by bypassing the write buffer completely
    bool status = OP_FAILED;
    if(bufferSize < aMaxFileSize){
        status = commitSmallFile(filename, file, buffer, bufferSize);
    }
    else{
        status = NO_ERRORS;
        ///write all chunks to the backend
        size_t sent = 0;
        while(sent < bufferSize){
            string seq;
            while(seq.empty()){
                unsigned seq_num;
                fillRandomBytes((unsigned char*)&seq_num, sizeof(unsigned));
                string tmp = to_string(seq_num);
                if(isValidNextSeq(file->aOldMetadata, file->aNewMetadata, tmp))
                    seq = tmp;
            }
            string chunk_name = filename + seq;
            file->aNewMetadata += " "+seq;
            if(sent + file->aWriteChunkSize < bufferSize){
                ///no padding needed
                #ifdef MIE_DEBUG
                printf("STORAGE: Partial write, %s from %lu to %lu of %lu\n", chunk_name.c_str(),
                    sent, sent+file->aWriteChunkSize, bufferSize);
                #endif
                if(!aBackend->write(chunk_name, buffer+sent, file->aWriteChunkSize)){
                    status = OP_FAILED;
                    break;
                }
                #ifdef MIE_DEBUG
                if(status){
                    stringstream ss;
                    ss<<clock()<<": Thread "<<this_thread::get_id()<<" wrote segment "<<chunk_name
                        <<"("<<file->aWriteChunkSize<<")";
                    log(ss.str());
                }
                else{
                    stringstream ss;
                    ss<<clock()<<": Thread "<<this_thread::get_id()<<" failed to write segment "<<chunk_name
                        <<"("<<file->aWriteChunkSize<<")";
                    log(ss.str());   
                }
                #endif
            }
            else{
                unique_ptr<char[]> tmp(new char[file->aWriteChunkSize]);
                unsigned copied = bufferSize-sent;
                memcpy(tmp.get(), buffer+sent, copied);
                fillRandomBytes((unsigned char*)tmp.get() + copied, file->aWriteChunkSize - copied);
                #ifdef MIE_DEBUG
                printf("STORAGE: Partial write, %s from %lu to %lu of %lu\n", chunk_name.c_str(), sent,
                    sent+copied, bufferSize);
                #endif
                if(!aBackend->write(chunk_name, tmp.get(), file->aWriteChunkSize))
                    status = OP_FAILED;
                #ifdef MIE_DEBUG
                if(status){
                    stringstream ss;
                    ss<<clock()<<": Thread "<<this_thread::get_id()<<" wrote segment "<<chunk_name
                        <<"("<<file->aWriteChunkSize<<")";
                    log(ss.str());
                }
                else{
                    stringstream ss;
                    ss<<clock()<<": Thread "<<this_thread::get_id()<<" failed to write segment "<<chunk_name
                        <<"("<<file->aWriteChunkSize<<")";
                    log(ss.str());   
                }
                #endif
            }
            sent += file->aWriteChunkSize;
        }
        if(status){
            status = commitBigFile(filename, file);
        }
    }
    removeEntry(filename);
    return status;
}

bool Storage::partialWrite(const string& filename, const char* buffer, size_t bufferSize,
    shared_ptr<File>& file)
{
    bool ret = OP_FAILED;
    if(this_thread::get_id() == file->aOwner){
        if(file->aWriteBuffer.size() + bufferSize <= file->aWriteChunkSize){
            file->aWriteBuffer.insert(file->aWriteBuffer.end(), buffer, buffer + bufferSize);
            //printf("Writing segment: %d %d %d\n", file->buffer_size, buffer_size, file->buffer_capacity);
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" added "<<bufferSize<<" to "<<filename
                <<"("<<file->aWriteBuffer.size()<<")";
            log(ss.str());
            #endif
            ret = NO_ERRORS;
        }
        else if(!file->aNewMetadata.empty()){
            int copied = file->aWriteChunkSize - file->aWriteBuffer.size();
            file->aWriteBuffer.insert(file->aWriteBuffer.end(), buffer, buffer + copied);
            string seq;
            while(seq.empty()){
                unsigned seq_num;
                fillRandomBytes((unsigned char*)&seq_num, sizeof(unsigned));
                string tmp = to_string(seq_num);
                if(isValidNextSeq(file->aOldMetadata, file->aNewMetadata, tmp))
                    seq = tmp;
            }
            string chunk_name = filename + seq;
            ///write data
            if(aBackend->write(chunk_name, file->aWriteBuffer.data(), file->aWriteChunkSize))
                ret = NO_ERRORS;
            #ifdef MIE_DEBUG
            if(ret){
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" wrote segment "<<chunk_name
                    <<"("<<file->aWriteChunkSize<<")";
                log(ss.str());
            }
            else{
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" failed to write segment "<<chunk_name
                    <<"("<<file->aWriteChunkSize<<")";
                log(ss.str());   
            }
            #endif
            file->aNewMetadata += " "+seq;
            ///copy remaining data to buffer
            file->aWriteBuffer.clear();
            file->aWriteBuffer.insert(file->aWriteBuffer.end(), buffer + copied, buffer + bufferSize);
        }
    }
    else{
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" tried to write without ownership on "<<filename
            <<"("<<file->aWaitingWriters<<")";
        log(ss.str());
        #endif
        //to get here a thread must have called write without using prepareWrite while another
        //thread was writting
        takeWriteEntryOwnership(filename, file);
        setupWriteBuffer(file, bufferSize);
        ret = singleWrite(filename, buffer, bufferSize, file);
    }
    #ifdef MIE_DEBUG
    if(!ret)
        raise(SIGABRT);
    #endif
    return ret;
}

bool Storage::finishWrite(const string& filename)
{
    shared_ptr<File> file;
    if(!getEntry(filename, file)){
        return false;
    }
    if(this_thread::get_id() != file->aOwner)
        return false;
    bool status = NO_ERRORS;
    //write final data
    if(0 < file->aWriteBuffer.size()){
        if(file->aNewMetadata.empty()){
            status = commitSmallFile(filename, file);
        }
        else{
            while(file->aWriteBuffer.size() < file->aWriteChunkSize){
                char c;
                fillRandomBytes((unsigned char*)&c, sizeof(char));
                file->aWriteBuffer.push_back(c);
            }
            string seq;
            while(seq.empty()){
                unsigned seq_num;
                fillRandomBytes((unsigned char*)&seq_num, sizeof(unsigned));
                string tmp = to_string(seq_num);
                if(isValidNextSeq(file->aOldMetadata, file->aNewMetadata, tmp))
                    seq = tmp;
            }
            string chunk_name = filename + seq;
            ///write data
            status = aBackend->write(chunk_name, file->aWriteBuffer.data(), file->aWriteChunkSize);
            file->aNewMetadata += " "+seq;
            ///update blobs file
            if(status){
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" wrote segment "<<chunk_name
                    <<"("<<file->aWriteChunkSize<<")";
                log(ss.str());
                #endif
                status = commitBigFile(filename, file);
            }
            #ifdef MIE_DEBUG
            else{
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" failed to write segment "<<chunk_name
                    <<"("<<file->aWriteChunkSize<<")";
                log(ss.str());
            }
            #endif
        }
    }
    removeEntry(filename);
    return status;
}

bool Storage::commitSmallFile(const string& filename, std::shared_ptr<File>& file, const char* buffer,
        size_t totalSize)
{
    bool status = OP_FAILED;
    unique_lock<mutex> file_lock(file->aFileLock);
    enterCommitZone(filename, file, file_lock);

    if(!file->aOldMetadata.empty())
        updateBlobsRemove(filename);
    else if(aUseCache && aClientCacheHint)
        aCache->remove(filename);
    if(NULL == buffer)
        status = aBackend->write(filename, file->aWriteBuffer.data(), file->aWriteChunkSize);
    else
        status = aBackend->write(filename, buffer, totalSize);
    #ifdef MIE_DEBUG
    if(status){
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" wrote "<<filename
            <<"("<<file->aWriteChunkSize<<")";
        log(ss.str());
    }
    else{
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" failed to write "<<filename
            <<"("<<file->aWriteChunkSize<<")";
        log(ss.str());
    }
    #endif
    leaveCommitZone(filename, file);
    //end of commit, file is unlocked, other threads can see the new data
    return status;
}

bool Storage::commitBigFile(const string& filename, std::shared_ptr<File>& file)
{
    bool status = OP_FAILED;
    unique_lock<mutex> file_lock(file->aFileLock);
    enterCommitZone(filename, file, file_lock);

    if(file->aOldMetadata.empty()){
        #ifdef MIE_DEBUG
        printf("STORAGE::commitBigFile: Removing old file: %s\n", filename.c_str());
        #endif
        aBackend->remove(filename);
        if(aUseCache && aClientCacheHint)
            aCache->remove(filename);
    }
    status = updateBlobsAdd(filename, file->aNewMetadata);

    leaveCommitZone(filename, file);
    //end of commit, file is unlocked, other threads can see the new data
    return status;
}

void Storage::enterCommitZone(const string& filename, shared_ptr<File>& file,
    unique_lock<mutex>& file_lock)
{
    file->aAllowedReaders = false;
    while(0 < file->aReaders){
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" waiting for readers on file "<<filename
            <<"("<<file->aReaders<<")";
        log(ss.str());
        #endif
        file->aNoReaders.wait(file_lock);
    }
}

void Storage::leaveCommitZone(const string& filename, shared_ptr<File>& file)
{
    file->aAllowedReaders = true;
    file->aIsWriting--;
    assert(file->aIsWriting == 0);
    file->aDirty = true;
    if(0 < file->aWaitingReaders){
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" released ownership and signaled waiting readers "
            <<file->aWaitingReaders;
        log(ss.str());
        #endif
        file->aWriteDone.notify_all();
    }
    else{
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" released ownership and signaled waiting writer "
            <<file->aWaitingWriters;
        log(ss.str());
        #endif
        file->aNoWriters.notify_one();
    }
}

int Storage::prepareRead(const string& filename)
{
    shared_ptr<File> file;
    if(createEntry(filename, file)){
        return setupReadBuffer(filename, file);
    }
    else{
        takeReadEntry(filename, file);
        return setupReadBuffer(filename, file);
    }
}

void Storage::takeReadEntry(const string& filename, shared_ptr<File>& file)
{
    unique_lock<mutex> file_lock(file->aFileLock);
    file->aPotencialWaitingReaders--;
    bool blocked = false;
    while(!file->aAllowedReaders){
        if(!blocked){
            file->aWaitingReaders++;
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" joined waiting readers "
                <<file->aWaitingReaders;
            log(ss.str());
            #endif
        }
        blocked = true;
        file->aWriteDone.wait(file_lock);
        #ifdef MIE_DEBUG
        stringstream ss;
        ss<<clock()<<": Thread "<<this_thread::get_id()<<" is leaving waiting readers "
            <<file->aWaitingReaders;
        log(ss.str());
        #endif
    }
    if(blocked){
        file->aWaitingReaders--;
        if(0 == file->aWaitingReaders){
            file->aNoWriters.notify_one();
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" notified a writer "
                <<file->aWaitingWriters;
            log(ss.str());
            #endif
        }
    }
    file->aReaders++;
    file->aReadBuffer[this_thread::get_id()] = ReadBuffer();
    #ifdef MIE_DEBUG
    stringstream ss;
    ss<<clock()<<": Thread "<<this_thread::get_id()<<" became a reader "
        <<file->aReaders;
    log(ss.str());
    #endif
    if(file->aDirty){
        fillFileMetadata(filename, file);
        file->aDirty = false;
    }
}

void Storage::leaveReadEntry(const string& filename, shared_ptr<File>& file)
{
    unique_lock<mutex> file_lock(file->aFileLock);
    map<thread::id, ReadBuffer>::iterator it = file->aReadBuffer.find(this_thread::get_id());
    if(file->aReadBuffer.end() == it)
        return;
    file->aReadBuffer.erase(this_thread::get_id());
    file->aReaders--;
    #ifdef MIE_DEBUG
    stringstream ss;
    ss<<clock()<<": Thread "<<this_thread::get_id()<<" finished reading "
        <<file->aReaders;
    log(ss.str());
    #endif
    if(file->aReaders == 0){
        file->aNoReaders.notify_one();
        #ifdef MIE_DEBUG
        stringstream ss2;
        ss2<<clock()<<": Thread "<<this_thread::get_id()<<" notified commiting writer "
            <<file->aIsWriting;
        log(ss2.str());
        #endif
    }
    file_lock.unlock();
    removeEntry(filename);
}

int Storage::setupReadBuffer(const string& filename, shared_ptr<File>& file)
{
    int status = NOT_FOUND;
    unique_lock<mutex> file_lock(file->aFileLock);
    ReadBuffer b = file->aReadBuffer[this_thread::get_id()];
    file_lock.unlock();
    b.aBufferOffset = 0;
    b.aBuffer = unique_ptr<vector<char>>(new vector<char>());
    if(0 != file->aInitialSeqIndex){
        string seq;
        b.aNextSeqIndex = getNextSeq(file->aOldMetadata, seq, file->aInitialSeqIndex);
        string chunk_name = filename + seq;
        status = aBackend->read(chunk_name, *b.aBuffer);
        if(0 <= status)
            status = file->aReadTotalFileSize;
    }
    else{
        b.aNextSeqIndex = 0;
        status = aBackend->read(filename, *b.aBuffer);
        if(0 <= status){
            unique_lock<mutex> tmp(file->aFileLock);
            #ifdef MIE_DEBUG
            stringstream ss2;
            ss2<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                <<filename<<" for setupReadBuffer ("<<status<<")";
            log(ss2.str());
            #endif
            file->aReadTotalFileSize = status;
            file->aReadChunkSize = status;
        }
    }
    if(0 < status){
        file_lock.lock();
        file->aReadBuffer[this_thread::get_id()] = b;
    }
    else{
        leaveReadEntry(filename, file);
    }
    return status;
}

int Storage::forceRead(const string& filename, vector<char>& buffer)
{
    return read(filename, buffer, false);
}

int Storage::read(const string& filename, vector<char>& buffer, size_t toRead, bool useCache)
{
    shared_ptr<File> file;
    if(createEntry(filename, file))
        return singleRead(filename, buffer, file, toRead, useCache);
    else
        return partialRead(filename, buffer, file, toRead, useCache);
}

int Storage::singleRead(const string& filename, vector<char>& buffer, shared_ptr<File>& file,
    size_t toRead, bool useCache)
{
    int value_size = NOT_FOUND;
    if(file->aInitialSeqIndex == 0){
        if(aUseCache && useCache && aClientCacheHint)
            value_size = aCache->read(filename, buffer);
        if(0 > value_size){
            ///not found, read from backend and update cache
            value_size = aBackend->read(filename, buffer);
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                <<filename;
            log(ss.str());
            #endif
            if(0 <= value_size && aUseCache && aClientCacheHint)
                aCache->write(filename, buffer.data(), value_size);
        }
    }
    else{
        size_t offset = file->aInitialSeqIndex;
        buffer.clear();
        while(buffer.size() < file->aReadTotalFileSize && 
            (0 == toRead || buffer.size() < toRead)){
            value_size = NOT_FOUND;
            string seq;
            offset = getNextSeq(file->aOldMetadata, seq, offset);
            string chunk_name = filename+seq;
            vector<char> b;
            if(aUseCache && useCache && aClientCacheHint)
                value_size = aCache->read(chunk_name, b);
            if(0 > value_size){
                ///not found, read from backend and update cache
                value_size = aBackend->read(chunk_name, b);
                if(0 <= value_size && aUseCache && aClientCacheHint)
                    aCache->write(filename, b.data(), value_size);
            }
            if(0 <= value_size){
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                    <<chunk_name;
                log(ss.str());
                #endif
                unsigned to_copy = buffer.size() + value_size < file->aReadTotalFileSize ? value_size :
                    file->aReadTotalFileSize - buffer.size();
                buffer.insert(buffer.end(), b.begin(), b.begin() + to_copy);
            }
            else{
                break;
            }
        }
    }
    if(0 < toRead)
        buffer.resize(toRead);
    leaveReadEntry(filename, file);
    if(0 <= value_size)
        return buffer.size();
    else
        return value_size;
}

int Storage::partialRead(const string& filename, vector<char>& buffer, shared_ptr<File>& file,
    size_t toRead, bool useCache)
{
    unique_lock<mutex> file_lock(file->aFileLock);
    ReadBuffer b = file->aReadBuffer[this_thread::get_id()];
    file_lock.unlock();
    int status = NOT_FOUND;
    if(b.aBuffer){
        //printf("\n\n\nSTORAGE: Partial read\n\n\n");
        //fflush(stdin);
        buffer.clear();
        if(toRead == 0){
            if(b.aBufferOffset < b.aBuffer->size() && useCache){
                //copy the rest of the data on the read buffer
                unsigned to_copy = b.aBuffer->size() - b.aBufferOffset;
                char* begin = b.aBuffer->data() + b.aBufferOffset;
                buffer.insert(buffer.end(), begin, begin + to_copy);
                b.aBufferOffset += to_copy;
            }
            string seq;
            size_t nextSeqIndex = b.aNextSeqIndex;
            while(file->aOldMetadata.size() != b.aNextSeqIndex){
                string chunk_name = filename + seq;
                nextSeqIndex = getNextSeq(file->aOldMetadata, seq, nextSeqIndex);
                vector<char> buf;
                if(aUseCache && useCache && aClientCacheHint)
                    status = aCache->read(chunk_name, buf);
                if(0 > status){
                    ///not found, read from backend and update cache
                    status = aBackend->read(chunk_name, buf);
                    if(0 <= status && aUseCache && aClientCacheHint)
                        aCache->write(filename, buf.data(), status);
                }
                if(0 <= status){
                    #ifdef MIE_DEBUG
                    stringstream ss;
                    ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                        <<chunk_name;
                    log(ss.str());
                    #endif
                    b.aNextSeqIndex = nextSeqIndex;
                    buffer.insert(buffer.end(), buf.data(), buf.data() + status);
                }
                else
                    break;
                nextSeqIndex = getNextSeq(file->aOldMetadata, seq, nextSeqIndex);
            }
            if(0 <= status){
                status = buffer.size();
                leaveReadEntry(filename, file);
            }
        }
        else{
            unsigned read = 0;
            unsigned to_copy = b.aBufferOffset + toRead <= b.aBuffer->size() ? toRead :
                b.aBuffer->size() - b.aBufferOffset;
            if(0 < to_copy){
                buffer.insert(buffer.end(), b.aBuffer->data() + b.aBufferOffset,
                    b.aBuffer->data() + b.aBufferOffset + to_copy);
                read += to_copy;
                b.aBufferOffset += to_copy;
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                    <<to_copy<<" ("<<b.aBuffer->size()-b.aBufferOffset<<")";
                log(ss.str());
                #endif
            }
            else if(0 == to_copy && file->aOldMetadata.empty()){
                #ifdef MIE_DEBUG
                stringstream ss;
                ss<<clock()<<": Thread "<<this_thread::get_id()<<" tried to read "
                    <<filename<<" after reaching end of file";
                log(ss.str());
                #endif
                return END_OF_FILE;
            }
            size_t nextSeqIndex = b.aNextSeqIndex;
            while(read < toRead && file->aOldMetadata.size() != b.aNextSeqIndex){
                string seq;
                nextSeqIndex = getNextSeq(file->aOldMetadata, seq, nextSeqIndex);
                string chunk_name = filename + seq;
                if(aUseCache && useCache && aClientCacheHint)
                    status = aCache->read(chunk_name, *b.aBuffer);
                if(0 > status){
                    ///not found, read from backend and update cache
                    status = aBackend->read(chunk_name, *b.aBuffer);
                    if(0 <= status && aUseCache && aClientCacheHint)
                        aCache->write(filename, b.aBuffer->data(), status);
                }
                if(0 <= status){
                    #ifdef MIE_DEBUG
                    {
                        stringstream ss;
                        ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                            <<chunk_name;
                        log(ss.str());
                    }
                    #endif
                    b.aBufferOffset = 0;
                    b.aNextSeqIndex = nextSeqIndex;
                    to_copy = b.aBufferOffset + toRead - read <= b.aBuffer->size() ? toRead - read :
                        b.aBuffer->size();
                    buffer.insert(buffer.end(), b.aBuffer->data(), b.aBuffer->data() + to_copy);
                    #ifdef MIE_DEBUG
                    {
                        stringstream ss;
                        ss<<clock()<<": Thread "<<this_thread::get_id()<<" read "
                            <<to_copy<<" ("<<b.aBuffer->size()-b.aBufferOffset<<")";
                        log(ss.str());
                    }
                    #endif
                    read += to_copy;
                    b.aBufferOffset += to_copy;
                }
                else{
                    break;
                }
            }
            unique_lock<mutex> file_lock(file->aFileLock);
            file->aReadBuffer[this_thread::get_id()] = b;
            file_lock.unlock();
            if(0 < status){
                if(file->aOldMetadata.size() == b.aNextSeqIndex && 0 == read)
                    status = END_OF_FILE;
                else if(0 < read)
                    status = read;
            }
            else{
                status = read;
            }
        }
        return status;
    }
    else{
        takeReadEntry(filename, file);
        return singleRead(filename, buffer, file, toRead, useCache);
    }
}

void Storage::closeRead(const string& filename)
{
    shared_ptr<File> file;
    if(getEntry(filename, file))
        leaveReadEntry(filename, file);
}

bool Storage::updateBlobsAdd(const string& filename, const string& metadata)
{
    if(removeOldFiles(filename)){
        unique_lock<mutex> blobs_lock(aBlobsLock);
        aBlobList[filename] = metadata;
        blobs_lock.unlock();
        return writeBlobs();
    }
    else{
        return false;
    }
}

bool Storage::updateBlobsRemove(const string &filename)
{
    if(removeOldFiles(filename)){
        unique_lock<mutex> blobs_lock(aBlobsLock);
        aBlobList.erase(filename);
        blobs_lock.unlock();
        return writeBlobs();
    }
    else{
        return false;
    }
}

bool Storage::removeOldFiles(const string& filename)
{
    bool status = NO_ERRORS;
    unique_lock<mutex> blobs_lock(aBlobsLock);
    map<string, string>::iterator it = aBlobList.find(filename);
    if(aBlobList.end() != it){
        blobs_lock.unlock();
        size_t original_size;
        unsigned chunks_size;
        size_t offset = getBlobSizes(it->second, original_size, chunks_size);
        string seq;
        while(it->second.size() != offset && status){
            offset = getNextSeq(it->second, seq, offset);
            string oldFileName(filename+seq);
            status = aBackend->remove(oldFileName);
            #ifdef MIE_DEBUG
            stringstream ss;
            ss<<clock()<<": Thread "<<this_thread::get_id()<<" removed "<<oldFileName;
            log(ss.str());
            #endif
            if(aUseCache)
                aCache->remove(oldFileName);
        }
    }
    return status;
}

bool Storage::writeBlobs(){
    vector<char> buffer;
    unique_lock<mutex> blobs_lock(aBlobsLock);
    if(aBlobList.empty())
        return true;
    for(map<string, string>::iterator it=aBlobList.begin(); it != aBlobList.end(); ++it){
        for(size_t i = 0; i < it->first.size(); i++){
            buffer.push_back(it->first[i]);
        }
        buffer.push_back(' ');
        for(size_t i = 0; i < it->second.size(); i++){
            buffer.push_back(it->second[i]);
        }
        buffer.push_back('\n');
    }
    blobs_lock.unlock();
    //the whole concept of the blob file needs a lot more work
    assert(aMaxFileSize >= buffer.size());
    ///TODO: encrypt buffer.data()
    string blob_name("blobs.blob");
    ///DEBUG
    string debug(buffer.data(), buffer.size());
    printf("storing blob file:\n%s", debug.c_str());
    return aBackend->write(blob_name, buffer.data(), buffer.size());
}

void Storage::readBlobs(){
    vector<char> buffer;
    int b_size = aBackend->read("blobs.blob", buffer);
    if(0 <= b_size){
        ///TODO: decrypt buffer.data()
        char* start = buffer.data();
        for(size_t i = 0; i < buffer.size(); i++){
            ///find '\n'
            if(buffer[i] == '\n'){
                char* end = buffer.data()+i;
                ///reconstruct entry stored between start and buffer[i]
                string filename;
                size_t j = 0;
                while(j < i){
                    ///get name of entry
                    if(*(start+j) == ' '){
                        filename.assign(start, j);
                        break;
                    }
                    j++;
                }
                string metadata(start+j+1, end-(start+j+1));
                start = end+1;
                aBlobList[filename] = metadata;
            }
        }
    }
}

size_t Storage::getBlobSizes(const string& metadata, size_t& original_size, unsigned& chunks_size)
{
    size_t pos = metadata.find_first_of(' ');
    if(string::npos == pos)
        return 0;
    else
        original_size = stoi(metadata.substr(0, pos));
    size_t pos2 = metadata.find_first_of(' ', pos + 1);
    if(string::npos == pos2)
        return 0;
    else
        chunks_size = stoi(metadata.substr(pos + 1, pos2 - pos - 1));
    return pos2 + 1;
}

size_t Storage::getNextSeq(const string& metadata, string &seq, size_t offset)
{
    if(metadata.empty()){
        return 0;
    }
    size_t pos = metadata.find_first_of(' ', offset);
    if(string::npos != pos){
        seq = metadata.substr(offset, pos - offset);
        return pos+1;
    }
    else{
        seq = metadata.substr(offset);
        return metadata.size();
    }
}

bool Storage::isValidNextSeq(const string& oldMetadata, const string& newMetadata,
    const string& nextSeq)
{
    if(oldMetadata.find(nextSeq) != string::npos || newMetadata.find(nextSeq) != string::npos)
        return false;
    else
        return true;
}

void Storage::fillRandomBytes(unsigned char* toFill, unsigned size)
{
    while(!RAND_bytes(toFill, size)){
        struct timespec waiting;
        waiting.tv_sec = aPNRGWaitSec;
        waiting.tv_nsec = aPNRGWaitNSec;
        nanosleep(&waiting, NULL);
    }
}

double Storage::getTotalNetUp()
{
    return aBackend->getTotalNetUp();
}

double Storage::getTotalNetDown()
{
    return aBackend->getTotalNetDown();
}

double Storage::getParallelNetUp()
{
    return aBackend->getParallelNetUp();
}

double Storage::getParallelNetDown()
{
    return aBackend->getParallelNetDown();
}

double Storage::getHitRatio()
{
    if(aUseCache)
        return aCache->getHitRatio();
    else
        return 0;
}

void Storage::resetTimes()
{
    aBackend->resetTimes();
    if(aUseCache)
        aCache->resetStats();
}

void Storage::resetCache()
{
    if(aUseCache)
        aCache->clear();
}

void Storage::setCache(bool useCache)
{
    aClientCacheHint = useCache;
}

}//end namespace mie