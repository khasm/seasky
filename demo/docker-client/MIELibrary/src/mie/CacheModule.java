package mie;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class CacheModule implements Cache {
	
	private static final int DEFAULT_MAX_CACHE_SIZE = /*274388*/104857600;///100*1024*1024; ///100MB
	private static final long DEFAULT_MAX_TTL = 600000000000L;///600000; ///10min
	
	private Map<String, CacheEntry> cache;
	///Queue that keeps track of the order entries were added, prefered over iteration over all entries of cache
	private Queue<String> toRemove;
	private int cacheLimit;
	private int cacheSize;
	private long maxTTL;
	private static double totalReads = 0;
	private static double totalHits = 0;
	private static final Object lock = new Object();
	
	public CacheModule(){
		this(DEFAULT_MAX_CACHE_SIZE, DEFAULT_MAX_TTL);
	}
	
	public CacheModule(int capacity, long ttl){
		cacheLimit = capacity;
		maxTTL = ttl;
		cacheSize = 0;
		cache = new HashMap<String, CacheEntry>();
		toRemove = new LinkedList<String>();
	}

	@Override
	public void addToCache(String id, byte[] contents) {
		if(contents.length <= cacheLimit){
			synchronized(this){
				///make room for new object
				if(cache.containsKey(id)){
					cacheSize -= cache.remove(id).contents.length;
					toRemove.remove(id);
				}
				while(cacheSize+contents.length >= cacheLimit && cacheSize > 0){
					//int it = toRemove.peek();
					//int size = cache.get(it).contents.length;
					//System.out.println("Removing "+it+" with length "+size);
					cacheSize-=cache.remove(toRemove.remove()).contents.length;
				}
				///add object to cache
				//System.out.println("Adding "+id+" to cache with length "+to_cache.length);
				cache.put(id, new CacheEntry(contents));
				cacheSize += contents.length;
				toRemove.add(id);
			}
		}
	}

	@Override
	public byte[] getFromCache(String id) {
		CacheEntry entry;
		synchronized (lock) {
			totalReads++;
		}
		synchronized(this){
			entry = cache.get(id);
		}
		if(entry != null){
			long ttl = Math.abs(System.nanoTime()-entry.lastAccessed);
			if(ttl < maxTTL){
				///cache entry is fresh enough
				synchronized(lock){
					totalHits++;
				}
				return entry.contents;
			}
			else{
				///cache entry is too old
				synchronized(this){
					System.out.println("Removing from cache due to old age");
					int size = cache.remove(id).contents.length;
					cacheSize -= size;
					toRemove.remove(id);
				}
				//System.out.println("Removing "+id+" with length "+size+" and ttl "+ttl);
			}
		}
		return null;
	}

	@Override
	public int getCacheLimit() {
		return cacheLimit;
	}

	@Override
	public void setCacheLimit(int newLimit) {
		if(newLimit < 0){
			cacheLimit = 0;
			clear();
		}
		else{
			cacheLimit = newLimit;
			synchronized(this){
				while(cacheSize > cacheLimit && !toRemove.isEmpty()){
					String id = toRemove.remove();
					cacheSize -= cache.remove(id).contents.length;
				}
			}
		}
	}

	private class CacheEntry{
		
		private byte[] contents;
		private long lastAccessed;
		
		private CacheEntry(byte[] contents){
			this.contents = contents;
			lastAccessed = System.nanoTime();
		}
	}

	@Override
	public int getCacheTTL() {
		return (int)(maxTTL/1000000000L);
	}

	@Override
	public void setCacheTTL(int newTTL) {
		if(newTTL <= 0){
			maxTTL = 0;
			clear();
		}
		else{
			maxTTL = newTTL * 1000000000L;
		}
	}

	@Override
	public void clear() {
		cache.clear();
		toRemove.clear();
		cacheSize = 0;
	}

	@Override
	public void resetStats() {
		synchronized(lock){
			totalHits = 0;
			totalReads = 0;
		}
	}

	@Override
	public double getHitRatio() {
		synchronized(lock){
			if(totalReads > 0)
				return totalHits / totalReads * 100;
			else
				return 0;
		}
	}
}
