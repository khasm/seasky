package mie.utils;

import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.mail.MessagingException;

import mie.SearchResult;
import mie.crypto.TimeSpec;
import mie.MIE;
import mie.MIEClient;
import mie.Cache;

public class ClientThread extends Thread {

	private static final String UNSTRUCTURED_IMG_DIR = "/imgs";
	private static final String UNSTRUCTURED_TXT_DIR = "/tags";
	private static final String MIME_DIR = "/mime";
	private static final String UNSTRUCTURED_IMG_NAME_FORMAT = UNSTRUCTURED_IMG_DIR+"/im%s.jpg";
	private static final String UNSTRUCTURED_TXT_NAME_FORMAT = UNSTRUCTURED_TXT_DIR+"/tags%s.txt";
	private static final String MIME_DOC_NAME_FORMAT = MIME_DIR+"/%s.mime";

	private MIE mie;
	private Monitor monitor;
	private SearchStats searchStats;
	private File datasetDir;
	private Queue<Command> commands;
	private int threadId;
	private int nOperations;
	private int prefix;
	private int nextSingleOp;
	private long bytesUpload;
	private long bytesSearch;
	private long bytesDownload;

	public ClientThread(Queue<Command> commands, int threadId, Monitor monitor, MIE mie,
		File datasetDir, int prefix) {
		this.commands = commands;
		this.threadId = threadId;
		this.prefix = prefix;
		this.mie = mie;
		this.monitor = monitor;
		this.datasetDir = datasetDir;
		searchStats = new SearchStats();
		bytesUpload = 0;
		bytesSearch = 0;
		bytesDownload = 0;
		nOperations = 0;
		nextSingleOp = 1;
	}

