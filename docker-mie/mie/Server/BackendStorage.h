#ifndef MIE_BACKEND
#define MIE_BACKEND
#include <vector>
#include <string>

namespace MIE{

const int BACKEND_UNDEFINED = 0;
const int BACKEND_DEPSKY = 1;
const int BACKEND_RAMCLOUD = 2;

/**
 * Base class for the backend classes.
 * All backends specific implementations subclass from this class.
 */
class BackendStorage{
  public:
  	/**
  	 * Read a document from the backend.
  	 * The document will be read and stored on buffer. If the document couldn't be read for any reason
  	 * buffer remains unchanged, otherwise all previous contents are erased. This method guarantees that
  	 * the contents return in buffer are correct. If the document was read but was found corrupt no data
  	 * is returned.
  	 * @param name the name of the document to be read
  	 * @param buffer buffer to hold the contents of the document
  	 * @return the size of the document contents stored in buffer, which could be 0, or a negative value
  	 * if an error occurred.
  	 */
    virtual int read(const std::string& name, std::vector<char>& buffer) =0;
    /**
     * Write a document to the backend.
     * Writes bufferSize bytes from buffer to the backend. All fragmentation and digital signatures if
     * required are done in this method. Its the caller responsability to make sure that buffer points
     * to a memory location with at least bufferSize bytes available.
     * @param name the name of the document
     * @param buffer pointer to the start of the data to be written
     * @param bufferSize the number of bytes to be written
     * @returns true if the write succeded, false otherwise
     */
    virtual bool write(const std::string& name, const char* buffer, unsigned bufferSize) =0;
    /**
     * Removes a document from the backend.
     * This operation will remove the document from the backed. It will not fail if the document isn't
     * in the backend.
     * @return true if it succeeds, false otherwise.
     */
    virtual bool remove(const std::string& name) =0;
    virtual double getTotalNetUp() =0;
    virtual double getTotalNetDown() =0;
    virtual double getParallelNetUp() =0;
    virtual double getParallelNetDown() =0;
    virtual void resetTimes() =0;
    virtual ~BackendStorage(){};
};
}//end namespace mie
#endif
