package mie.utils;

import java.util.Map;
import java.util.HashMap;

public class DataSerie {

	protected enum Stat {
		//geral
		TOTAL_THREADS("Number of Threads", true, null),
		TOTAL_OPERATIONS("Operations", true, null),
		//bandwidth
		UPLOAD("Bytes Uploaded", true, Unit.getDefault(false)),
		SEARCH("Bytes Searched", true, Unit.getDefault(false)),
		DOWNLOAD("Bytes Downloaded", true, Unit.getDefault(false)),
		//times
		CLIENT_INDEX("Client Index", true, Unit.getDefault(true)),
		FEATURE_EXTRACTION("Feature Extraction", true, Unit.getDefault(true)),
		ENCRYPTION_TOTAL("Encryption Total", true, Unit.getDefault(true)),
		ENCRYPTION_SYMMETRIC("Encryption Symmetric", true, Unit.getDefault(true)),
		ENCRYPTION_CBIR("Encryption Cbir", true, Unit.getDefault(true)),
		ENCRYPTION_MISC("Encryption Misc", true, Unit.getDefault(true)),
		CLIENT_NETWORK_TIME("Cloud Time", true, Unit.getDefault(true)),
		TOTAL_TIME("Total Time", true, Unit.getDefault(true)),
		SERVER_INDEX("Index time", false, Unit.getDefault(true)),
		TRAIN_TIME("Train time", false, Unit.getDefault(true)),
		SEARCH_TIME("Search time", false, Unit.getDefault(true)),
		SERVER_NETWORK_TIME("Network time", false, Unit.getDefault(true)),
		NETWORK_FEATURE_TIME("Network feature time", false, Unit.getDefault(true)),
		NETWORK_INDEX_TIME("Network index time", false, Unit.getDefault(true)),
		NETWORK_ADD_TIME("Network add time", false, Unit.getDefault(true)),
    	NETWORK_GET_TIME("Network get time", false, Unit.getDefault(true)),
    	NETWORK_PARALLEL_ADD("Network parallel add", false, Unit.getDefault(true)),
    	NETWORK_PARALLEL_GET("Network parallel get", false, Unit.getDefault(true)),
    	NETWORK_UPLOAD_TIME("Network upload time", false, Unit.getDefault(true)),
    	NETWORK_DOWNLOAD_TIME("Network download time", false, Unit.getDefault(true)),
    	NETWORK_PARALLEL_UPLOAD("Network parallel upload", false, Unit.getDefault(true)),
    	NETWORK_PARALLEL_DOWNLOAD("Network parallel download", false, Unit.getDefault(true)),
    	//cache
    	CLIENT_HIT_RATIO("Client Cache Hit Ratio", true, null),
		SERVER_HIT_RATIO("Server Cache Hit Ratio", false, null),
		//search
		HIT_RATIO("Search precision", true, null),
		AVERAGE_SCORE("Average search score", true, null);

		private final String aKey;
		private final boolean aClient;
		private final Unit aDefaultUnit;

		Stat(String key, boolean client, Unit defaultUnit) {
			aKey = key;
			aClient = client;
			aDefaultUnit = defaultUnit;
		}

		protected String getKey() {
			return aKey;
		}

		protected boolean isClientTime() {
			return aClient;
		}

		protected Unit getDefaultUnit() {
			return aDefaultUnit;
		}

		protected static Stat getStat(String key) {
			Stat ret = null;
			Stat[] values = Stat.values();
			int i = 0;
			while(null == ret && i < values.length){
				if(values[i].aKey.equalsIgnoreCase(key))
					ret = values[i];
				else
					i++;
			}
			return ret;
		}
	}

	protected enum Unit {
		//time
		NANO("nanosecond", 'n', true),
		MILLI("millisecond", 'm', true),
		SECONDS("second", 's', true),
		MINUTES("minute", 'M', true),
		HOURS("hour", 'h', true),
		//quantity
		BYTE("B", 'b', false),
		KILO("kB", 'k', false),
		MEGA("MB", 'm', false),
		GIGA("GB", 'g', false);

		private String aUnit;
		private char aId;
		private boolean aTime;

		Unit(String unit, char identifier, boolean time) {
			aUnit = unit;
			aId = identifier;
			aTime = time;
		}