	public void run() {
		monitor.ready();
		//System.out.println("Thread "+threadId+" started");
		for(Command command: commands){
			//System.out.println("Thread "+threadId+" exec: "+command.toString());
			String op = command.getOp().toLowerCase();
			try{
				if(op.startsWith(TestSet.ADD) || op.startsWith(TestSet.SEARCH)||
					op.startsWith(TestSet.GET)){
					int first = Integer.parseInt(command.getArgs()[0]);
					int last = 2 <= command.getArgs().length ?
						Integer.parseInt(command.getArgs()[1]) : first;
					if(first != last){
						int nThreads = monitor.getNThreads();
						int maxDocs = 0;
						if(op.equals(TestSet.ADD) || op.equals(TestSet.GET) ||
							op.equals(TestSet.SEARCH)){
							String[] imgs = new File(datasetDir, "imgs").list();
							String[] txts = new File(datasetDir, "tags").list();
							int maxUnstructuredDocs = imgs.length > txts.length ?
								txts.length : imgs.length;
							maxDocs = maxUnstructuredDocs;
						}
						else if(op.equals(TestSet.ADD_MIME) || op.equals(TestSet.GET_MIME) ||
							op.equals(TestSet.SEARCH_MIME)){
							int maxMimeDocs = new File(datasetDir, "mime").list().length;
							maxDocs = maxMimeDocs;
						}
						int nDocs = last - first + 1;
						if(maxDocs < nDocs)
							nDocs = maxDocs;
						if(1 < nThreads){
							int reqDocs = nDocs % nThreads == 0 ? nDocs / nThreads : nDocs / nThreads +1;
							first = threadId * reqDocs;
							int max = first + reqDocs - 1;
							if(max > last)
								max = last;
							last = max;
							/*System.out.println("Thread "+threadId+" exec change: "+command.getOp()+" "+
								first+" "+last);*/
						}
					}
					if(op.equalsIgnoreCase(TestSet.ADD)){
						addUnstructered(first, last);
					}
					else if(op.equalsIgnoreCase(TestSet.ADD_MIME)){
						addMime(first, last);
					}
					else if(op.equalsIgnoreCase(TestSet.SEARCH)){
						searchUnstructured(first, last);
					}
					else if(op.equalsIgnoreCase(TestSet.SEARCH_MIME)){
						searchMime(first, last);
					}
					else{
						int cacheMode = TestSet.CACHE_DISABLED;
						if(first != last && 2 < command.getArgs().length){
							if(command.getArgs()[2].equalsIgnoreCase(TestSet.CACHE_80_USER)){
								cacheMode = TestSet.CACHE_80;
							}
							else if(command.getArgs()[2].equalsIgnoreCase(TestSet.CACHE_CLIENT_100_USER)){
								cacheMode = TestSet.CACHE_CLIENT_100;
							}
							else if(command.getArgs()[2].equalsIgnoreCase(TestSet.CACHE_SERVER_100_USER)){
								cacheMode = TestSet.CACHE_SERVER_100;
							}
							else if(command.getArgs()[2].equalsIgnoreCase(TestSet.CACHE_DOUBLE_USER)){
								cacheMode = TestSet.CACHE_DOUBLE;
							}
						}
						int[] ids = setupGet(cacheMode, first, last);
						//System.out.println("Thread "+threadId+": "+ids[0]+" "+ids[ids.length-1]);
						if(op.equalsIgnoreCase(TestSet.GET)){
							getUnstructured(ids, first);
						}
						else if(op.equalsIgnoreCase(TestSet.GET_MIME)){
							getMime(ids, first);
						}
					}
				}
				else if(op.equalsIgnoreCase(TestSet.INDEX)){
					nOperations++;
					boolean wait = command.getArgs().length > 0 &&
						(command.getArgs()[0].equalsIgnoreCase("w") ||
						command.getArgs()[0].equalsIgnoreCase("wait")) ? true : false;
					mie.index(true, wait);
				}
				else if(op.equalsIgnoreCase(TestSet.SYNC)){
					monitor.waitForAll();
				}
				else if(op.equalsIgnoreCase(TestSet.RESET)){
					if(monitor.canExecute(nextSingleOp++)){
						mie.resetServerCache();
					}
				}
				else if(op.equalsIgnoreCase(TestSet.CLEAR)){
					if(monitor.canExecute(nextSingleOp++)){
						mie.clearTimes();
					}
				}
				else if(op.equalsIgnoreCase(TestSet.WAIT)){
					try{
						int seconds = Integer.parseInt(command.getArgs()[0]);
						monitor.waitFor(seconds);
					}
					catch(NumberFormatException e){
						e.printStackTrace();
					}
				}
				else if(op.equalsIgnoreCase(TestSetGenerator.COMPARE)){
					compareCBIR();
				}
			}
			catch(IOException | MessagingException | NoSuchAlgorithmException | InvalidKeyException
				| IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException e){
				e.printStackTrace();
			}
		}
	}

	public SearchStats getSearchStats() {
		return searchStats;
	}

	public long getBytesUpload() {
		return bytesUpload;
	}

	public long getBytesSearch() {
		return bytesSearch;
	}

	public long getBytesDownload() {
		return bytesDownload;
	}

	public int getNOperations() {
		return nOperations;
	}

	private void addUnstructered(int first, int last) throws IOException{
		nOperations += last - first +1;
		for(int i = first; i <= last; i++){
			byte[] img = readImg(i);
			byte[] txt = readTxt(i);
			bytesUpload += img.length + txt.length;
			mie.addUnstructredDoc(""+(prefix+i), img, txt);
		}
	}


	private void searchUnstructured(int first, int last) throws IOException{
		nOperations += last - first +1;
		for(int i = first; i <= last; i++){
			byte[] img = readImg(i);
			byte[] txt = readTxt(i);
			bytesSearch += img.length + txt.length;
			processResults(mie.searchUnstructuredDocument(img, txt, 0), i);
		}
	}

	private void getUnstructured(int[] ids, int first) throws IOException{
		nOperations += ids.length;
		for(int i = 0; i < ids.length; i++){
			byte[][] file = mie.getUnstructuredDoc(ids[i]+"", true);
			bytesDownload += file[0].length + file[1].length;
			writeFile((i+first)+".jpg", file[0]);
			writeFile((i+first)+".txt", file[1]);
		}
	}

