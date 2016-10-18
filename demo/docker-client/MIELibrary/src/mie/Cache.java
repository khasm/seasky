package mie;

public interface Cache {

	/**
	 * Adds contents to cache.  If an entry with the same id is already there it is removed. This will always succeed 
	 * unless the size of contents is bigger than the cache limit. If the contents size is bigger than the cache limit
	 * no other alterations are made to the cache, otherwise the older entries are removed until enough space is free 
	 * to add contents. No entries are removed if not necessary.
	 * @param id name of the entry
	 * @param contents the contents of the entry
	 */
	public void addToCache(String id, byte[] contents);
	
	/**
	 * Retrieves the entry with name id from the cache. If the TTL of the entry has expired it is removed from the cache
	 * and null is returned.
	 * @param id the name of the entry
	 * @return the contents of the entry, or null if there is no entry or TTL for it has expired
	 */
	public byte[] getFromCache(String id);
	
	/**
	 * Return the max size objects in the cache will take. The actual memory taken by the cache will be bigger
	 * @return the max size of all the objects in the cache
	 */
	public int getCacheLimit();
	
	/**
	 * Sets a new limit for the max size of the cache. 0 or negative values will prevent objects from being inserted
	 * If the new limit is below the current cache size objects will be discarded starting from the older entries until
	 * the cache size is at or below the limit
	 * @param newLimit
	 */
	public void setCacheLimit(int newLimit);
	
	/**
	 * Returns the current max TTL in seconds after which objects will be considered too old and discarded
	 * @return
	 */
	public int getCacheTTL();
	
	/**
	 * Sets a new max TTL. Objects in the cache which have a bigger TTL are not automatically discarded 
	 * until requested or space is needed. Negatives values are converted to a TTL of 0 and will prevent objects
	 * from being returned, but not from being added
	 * @param newTTL the new max TTL
	 */
	public void setCacheTTL(int newTTL);
	
	/**
	 * Empties the cache. All entries will be removed unconditially.
	 */
	public void clear();

	public void resetStats();

	public double getHitRatio();
}
