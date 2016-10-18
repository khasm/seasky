package mie;

import java.util.List;
import java.util.Map;

public interface ServerConnector {
	
	public boolean index(boolean train, boolean wait);
	
	public boolean sendUnstructredDoc(String name, byte[] img_cipher_text, byte[] txt_cipher_text);
	
	public boolean sendMimeDoc(String name, Map<String, byte[]> features, byte[] mime_cipher_text);
	
	public List<SearchResult> searchUnstructredDoc(byte[] img_features, byte[] txt_features, int nResults);
	
	public List<SearchResult> searchMimeDoc(Map<String, byte[]> features, int nResults);
	
	public byte[] getDoc(String name, boolean useCache);

	public Map<String,String> printStatistics();

	public boolean clearTimes();

	public boolean resetCache();
}
