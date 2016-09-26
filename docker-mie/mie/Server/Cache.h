#ifndef MIE_CACHE_H
#define MIE_CACHE_H

#include <string>
#include <vector>
#include "Status.h"

namespace MIE{

/**
 * Base classe for the cache module.
 * Specific cache implementations should subclass and implement these methods.
 */
class Cache{
  public:
  	/**
  	 * Read a document from the cache.
  	 * The document will be read and stored in the buffer. If the document was not found no changes
  	 * to the buffer are made, otherwise all content is cleared before adding the document data.
  	 * @param name the name of the document
  	 * @param buffer the buffer to hold the document data
  	 * @return the size of the document read, or a negative value if the file couldn't be read
  	 */
    virtual int read(const std::string& name, std::vector<char>& buffer) =0;
    /**
     * Write a document to the cache
     * The document will be written to the cache unless it's too big to fit in it.
     * @param name the name of the document
     * @param buffer the document contents
     * @param bufferSize the size of the document contents
     * @return NO_ERRORS if the write succeed, any other value indicates an error 
     */
    virtual int write(const std::string& name, const char* buffer, size_t bufferSize) =0;
    /**
     * Removes a document from the cache.
     * The document will be removed from the cache. This method only returns an error status if
     * something went wrong with the request processing, it will not fail if the document
     * wasn't found in the cache.
     * @param name the name of the document to be removed
     * @return NO_ERRORS unless an internal error occurred.
     */
    virtual int remove(const std::string& name) =0;
    /**
     * Removes all documents from the cache.
     * Removes all documents from the cache imediatilly. It will only fail if some internal error
     * occured.
     * @return NO_ERRORS unless an internal error occurred.
     */
    virtual int clear() =0;

    virtual double getHitRatio() =0;
    virtual void resetStats() =0;
    virtual ~Cache(){};
};

}//end namespace mie
#endif