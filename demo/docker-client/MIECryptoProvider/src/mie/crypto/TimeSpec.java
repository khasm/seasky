package mie.crypto;

public final class TimeSpec {
	
	private static long featureExtractureTime = 0;
	private static long encryptionTimeSymmetric = 0;
	private static long encryptionTimeCbir = 0;
	private static long encryptionTimeMisc = 0;
	private static long indexTime = 0;
	
	public static void reset(){
		featureExtractureTime = 0;
		encryptionTimeSymmetric = 0;
		encryptionTimeCbir = 0;
		encryptionTimeMisc = 0;
		indexTime = 0;
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
	
	protected static synchronized void addFeatureTime(long time){
		featureExtractureTime += time;
	}
	
	protected static synchronized void addSymmetricEncryptionTime(long time) {
		encryptionTimeSymmetric += time;
	}
	
	protected static synchronized void addCbirEncryptionTime(long time) {
		encryptionTimeCbir += time;
	}

	protected static synchronized void addMiscEncryptionTime(long time) {
		encryptionTimeMisc += time;
	}

	protected static synchronized void addIndexTime(long time) {
		indexTime += time;
	}

}