	private void addMime(int first, int last) throws IOException, MessagingException{
		nOperations += last - first +1;
		for(int i = first; i <= last; i++){
			byte[] mime = readMime(i);
			bytesUpload += mime.length;
			mie.addMime(""+(prefix+i), mime);
		}
	}

	private void searchMime(int first, int last) throws IOException, MessagingException{
		nOperations += last - first +1;
		for(int i = first; i <= last; i++){
			byte[] mime = readMime(i);
			bytesSearch += mime.length;
			processResults(mie.searchMime(mime, 0), i);
		}
	}

	private void getMime(int[] ids, int first) throws IOException{
		nOperations += ids.length;
		for(int i = 0; i < ids.length; i++){
			byte[] file = mie.getMime(ids[i]+"", true);
			bytesDownload += file.length;
			writeFile((i+first)+".mime", file);
		}
	}

	private byte[] readImg(int id) throws IOException{
		FileInputStream in = new FileInputStream(new File(datasetDir,
			String.format(UNSTRUCTURED_IMG_NAME_FORMAT, id)));
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private byte[] readTxt(int id) throws IOException{
		FileInputStream in = new FileInputStream(new File(datasetDir,
			String.format(UNSTRUCTURED_TXT_NAME_FORMAT, id)));
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}

	private byte[] readMime(int id) throws IOException{
		FileInputStream in = new FileInputStream(new File(datasetDir,
			String.format(MIME_DOC_NAME_FORMAT, id)));
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}

	private void writeFile(String name, byte[] buffer) throws IOException{
		FileOutputStream out = new FileOutputStream(name);
		out.write(buffer);
		out.close();
	}

	private void processResults(List<SearchResult> res, int i) {
		if(!res.isEmpty()){
			SearchResult r = res.get(0);
			if(r.getId().equals(""+i)){
				searchStats.hit();
			}
			else{
				searchStats.miss();
			}
			searchStats.addScore(r.getScore());
			searchStats.setMinMax(i, r.getId(), r.getScore());
			//printSearchResults(res, i);
		}
		else{
			searchStats.miss();
		}
	}

	private int[] setupGet(int cacheMode, int first, int last) throws IOException {
		Set<Integer> added = new HashSet<Integer>();
		Cache c = ((MIEClient)mie).cache;
		int[] ids;
		switch(cacheMode){
		case(TestSet.CACHE_80):
			ids = TestSet.getIds(first, last, last-first+1);
			break;
		case(TestSet.CACHE_CLIENT_100):
			ids = TestSet.getIds(first, last, last-first+1);
			for(int i: ids){
				if(!added.contains(i)){
					byte[] img = readImg(i);
					byte[] txt = readTxt(i);
					ByteBuffer buffer = ByteBuffer.allocate(4+img.length +
						txt.length);
					buffer.putInt(img.length);
					buffer.put(img);
					buffer.put(txt);
					c.addToCache(i+"", buffer.array());
					added.add(i);
				}
			}
			break;
		case(TestSet.CACHE_SERVER_100):
			ids = TestSet.getIds(first, last, last-first+1);
			for(int i: ids){
				if(!added.contains(i)){
					added.add(i);
				}
			}
			TestSet.setupServerCache(added, true, mie.getServerIp());
			break;
		case(TestSet.CACHE_DOUBLE):
			//need to do 3 steps for 80% hit rate on both caches
			//first: get a list of the unique documents going to be
			//retrieved
			//second: retrieve ~80% of those unique documents to
			//populate the server cache
			//third: clear the client cache
			ids = TestSet.getIds(first, last, last-first+1);
			for(int i: ids){
				if(!added.contains(i)){
					added.add(i);
				}
			}
			TestSet.setupServerCache(added, false, mie.getServerIp());
			break;
		default:
			ids = new int[last-first+1];
			for(int i = first; i <=last; i++){
				ids[i-first] = i;
			}
		}
		return ids;
	}

	private void printSearchResults(List<SearchResult> res, int id){
		System.out.println("Searching for: "+id);
		for(SearchResult result: res){
			System.out.printf("id: %s score: %.6f\n", result.getId(), result.getScore());
		}
	}

	private void compareCBIR() throws NoSuchAlgorithmException, IOException, InvalidKeyException,
		IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException {
		KeyGenerator cbirKeyGen = KeyGenerator.getInstance("CBIRD");
		SecretKey cbirKey = cbirKeyGen.generateKey();
		Mac cbir = Mac.getInstance("CBIRD");
		KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
		SecretKey aesKey = aesKeyGen.generateKey();
		Cipher aes = Cipher.getInstance("AES/CTR/PKCS7Padding");
		long cbird = 0;
		long aesi = 0;
		long cbirdSize = 0;
		long aesiSize = 0;
		long plaini = 0;
		long plaint = 0;
		int max = new File(datasetDir, UNSTRUCTURED_IMG_DIR).list().length;
		for(int i = 0; i < max; i++){
			byte[] img = readImg(i);
			plaini += img.length;
			long start = System.nanoTime();
			cbir.init(cbirKey);
			cbir.update(img);
			byte[] tmp = cbir.doFinal();
			cbird += System.nanoTime() - start;
			cbirdSize += tmp.length;
			start = System.nanoTime();
			aes.init(Cipher.ENCRYPT_MODE, aesKey);
			tmp = aes.doFinal(img);
			aesi += System.nanoTime() - start;
			aesiSize += tmp.length;
		}
		long cryptoCbirD = TimeSpec.getEncryptionTime();
		long featureCbirD = TimeSpec.getFeatureTime();
		long indexCbirD = TimeSpec.getIndexTime();
		TimeSpec.reset();
		cbirKeyGen = KeyGenerator.getInstance("CBIRDWithSymmetricCipher");
		cbirKey = cbirKeyGen.generateKey();
		Cipher cbirS = Cipher.getInstance("CBIRDWithSymmetricCipher");
		long cbirdS = 0;
		for(int i = 0; i < max; i++){
			byte[] img = readImg(i);
			long start = System.nanoTime();
			cbirS.init(Cipher.ENCRYPT_MODE, cbirKey);
			byte[] tmp = cbirS.doFinal(img);
			cbirdS += System.nanoTime() - start;
		}
		TimeSpec.reset();
		cbirKeyGen = KeyGenerator.getInstance("CBIRS");
		cbirKey = cbirKeyGen.generateKey();
		cbir = Mac.getInstance("CBIRS");
		long cbirs = 0;
		long aest = 0;
		long cbirsSize = 0;
		long aestSize = 0;
		max = new File(datasetDir, UNSTRUCTURED_TXT_DIR).list().length;
		for(int i = 0; i < max; i++){
			byte[] txt = readTxt(i);
			plaint += txt.length;
			long start = System.nanoTime();
			cbir.init(cbirKey);
			cbir.update(txt);
			byte[] tmp = cbir.doFinal();
			cbirs += System.nanoTime() - start;
			cbirsSize += tmp.length;
			start = System.nanoTime();
			aes.init(Cipher.ENCRYPT_MODE, aesKey);
			tmp = aes.doFinal(txt);
			aest += System.nanoTime() - start;
			aestSize += tmp.length;
		}
		long cryptoCbirS = TimeSpec.getEncryptionTime();
		long featureCbirS = TimeSpec.getFeatureTime();
		long indexCbirS = TimeSpec.getIndexTime();
		System.out.printf("Plain size images: %d Plain size text: %d\n"+
			"CBIR Dense: %.6f %d bytes AES: %.6f %d bytes\n"+
			"crypto: %.6f features: %.6f index: %.6f\n"+
			"CBIR Sparse: %.6f %d bytes AES: %.6f %d bytes\n"+
			"crypto: %.6f features: %.6f index: %.6f\n"+
			"CBIR Dense cipher: %.6f\n",
			plaini, plaint,
			cbird/1000000000f, cbirdSize, aesi/1000000000f, aesiSize, 
			cryptoCbirD/1000000000f, featureCbirD/1000000000f, indexCbirD/1000000000f,
			cbirs/1000000000f, cbirsSize, aest/1000000000f, aestSize,
			cryptoCbirS/1000000000f, featureCbirS/1000000000f, indexCbirS/1000000000f,
			cbirdS/1000000000f);
	}
}