#ifndef MIE_STORAGE
#define MIE_STORAGE

#include <vector>
#include <string>
#include <thread>
#include <map>
#include <queue>
#include <memory>
#include <mutex>
#include <condition_variable>
#include "BackendStorage.h"
#include "Config.h"
#include "Status.h"
#include "Cache.h"

#include <fstream>

namespace MIE{

struct ReadBuffer{
    std::shared_ptr<std::vector<char>> aBuffer;
    unsigned aBufferOffset;
    unsigned aNextSeqIndex;
};

struct File{
    //used for both writes and reads
    std::string aOldMetadata;
    //file write data
    std::vector<char> aWriteBuffer;
    unsigned aWriteChunkSize;
    size_t aWriteTotalFileSize;
    std::string aNewMetadata;
    //file read data
    std::map<std::thread::id, ReadBuffer> aReadBuffer;
    size_t aReadTotalFileSize;
    unsigned aReadChunkSize;
    unsigned aInitialSeqIndex;
    //concurrency management
    std::mutex aFileLock;
    std::condition_variable aNoWriters;
    std::condition_variable aNoReaders;
    std::condition_variable aWriteDone;
    unsigned aIsWriting;
    unsigned aReaders;
    unsigned aWaitingWriters;
    unsigned aWaitingReaders;
    unsigned aPotencialWaitingReaders;
    bool aAllowedReaders;
    bool aDirty;
    //id of writer thread currently writing
    std::thread::id aOwner;
};

class Storage{

    std::map<std::string, std::shared_ptr<File>> aOpenFiles;
    std::mutex aOpenFilesLock;
    std::map<std::string, std::string> aBlobList;
    std::mutex aBlobsLock;
    std::unique_ptr<MIE::BackendStorage> aBackend;

    std::unique_ptr<MIE::Cache> aCache;
    bool aUseCache;
    size_t aMaxFileSize;
    time_t aPNRGWaitSec;
    long aPNRGWaitNSec;

    std::ofstream logger;
    void log(std::string msg);

    /**
     * Attempts to create a new file entry.
     * If a entry for the file was not found it is created and the caller thread assigned ownership.
     * Otherwise it returns false. In either case file will point to the file entry.
     * @return true if the file entry was created, false otherwise 
     */
    bool createEntry(const std::string& filename, std::shared_ptr<File>& file, size_t totalSize = 0,
        bool write = false);
    bool getEntry(const std::string& filename, std::shared_ptr<File>& file);
    void removeEntry(const std::string& filename);

    void enterCommitZone(const std::string& filename, std::shared_ptr<File>& file,
        std::unique_lock<std::mutex>& file_lock);
    void leaveCommitZone(const std::string& filename, std::shared_ptr<File>& file);

    void setupWriteBuffer(std::shared_ptr<File>& file, size_t totalSize);
    int setupReadBuffer(const std::string& filename, std::shared_ptr<File>& file);
    void takeWriteEntryOwnership(const std::string& filename, std::shared_ptr<File>& file);
    void takeReadEntry(const std::string& filename, std::shared_ptr<File>& file);
    void leaveReadEntry(const std::string& filename, std::shared_ptr<File>& file);
    bool commitSmallFile(const std::string& filename, std::shared_ptr<File>& file, const char* buffer = NULL,
        size_t totalSize = 0);
    bool commitBigFile(const std::string& filename, std::shared_ptr<File>& file);
    void fillFileMetadata(const std::string& filename, std::shared_ptr<File> file);
    
    bool singleWrite(const std::string& filename, const char* buffer, size_t bufferSize,
        std::shared_ptr<File>& file);
    bool partialWrite(const std::string& filename, const char* buffer, size_t bufferSize,
        std::shared_ptr<File>& file);

    int singleRead(const std::string& filename, std::vector<char>& buffer, std::shared_ptr<File>& file,
        size_t toRead = 0, bool useCache = true);
    int partialRead(const std::string& filename, std::vector<char>& buffer, std::shared_ptr<File>& file,
        size_t toRead = 0, bool useCache = true);

    bool removeFile(const std::string& filename, std::shared_ptr<File>& file);

    bool removeOldFiles(const std::string& oldMetadata);
    bool updateBlobsAdd(const std::string& filename, const std::string& metadata);
    bool updateBlobsRemove(const std::string& filename);
    bool writeBlobs();
    void readBlobs();

    size_t getBlobSizes(const std::string& metadata, size_t& original_size, unsigned& chunkSize);
    size_t getNextSeq(const std::string& metadata, std::string& seq, size_t offset);
    bool isValidNextSeq(const std::string& oldMetadata, const std::string& newMetadata,
        const std::string& seq_num);
    void fillRandomBytes(unsigned char* toFill, unsigned size);

  public:
    /**
     * Prepares a document to be read in parts.
     * This method will only return when the document is ready. If a write is currently in progress
     * it will wait until the write is done before returning. It will return the file size or a negative
     * error status. On small files the entirity of the file might be read on this method and buffered
     * for the next read calls.
     * @param filename name of the file
     * @return size of the file of a negative value for errors
     */
    int prepareRead(const std::string& filename);
    /**
     * Reads a documents and places it at the beginning of buffer.
     * If this method is called without a call to prepare write it will place up to toRead bytes of the
     * document in buffer. If the document has less than toRead bytes or toRead is 0 it will place the
     * whole document in buffer. Further calls to this method will return the first toRead bytes again.
     * If a call to prepareRead was made this method will keep track of the bytes returned to the calling
     * thread. In this case this method will put the next toRead bytes in buffer, or the rest of the
     * document if there are less than toRead bytes remaining or toRead is 0. Several joinable threads 
     * can safely try to read the same file in parts. Information about which bytes were returned is 
     * kept on a per thread basis. When a file is read in parts useCache is only considered when some of
     * the file data needs to be read from the backend. Once its read from the backend it will be kept in
     * a buffer until the calling thread requests those bytes. While reading from that buffer the
     * useCache option is ignored. If a thread tries to read once all bytes from the file have been
     * consumed this method will return END_OF_FILE.
     * It will return the number of bytes read or a negative value for errors. The previous contents of
     * buffer be cleared.
     * @param filename name of the file
     * @param buffer the buffer to hold the contents read
     * @param toRead maximum number of bytes to read
     * @param useCache use the server cache if it is required to retrieve some part of the file from the
     * backend
     * @return number of bytes read, or a negative value for errors
     */
    int read(const std::string& filename, std::vector<char>& buffer, size_t toRead = 0,
        bool useCache = true);
    /**
     * Finishes a prepared read.
     * Once a thread is finished reading it should call this method if it used prepareRead to free the
     * resources allocated for the read and allow writers to progress. Calling this method without 
     * calling prepareRead before has no effect.
     */
    void closeRead(const std::string& filename);
    int forceRead(const std::string& filename, std::vector<char>& buffer);

    void prepareWrite(const std::string& filename, size_t totalSize);
    bool write(const std::string& filename, const char* buffer, size_t bufferSize);
    bool finishWrite(const std::string& filename);

    bool remove(const std::string& filename);

    double getTotalNetUp();
    double getTotalNetDown();
    double getParallelNetUp();
    double getParallelNetDown();
    double getHitRatio();
    void resetTimes();
    void resetCache();

    Storage(int backend, bool cache = true, int model = 0, const std::vector<std::string>&
        ips = std::vector<std::string>(), int cid = 1);
    ~Storage();
};

}//end namespace mie
#endif