		public String toString() {
			return aUnit;
		}

		protected static Unit getDefault(boolean time) {
			if(time)
				return SECONDS;
			else
				return BYTE;
		}

		protected boolean isTime() {
			return aTime;
		}

		protected static Unit getUnit(char prefix) {
			Unit ret = null;
			Unit[] values = Unit.values();
			int i = 0;
			while(null == ret && i < values.length){
				if(values[i].aId == prefix)
					ret = values[i];
				else
					i++;
			}
			return ret;
		}
	}

	private String aTitle;
	private Map<Stat,Double> aStats;

	public DataSerie(int nThreads, int nOperations, long bytesUpload, long bytesSearch,
		long bytesDownload, long indexTime, long featureExtractionTime, long encryptionTime,
		long encryptionSymmetricTime, long encryptionCbirTime, long encryptionMiscTime,
		long networkTime, long totalTime, boolean indexWait, Map<String,String> serverStats,
		SearchStats searchStats, double hitRatio, String title) {
		aTitle = title;
		aStats = new HashMap<Stat,Double>();
		double indexWaitValue = 0;
		for(String key: serverStats.keySet()){
			double value = Double.parseDouble(serverStats.get(key));
			if(!key.equals(Stat.SERVER_HIT_RATIO.getKey())){
				value *= 1000000000;
			}
			Stat keyStat = Stat.getStat(key);
			aStats.put(keyStat, value);
			if(key.equalsIgnoreCase("Train time")||key.equalsIgnoreCase("Network feature time")||
				key.equalsIgnoreCase("Network index time")||key.equalsIgnoreCase("Index time")){
				indexWaitValue += value;
			}
		}
		if(indexWait)
			networkTime -= indexWaitValue;
		aStats.put(Stat.TOTAL_THREADS, (double)nThreads);
		aStats.put(Stat.TOTAL_OPERATIONS, (double)nOperations);
		aStats.put(Stat.CLIENT_INDEX, (double)indexTime);
		aStats.put(Stat.FEATURE_EXTRACTION, (double)featureExtractionTime);
		aStats.put(Stat.ENCRYPTION_TOTAL, (double)encryptionTime);
		aStats.put(Stat.ENCRYPTION_SYMMETRIC, (double)encryptionSymmetricTime);
		aStats.put(Stat.ENCRYPTION_CBIR, (double)encryptionCbirTime);
		aStats.put(Stat.ENCRYPTION_MISC, (double)encryptionMiscTime);
		aStats.put(Stat.CLIENT_NETWORK_TIME,(double)networkTime);
		aStats.put(Stat.TOTAL_TIME, (double)totalTime);
		aStats.put(Stat.UPLOAD, (double)bytesUpload);
		aStats.put(Stat.SEARCH, (double)bytesSearch);
		aStats.put(Stat.DOWNLOAD, (double)bytesDownload);
		aStats.put(Stat.CLIENT_HIT_RATIO, hitRatio);
		aStats.put(Stat.HIT_RATIO, searchStats.getHitRatio());
		aStats.put(Stat.AVERAGE_SCORE, searchStats.getAverageScore());
	}

	public String getTitle() {
		return aTitle;
	}

	public double getStat(Stat stat) {
		return getStat(stat, stat.getDefaultUnit());
	}

	public double getStat(Stat stat, Unit prefix) {
		if(null == prefix)
			return aStats.get(stat);
		if(prefix.isTime())
			return getTimes(prefix, aStats.get(stat));
		else
			return convertBytes(prefix, aStats.get(stat));
	}

	@SuppressWarnings("fallthrough")
	private double getTimes(Unit unit, double time) {
		switch(unit){
			case HOURS:
			time /= 60;
			case MINUTES:
			time /= 60;
			case SECONDS:
			time /= 1000;
			case MILLI:
			time /= 1000000;
		}
		return time;
	}

	@SuppressWarnings("fallthrough")
	private double convertBytes(Unit prefix, double bytes) {
		switch(prefix){
			case GIGA:
			bytes /= 1024;
			case MEGA:
			bytes /= 1024;
			case KILO:
			bytes /= 1024;
		}
		return bytes;
	}
}