package mie;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;

import com.sun.mail.util.BASE64DecoderStream;


public class MIEClient implements MIE {
	
	private static final int DEFAULT_SERVER_PORT = 9978;
	private static final String DEFAULT_SERVER_HOST = "127.0.0.1";
	
	private MIECrypto crypto;
	protected ServerConnector server;
	protected Cache cache;
	private boolean aUseCache;
	private boolean aVerified; //not actually used yet, proof of concept
	
	public MIEClient() throws NoSuchAlgorithmException, NoSuchPaddingException, IOException{
		this(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
	}
	
	public MIEClient(String host, int port) throws NoSuchAlgorithmException, NoSuchPaddingException, IOException{
		server = new ServerConnectorModule(host, port);
		crypto = new MIECryptoModule();
		cache = new CacheModule();
		aUseCache = false;
		System.out.println(aUseCache);
		TpmVerifier tpm = new TpmVerifier();
		aVerified = tpm.verify(host);
		System.out.println("verified: "+aVerified);
	}
	
	public MIEClient(String host) throws NoSuchAlgorithmException, NoSuchPaddingException, IOException{
		this(host, DEFAULT_SERVER_PORT);
	}
	
	@Override
	public int getCacheLimit() {
		return cache.getCacheLimit();
	}
	
	@Override
	public void setCacheLimit(int newLimit) {
		if(newLimit <= 0){
			aUseCache = false;
		}
		else{
			aUseCache = true;
		}
		cache.setCacheLimit(newLimit);
	}

	@Override
	public int getCacheTTL() {
		return cache.getCacheTTL();
	}

	@Override
	public void setCacheTTL(int TTL) {
		if(TTL <= 0){
			aUseCache = false;
		}
		else{
			aUseCache = true;
		}
		cache.setCacheTTL(TTL);
		
	}

	@Override
	public void clearCache() {
		cache.clear();
	}

	@Override
	public boolean addUnstructredDoc(String name, byte[] img, byte[] txt) {
		try {
			byte[] img_cipher_text = crypto.encryptImg(img);
			byte [] txt_cipher_text = crypto.encryptTxt(txt);
			long start = System.nanoTime();
			boolean ret = server.sendUnstructredDoc(name, img_cipher_text, txt_cipher_text);
			long time = System.nanoTime()-start;
			Main.networkTime += time;
			return ret;
		} catch (IllegalBlockSizeException e) {
			///should not happen
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public byte[][] getUnstructuredDoc(String name, boolean useCache) {
		byte[][] result = new byte[2][];
		byte[] doc = null;
		if(aUseCache && useCache){
			doc = cache.getFromCache(name);
		}
		if(doc == null){
			long start = System.nanoTime();
			doc = server.getDoc(name, useCache);
			long time = System.nanoTime()-start;
			Main.networkTime += time;
			if(doc == null)
				return null;
			///both parts length
			ByteBuffer buffer = ByteBuffer.wrap(doc);
			int img_size = buffer.getInt();
			int txt_size = buffer.getInt();
			///process img
			if(img_size > 0){
				byte[] imgc = new byte[img_size];
				buffer.get(imgc);
				try {
					byte[] img = crypto.decryptImg(imgc);
					result[0] = img;
				} catch (IllegalBlockSizeException | BadPaddingException e) {
					e.printStackTrace();
					result[0] = new byte[0];
				}
			}
			else{
				result[0] = new byte[0];
			}
			///process txt
			if(txt_size > 0){
				byte[] txtc = new byte[txt_size];
				buffer.get(txtc);
				try {
					byte[] txt = crypto.decryptTxt(txtc);
					result[1] = txt;
				} catch (IllegalBlockSizeException | BadPaddingException e) {
					e.printStackTrace();
					result[1] = new byte[0];
				}
			}
			else{
				result[1] = new byte[0];
			}
			ByteBuffer cont = ByteBuffer.allocate(4+result[0].length+result[1].length);
			cont.putInt(result[0].length);
			cont.put(result[0]);
			cont.put(result[1]);
			if(aUseCache){
				cache.addToCache(name, cont.array());
			}
			return result;
		}
		else{
			ByteBuffer cont = ByteBuffer.wrap(doc);
			int img_size = cont.getInt();
			result[0] = new byte[img_size];
			cont.get(result[0]);
			result[1] = new byte[cont.remaining()];
			cont.get(result[1]);
			return result;
		}
	}

	@Override
	public boolean addMime(String name, byte[] mime) throws MessagingException {
		Map<String, byte[]> cbir_features = processMimeDocument(mime);
		try {
			byte[] cipher_doc = crypto.encryptMime(mime);
			long start = System.nanoTime();
			boolean ret = server.sendMimeDoc(name, cbir_features, cipher_doc);
			long time = System.nanoTime()-start;
			Main.networkTime += time;
			return ret;
		} catch (IllegalBlockSizeException e) {
			///should not happen
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public byte[] getMime(String name, boolean useCache) {
		byte[] doc = null;
		if(aUseCache && useCache){
			doc = cache.getFromCache(name);
		}
		if(doc == null){
			long start = System.nanoTime();
			doc = server.getDoc(name, useCache);
			long time = System.nanoTime()-start;
			Main.networkTime += time;
			if(doc == null)
				return null;
			try {
				byte[] contents = crypto.decryptMime(doc);
				if(aUseCache){
					cache.addToCache(name, contents);
				}
				return contents;
			} catch (IllegalBlockSizeException | BadPaddingException e) {
				e.printStackTrace();
				return null;
			}
		}
		return doc;
	}

	@Override
	public boolean index(boolean train) {
		long start = System.nanoTime();
		boolean ret = server.index(train);
		long time = System.nanoTime()-start;
		Main.networkTime += time;
		return ret;
	}

	@Override
	public List<SearchResult> searchUnstructuredDocument(byte[] img, byte[] txt, int nResults) {
		byte [] img_features = crypto.cbirImg(img);
		byte [] txt_features = crypto.cbirTxt(txt);
		long start = System.nanoTime();
		List<SearchResult> ret = server.searchUnstructredDoc(img_features, txt_features, nResults);
		long time = System.nanoTime()-start;
		Main.networkTime += time;
		return ret;
	}

	@Override
	public List<SearchResult> searchMime(byte[] mime, int nResults) throws MessagingException {
		Map<String, byte[]> cbir_features = processMimeDocument(mime);
		long start = System.nanoTime();
		List<SearchResult> ret = server.searchMimeDoc(cbir_features, nResults);
		long time = System.nanoTime()-start;
		Main.networkTime += time;
		return ret;
		
	}

	@Override
	public Map<String,String> printServerStatistics() {
		Map<String,String> tmp = server.printStatistics();
		tmp.put("Client cache hit ratio", cache.getHitRatio()+"");
		return tmp;
	}

	@Override
	public boolean clearServerTimes() {
		boolean ret = server.clearTimes();
		cache.resetStats();
		return ret;
	}

	@Override
	public void resetServerCache() {
		server.resetCache();
	}
	
	/**
	 * Scans a mime document for text and image parts and returns the cbir encrypted features of each part
	 * in a map
	 * @param msg the mime document to be processed
	 * @return a map containing the encrypted features of every text and image part
	 * @throws MessagingException if an error occurs during the process of the mime document
	 */
	private Map<String, byte[]> processMimeDocument(byte[] mime) throws MessagingException{
		ByteArrayInputStream din = new ByteArrayInputStream(mime);
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s, din);
		String contentType = msg.getContentType();
		Map<String, byte[]> ret= new HashMap<String, byte[]>();
		if(contentType.startsWith("text")){
			try{
				String content = (String)msg.getContent();
				ret.put("textFeatures"+0, crypto.cbirTxt(content.getBytes()));
			} catch(IOException e){
				e.printStackTrace();
			}
		}
		else if(contentType.equalsIgnoreCase("image/jpeg")){
			try {
				Object img = msg.getContent();
				ret.put("imgFeatures"+0, processImg(img));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if(contentType.startsWith("multipart")){
			try {
				Multipart mp = (Multipart) msg.getContent();
				//System.out.println("bodycount: "+mp.getCount());
				ret.putAll(processMultipart(mp, 0));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}
	
	private byte[] processImg(Object img) throws IOException{
		BASE64DecoderStream obj = (BASE64DecoderStream)img;
		byte[] tmp = new byte[obj.available()];
		int read = 0, j;
		while((j = obj.read()) != -1){
			tmp[read++]=(byte)j;
		}
		byte[] dec = new byte[read];
		System.arraycopy(tmp, 0, dec, 0, read);
		obj.close();
		return crypto.cbirImg(dec);
	}
	
	private Map<String, byte[]> processMultipart(Multipart mp, int i) throws MessagingException, IOException{
		Map<String, byte[]> ret = new HashMap<String, byte[]>();
		int id = i;
		for(int j = 0; j < mp.getCount(); j++){
			MimeBodyPart body = (MimeBodyPart) mp.getBodyPart(j);
			String type = body.getContentType();
			if(type.startsWith("text/")){
				String content = (String)body.getContent();
				ret.put("textFeatures"+id++, crypto.cbirTxt(content.getBytes()));
			}
			else if(type.equalsIgnoreCase("image/jpeg")){
				Object img = body.getContent();
				ret.put("imgFeatures"+id++, processImg(img));
			}
			else if(type.startsWith("multipart/")){
				Multipart mp2 = (Multipart)body.getContent();
				ret.putAll(processMultipart(mp2, id+1));
			}
		}
		return ret;
	}
}
