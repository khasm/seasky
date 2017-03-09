package mie.crypto;

public final class TimeSpec {
	
	private static long featureExtractureTime = 0;
	private static long encryptionTimeSymmetric = 0;
	private static long encryptionTimeCbir = 0;
	private static long encryptionTimeMisc = 0;
	private static long indexTime = 0;

	private static int featureTimeThreads;
	private static int encryptionTimeSymmetricThreads;
	private static int encryptionTimeCbirThreads;
	private static int encryptionTimeMiscThreads;
	private static int indexTimeThreads;

	private static long featureTimeStart;
	private static long encryptionTimeSymmetricStart;
	private static long encryptionTimeCbirStart;
	private static long encryptionTimeMiscStart;
	private static long indexTimeStart;
	
	public static void reset(){
		featureExtractureTime = 0;
		encryptionTimeSymmetric = 0;
		encryptionTimeCbir = 0;
		encryptionTimeMisc = 0;
		indexTime = 0;
		featureTimeThreads = 0;
		encryptionTimeSymmetricThreads = 0;
		encryptionTimeCbirThreads = 0;
		encryptionTimeMiscThreads = 0;
		indexTimeThreads = 0;
		featureTimeStart = 0;
		encryptionTimeSymmetricStart = 0;
		encryptionTimeCbirStart = 0;
		encryptionTimeMiscStart = 0;
		indexTimeStart = 0;
	}
	
	public static synchronized long getFeatureTime(){
		return featureExtractureTime;
	}
	
	public static synchronized long getEncryptionTime(){
		return encryptionTimeSymmetric + encryptionTimeCbir + encryptionTimeMisc;
	}

	public static synchronized long getEncryptionCbirTime(){
		return encryptionTimeCbir;
	}

	public static synchronized long getEncryptionSymmetricTime(){
		return encryptionTimeSymmetric;
	}

	public static synchronized long getEncryptionMiscTime(){
		return encryptionTimeMisc;
	}
	
	public static synchronized long getIndexTime(){
		return indexTime;
	}

	protected static synchronized void startFeatureTime(){
		if(0 == featureTimeThreads)
			featureTimeStart = System.nanoTime();
		featureTimeThreads++;
	}

	protected static synchronized void addFeatureTime(){
		if(1 == featureTimeThreads)
			featureExtractureTime += System.nanoTime() - featureTimeStart;
		featureTimeThreads--;
	}

	protected static synchronized void startSymmetricEncryptionTime(){
		if(0 == encryptionTimeSymmetricThreads)
			encryptionTimeSymmetricStart = System.nanoTime();
		encryptionTimeSymmetricThreads++;
	}

	protected static synchronized void addSymmetricEncryptionTime(){
		if(1 == encryptionTimeSymmetricThreads)
			encryptionTimeSymmetric += System.nanoTime() - encryptionTimeSymmetricStart;
		encryptionTimeSymmetricThreads--;
	}

	protected static synchronized void startCbirEncryptionTime(){
		if(0 == encryptionTimeCbirThreads)
			encryptionTimeCbirStart = System.nanoTime();
		encryptionTimeCbirThreads++;
	}

	protected static synchronized void addCbirEncryptionTime(){
		if(1 == encryptionTimeCbirThreads)
			encryptionTimeCbir += System.nanoTime() - encryptionTimeCbirStart;
		encryptionTimeCbirThreads--;
	}

	protected static synchronized void startMiscEncryptionTime(){
		if(0 == encryptionTimeMiscThreads)
			encryptionTimeMiscStart = System.nanoTime();
		encryptionTimeMiscThreads++;
	}

	protected static synchronized void addMiscEncryptionTime(){
		if(1 == encryptionTimeMiscThreads)
			encryptionTimeMisc += System.nanoTime() - encryptionTimeMiscStart;
		encryptionTimeMiscThreads--;
	}

	protected static synchronized void startIndexTime(){
		if(0 == indexTimeThreads)
			indexTimeStart = System.nanoTime();
		indexTimeThreads++;
	}

	protected static synchronized void addIndexTime(){
		if(1 == indexTimeThreads)
			indexTime += System.nanoTime() - indexTimeStart;
		indexTimeThreads--;
	}
}
