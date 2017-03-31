package mie;

import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

public interface MIE {
	
	/**
	 * Adds a mime document to the repository. The document will be parsed and text and jpeg images
	 * will be processed with cbir. 
	 * @param name name of the document
	 * @param mime document contents
	 * @return true if the server received the document, false otherwise
	 * @throws MessagingException if there is an error parsing the document
	 */
	public boolean addMime(String name, byte[] mime) throws MessagingException;
	
	/**
	 * Retrieves a mime document from the repository.
	 * @param name name of the document
	 * @return the document contents or null if the document didn't exist or there was a problem
	 * connecting to the server
	 */
	public byte[] getMime(String name, boolean useCache);
	
	/**
	 * Search the repository for documents that match the text and image components of the provided
	 * document
	 * @param mime the document contents to search for
	 * @param nResults maximum number of results returned
	 * @return a ranked list of possible matches or null if there was an error connecting to the server
	 * @throws MessagingException if there is an error processing mime
	 */
	public List<SearchResult> searchMime(byte[] mime, int nResults) throws MessagingException;
	
	/**
	 * Adds an unstructured document composed of an image and a text component to the server. Unlike
	 * mime, each document can only have at most one of each components
	 * @param name name of the document
	 * @param img image component
	 * @param txt text component
	 * @return true if the server received the document, false otherwise
	 */
	public boolean addUnstructredDoc(String name, byte[] img, byte[] txt);
	
	/**
	 * Retrieves the unstructured document with the given id from the server. An unstructured document
	 * is composed of an image, text, or both. If either the image or the text component doesn't exist
	 * an array of length 0 is returned in its place.
	 * @param name the name of the document
	 * @return an array of arrays of bytes of length 2. the first position of the array of arrays will
	 * contain the image contents, or an array of length 0 if the document doesn't have an image, while
	 * the second position will contain the text contents, or an array of length 0 if there is no text
	 * component. Returns null if there was an error connecting to the server
	 */
	public byte[][] getUnstructuredDoc(String name, boolean useCache);
	
	/**
	 * Searchs the server for documents that match the features of the given image and/or text
	 * components. An ordered list will be return with both the id and the score of the matching
	 * documents
	 * @param img the image contents
	 * @param txt the text contents
	 * @param nResults maximum number of results returned
	 * @return an ordered list with the matching documents, or null if there was an error connecting to
	 * the server
	 */
	public List<SearchResult> searchUnstructuredDocument(byte[] img, byte[] txt, int nResults);
	
	/**
	 * Sends the index command to the server. This will start the training and indexing operation in the
	 * background
	 * @param train force a training phase
	 * @param wait wait for the index to finish
	 * @return false if there was a problem connecting to the server
	 */
	public boolean index(boolean train, boolean wait);
	
	/**
	 * Returns a map with times retrieved from the server
	 * @return a map with measurements
	 */
	public Map<String,String> printServerStatistics();
	
	/**
	 * Returns the current max size, in bytes, of the cache, a value of 0 means cache is not used
	 * @return the current cache limit
	 */
	public int getCacheLimit();
	
	/**
	 * Sets a new limit for the cache. Negative values or 0 disable the cache
	 * @param newLimit the new limit
	 */
	public void setCacheLimit(int newLimit);
	
	/**
	 * Returns how long an object will be kept in cache
	 * @return
	 */
	public int getCacheTTL();
	
	/**
	 * Sets a new maximum TTL for the cache, Values of 0 or negative disable the cache
	 * @param TTL
	 */
	public void setCacheTTL(int TTL);
	
	/**
	 * Remove all entries from the cache
	 */
	public void clearCache();

	/**
	 * Reset all server time measurements
	 * @return false if there was a problem connecting to the server
	 */
	public boolean clearServerTimes();

	public void resetServerCache();

	public long getNetworkTime();

	public String getServerIp();

	public boolean wipe();

	public boolean setServerCache(boolean useCache);

	public double getCacheHitRatio();
	
}
