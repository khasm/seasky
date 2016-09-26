package mie;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ServerConnectorModule implements ServerConnector {

	private static final byte NO_ERRORS = 1;
	private static final byte OP_FAILED = 0;
	
	private String server_host;
	private int server_port;
	
	public ServerConnectorModule(String host, int port) {
		this.server_host = host;
		this.server_port = port;
	}

	@Override
	public boolean sendUnstructredDoc(String name, byte[] img_cipher_text, byte[] txt_cipher_text) {
		///process image cipher text
		ByteBuffer buffer = ByteBuffer.wrap(img_cipher_text);
		///skip first byte
		buffer.position(1);
		int cbir_length = buffer.getInt();
		byte[] img_features = new byte[cbir_length];
		buffer.get(img_features);
		byte[] img_cipher = new byte[buffer.remaining()];
		buffer.get(img_cipher);
		
		ByteBuffer b = ByteBuffer.wrap(img_features, 0, cbir_length);
		///skip first byte
		b.position(1);
		int n_features = b.getInt();
		int imgFeatureSize = b.getInt();
		
		///process text cipher text
		buffer = ByteBuffer.wrap(txt_cipher_text);
		///skip first byte
		buffer.position(1);
		cbir_length = buffer.getInt();
		byte[] txt_features = new byte[cbir_length];
		buffer.get(txt_features);
		byte[] txt_cipher = new byte[buffer.remaining()];
		buffer.get(txt_cipher);
		
		ByteBuffer t = ByteBuffer.wrap(txt_features);
		t.position(1);
		int n_keywords = t.getInt();
		int keywordSize = t.getInt();
		
		///setup buffer to send
		buffer = ByteBuffer.allocate(9*4+name.length()+img_features.length-9+txt_features.length-
			9+img_cipher.length+txt_cipher.length);
		buffer.putInt(name.length());
		buffer.put(name.getBytes());

		///cipher text lengths
		buffer.putInt(8+img_cipher.length+txt_cipher.length);
		buffer.putInt(img_cipher.length);
		buffer.putInt(txt_cipher.length); 
		
		///add img cipher text to buffer;
		buffer.put(img_cipher);
		
		///add txt cipher text to buffer;
		buffer.put(txt_cipher);
		//System.out.printf("id: %s size: %d pos: %d\n", name, buffer.limit(), buffer.position());
		
		///cbir metadata
		buffer.putInt(1);
		buffer.putInt(imgFeatureSize);

		buffer.putInt(n_keywords);
		buffer.putInt(keywordSize);
		buffer.put(img_features, 1, 4); ///n_features
		///add img features
		for(int i = 0; i < n_features; i++){
			for(int j = 0; j < imgFeatureSize; j++){
				float f = b.getFloat();
				buffer.putInt((int)f);
			}
		}
		///add txt features
		buffer.put(txt_features, t.position(), t.remaining());
		
		Socket sock = connect();
		if(null == sock)
			return false;
		byte[] op = new byte[1];
		op[0] = 'a';
		try {
			send(sock, op);
			send(sock, zip(buffer.array()));
			receive(sock, op);
			sock.close();
			if(NO_ERRORS == op[0])
				return true;
			else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean sendMimeDoc(String name, Map<String, byte[]> features, byte[] mime_cipher_text) {
		int cbir_length = 0, imgFeatureSize = 0, keywordSize = 0, n_keywords = 0;
		Queue<String> img_queue = new LinkedList<String>();
		Queue<String> txt_queue = new LinkedList<String>();
		for(String key: features.keySet()){
			//System.out.println(key);
			if(key.startsWith("imgFeatures")){
				byte[] img_features = features.get(key);
				ByteBuffer tmp = ByteBuffer.wrap(img_features);
				tmp.position(5);
				//tmp.getInt();
				imgFeatureSize = tmp.getInt();
				cbir_length += tmp.remaining();
				img_queue.add(key);
			}
			else if(key.startsWith("textFeatures")){
				byte[] txt_features = features.get(key);
				ByteBuffer tmp = ByteBuffer.wrap(txt_features);
				tmp.position(1);
				n_keywords += tmp.getInt();
				keywordSize = tmp.getInt();
				cbir_length += tmp.remaining();
				txt_queue.add(key);
			}
		}
		///server is not ready for more than one image per document yet
		//assert(n_text <= 1 && n_img <= 1);
		///setup buffer to send
		ByteBuffer buffer = ByteBuffer.allocate(6*4+name.length()+cbir_length+mime_cipher_text.length+img_queue.size()*4);
		buffer.putInt(name.length());
		buffer.put(name.getBytes());
		///cipher text lengths
		buffer.putInt(mime_cipher_text.length);
		
		///add mime cipher text 
		buffer.put(mime_cipher_text);
		///cbir metadata
		buffer.putInt(img_queue.size());
		buffer.putInt(imgFeatureSize);
		buffer.putInt(n_keywords);
		buffer.putInt(keywordSize);
		
		///add images features
		while(!img_queue.isEmpty()){
			byte[] img_features = features.get(img_queue.remove());
			ByteBuffer b = ByteBuffer.wrap(img_features);
			b.position(1);
			int n_features = b.getInt();
			b.getInt();
			buffer.putInt(n_features);
			for(int i = 0; i < n_features; i++){
				for(int j = 0; j < imgFeatureSize; j++){
					float f = b.getFloat();
					buffer.putInt((int)f);
				}
			}
		}
		///add text features
		while(!txt_queue.isEmpty()){
			byte[] txt_features = features.get(txt_queue.remove());
			ByteBuffer b = ByteBuffer.wrap(txt_features);
			b.position(1);
			b.getInt();
			b.getInt();
			while(b.hasRemaining()){
				buffer.put(b.get());
			}
		}
		Socket sock = connect();
		if(null == sock)
			return false;
		byte[] op = new byte[1];
		op[0] = 'a';
		try {
			send(sock, op);
			send(sock, zip(buffer.array()));
			receive(sock, op);
			sock.close();
			if(NO_ERRORS == op[0])
				return true;
			else return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean index(boolean train) {
		byte[] buffer = new byte[2];
		buffer[0] = 'i';
		if(train)
			buffer[1] = 'f';
		else
			buffer[1] = 0;
		return connectAndSend(buffer);
	}

	@Override
	public boolean resetCache() {
		byte[] buffer = new byte[1];
		buffer[0] = 'r';
		return connectAndSend(buffer);
	}
	
	public List<SearchResult> searchUnstructredDoc(byte[] img_features, byte[] txt_features, int nResults){
		ByteBuffer bi = ByteBuffer.wrap(img_features);
		bi.position(1);
		int n_features = bi.getInt();
		int imgFeatureSize = bi.getInt();
		
		ByteBuffer bt = ByteBuffer.wrap(txt_features);
		bt.position(1);
		int n_keywords = bt.getInt();
		int keywordSize = bt.getInt();
		
		///setup buffer to send
		ByteBuffer buffer = ByteBuffer.allocate(6*4+img_features.length-9+txt_features.length-9);

		///cbir metadata
		buffer.putInt(1);
		buffer.putInt(nResults);
		buffer.putInt(imgFeatureSize);
		
		buffer.putInt(n_keywords);
		buffer.putInt(keywordSize);
		///cbir features
		buffer.put(img_features, 1, 4); ///number of features
		for(int i = 0; i < n_features; i++){
			for(int j = 0; j < imgFeatureSize; j++){
				float f = bi.getFloat();
				buffer.putInt((int)f);
			}
		}
		buffer.put(txt_features, bt.position(), bt.remaining());
				
		Socket sock = connect();
		if(null == sock)
			return null;
		byte[] op = new byte[1];
		op[0] = 's';
		try{
			send(sock, op);
			send(sock, zip(buffer.array()));
			List<SearchResult> res = receiveQueryResults(sock);
			sock.close();
			return res;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public List<SearchResult> searchMimeDoc(Map<String, byte[]> features, int nResults) {
		int cbir_length = 0, imgFeatureSize = 0, keywordSize = 0, n_keywords = 0;
		Queue<String> img_queue = new LinkedList<String>();
		Queue<String> txt_queue = new LinkedList<String>();
		for(String key: features.keySet()){
			//System.out.println(key);
			if(key.startsWith("imgFeatures")){
				byte[] img_features = features.get(key);
				ByteBuffer tmp = ByteBuffer.wrap(img_features);
				tmp.position(5);
				//tmp.getInt();
				imgFeatureSize = tmp.getInt();
				cbir_length += tmp.remaining();
				img_queue.add(key);
			}
			else if(key.startsWith("textFeatures")){
				byte[] txt_features = features.get(key);
				ByteBuffer tmp = ByteBuffer.wrap(txt_features);
				tmp.position(1);
				n_keywords += tmp.getInt();
				keywordSize = tmp.getInt();
				cbir_length += tmp.remaining();
				txt_queue.add(key);
			}
		}
		ByteBuffer buffer = ByteBuffer.allocate(5*4+cbir_length+4*img_queue.size());
		///cbir metadata
		buffer.putInt(img_queue.size());
		buffer.putInt(nResults);
		buffer.putInt(imgFeatureSize);
		buffer.putInt(n_keywords);
		buffer.putInt(keywordSize);
		///add images features
		while(!img_queue.isEmpty()){
			byte[] img_features = features.get(img_queue.remove());
			ByteBuffer b = ByteBuffer.wrap(img_features);
			b.position(1);
			int n_features = b.getInt();
			b.getInt();
			buffer.putInt(n_features);
			for(int i = 0; i < n_features; i++){
				for(int j = 0; j < imgFeatureSize; j++){
					float f = b.getFloat();
					buffer.putInt((int)f);
				}
			}
		}
		///add text features
		while(!txt_queue.isEmpty()){
			byte[] txt_features = features.get(txt_queue.remove());
			ByteBuffer b = ByteBuffer.wrap(txt_features);
			b.position(1);
			b.getInt();
			b.getInt();
			while(b.hasRemaining()){
				buffer.put(b.get());
			}
		}
		Socket sock = connect();
		if(null == sock)
			return null;
		byte[] op = new byte[1];
		op[0] = 's';
		try {
			send(sock, op);
			send(sock, zip(buffer.array()));
			List<SearchResult> res = receiveQueryResults(sock);
			sock.close();
			return res;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public byte[] getDoc(String name, boolean useCache) {
		Socket sock = connect();
		if(null == sock)
			return null;
		///send request
		byte[] buffer = new byte[6+name.length()];
		ByteBuffer b = ByteBuffer.wrap(buffer, 2, buffer.length-2);
		buffer[0] = (byte)'g';
		if(useCache)
			buffer[1] = (byte)'f';
		else
			buffer[0] = (byte)0;
		b.putInt(name.length());
		b.put(name.getBytes());
		byte[] status = new byte[1];
		try{
			send(sock, buffer);
			///get first part of the response
			receive(sock, status);
		}
		catch(IOException e){
			e.printStackTrace();
			return null;
		}
		if(NO_ERRORS == status[0]){
			buffer = new byte[16];
			try {
				receive(sock, buffer);
				b = ByteBuffer.wrap(buffer);
				long zipSize = b.getLong();
				long dataSize = b.getLong();
				///receive data
				buffer = new byte[(int)zipSize];
				receive(sock, buffer);
				sock.close();
				return unzip(buffer, (int)dataSize);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		else{
			return null;
		}
	}

	@Override
	public Map<String,String> printStatistics() {
		byte[] buffer = new byte[1];
		buffer[0] = 'p';
		Socket socket = connect();
		if(null == socket)
			return null;
		try {
			send(socket, buffer);
			///get first part of the response
			buffer = new byte[17];
			receive(socket, buffer);
			ByteBuffer b = ByteBuffer.wrap(buffer);
			b.position(1);
			long zipSize = b.getLong();
			long dataSize = b.getLong();
			//System.out.println("Receiving "+zipSize+" "+dataSize);
			///receive data
			buffer = new byte[(int)zipSize];
			receive(socket, buffer);
			socket.close();
			byte[] data = unzip(buffer, (int)dataSize);
			String d = new String(data);
			Map<String, String> stats = new HashMap<String, String>();
			int eIndex = d.indexOf(':');
			int bIndex = 0;
			while(eIndex != -1){
				String key = d.substring(bIndex, eIndex);
				bIndex = eIndex+2;
				eIndex = d.indexOf('\n', eIndex);
				String value = d.substring(bIndex, eIndex);
				stats.put(key, value);
				bIndex = eIndex+1;
				eIndex = d.indexOf(':', bIndex);
			}
			return stats;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean clearTimes() {
		byte[] buffer = new byte[1];
		buffer[0] = 'c';
		return connectAndSend(buffer);
	}

	private List<SearchResult> receiveQueryResults(Socket sock){
		List<SearchResult> results = new ArrayList<SearchResult>(0);
		try {
			byte[] results_size = new byte[9];
			InputStream in = sock.getInputStream();
			int n = 0;
			while((n += in.read(results_size, n, results_size.length-n)) < results_size.length);
			ByteBuffer buffer = ByteBuffer.wrap(results_size);
			buffer.position(1);
			int nResults = buffer.getInt();
			int buffer_length = buffer.getInt();
			results = new ArrayList<SearchResult>(nResults);
			///read response
			n = 0;
			byte[] tmp = new byte[buffer_length];
			while((n += in.read(tmp, n, tmp.length-n)) < tmp.length);
			buffer = ByteBuffer.wrap(tmp);
			for(int i = 0; i < nResults; i++){
				///read name
				int name_size = buffer.getInt();
				byte[] name_buffer = new byte[name_size];
				buffer.get(name_buffer);
				String name = new String(name_buffer);
				///read score
				long tmp_float;
				ByteOrder b;
				if((b = buffer.order()) != ByteOrder.LITTLE_ENDIAN){
					buffer.order(ByteOrder.LITTLE_ENDIAN);
					tmp_float = buffer.getLong();
					buffer.order(b);
				}
				else{
					tmp_float = buffer.getInt();
				}
				double f = Double.longBitsToDouble(tmp_float);
				//System.out.println("Got "+name+" with score: "+f);
				results.add(new SearchResult(name, f));
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return results;
	}

	
	private Socket connect(){
		try {
			Socket sock = new Socket(server_host, server_port);
			return sock;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@SuppressWarnings("resource")
	private void send(Socket sock, byte[] buffer) throws IOException{
		OutputStream out = sock.getOutputStream();
		out.write(buffer);
	}
	
	@SuppressWarnings("resource")
	private void receive(Socket sock, byte[] buffer) throws IOException{
		InputStream in = sock.getInputStream();
		int n = 0;
		while(((n += in.read(buffer, n, buffer.length-n)) > 0) && n < buffer.length);
	}
	
	private boolean connectAndSend(byte[] buffer){
		Socket sock = connect();
		if(null == sock)
			return false;
		try {
			send(sock, buffer);
			byte[] status = new byte[1];
			receive(sock, status);
			sock.close();
			if(NO_ERRORS == status[0])
				return true;
			else
				return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private byte[] zip(byte[] data){
		Deflater compresser = new Deflater();
		compresser.setInput(data);
		compresser.finish();
		byte[] tmp = new byte[500];
		Queue<Byte> buffer = new LinkedList<Byte>();
		int compressed;
		while((compressed = compresser.deflate(tmp)) > 0){
			for(int i = 0; i < compressed; i++){
				buffer.add(tmp[i]);
			}
		}
		byte[] ret = new byte[buffer.size()+16];
		ByteBuffer bb = ByteBuffer.wrap(ret, 0, 16);
		bb.putLong(buffer.size());
		bb.putLong(data.length);
		int done = buffer.size();
		for(int i = 16; i < done+16; i++){
			ret[i] = buffer.remove();
		}
		return ret;
	}
	
	private byte[] unzip(byte[] zip, int data_size){
		Inflater decompressor = new Inflater();
		decompressor.setInput(zip);
		try {
			byte[] data = new byte[data_size];
			decompressor.inflate(data);
			decompressor.end();
			return data;
		} catch (DataFormatException e) {
			e.printStackTrace();
			return null;
		}
	}
}
