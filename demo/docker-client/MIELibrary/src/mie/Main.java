package mie;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Comparator;
import java.nio.ByteBuffer;
import java.lang.InterruptedException;

import javax.crypto.NoSuchPaddingException;
import javax.mail.MessagingException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Mac;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;

import mie.crypto.TimeSpec;
import mie.utils.Command;
import mie.utils.Pair;
import mie.utils.Monitor;
import mie.utils.SearchStats;
import mie.utils.TestSet;

public class Main {

	private static long networkTime = 0;
	private static long indexTime = 0;
	private static long encryptionTime = 0;
	private static long featureExtractionTime = 0;
	private static long totalTime = 0;
	private static String datasetPath = ".";
	private static String logDirPath = "/logs";
	private static List<List<Pair<Long,String>>> threadLogs;
	private static SearchStats searchStats;
	
	public static void main(String[] args) throws IOException, MessagingException,
		NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
		BadPaddingException {
		if(args.length == 0){
			printHelp();
		}
		else{
			int nextArg = 0;
			int n_threads = 1;
			boolean useCache = false;
			List<Command> queue = new ArrayList<Command>(args.length);
			String ip = "localhost";
			Monitor monitor = new Monitor(1);
			threadLogs = new LinkedList<List<Pair<Long,String>>>();
			searchStats = new SearchStats();
			while(nextArg < args.length){
				if(args[nextArg].equalsIgnoreCase("compare")){
					queue.add(new Command(args[nextArg], new String[0]));
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase("script")){
					try{
						queue.add(new Command(args[nextArg], new String[]{args[nextArg+1]}));
						nextArg += 2;
					}
					catch(ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].startsWith("add") || args[nextArg].startsWith("search") ||
					args[nextArg].startsWith("get")){
					try{
						int first = Integer.parseInt(args[nextArg+1]);
						int last = first;
						try{
							last = Integer.parseInt(args[nextArg+2]);
						} catch (NumberFormatException | ArrayIndexOutOfBoundsException e){}
						int cache_mode = 0;
						if(args[nextArg].equalsIgnoreCase("add") ||
							args[nextArg].equalsIgnoreCase("addDMime") ||
							args[nextArg].equalsIgnoreCase("addMime") ||
							args[nextArg].equalsIgnoreCase("search") ||
							args[nextArg].equalsIgnoreCase("searchDMime") ||
							args[nextArg].equalsIgnoreCase("searchMime")){
							Command com = new Command(args[nextArg], new String[]{""+first, ""+last});
							queue.add(com);
						}
						else if(args[nextArg].equalsIgnoreCase("get") ||
							args[nextArg].equalsIgnoreCase("getMime")){
							if(first != last){
								try{
									if(args[nextArg+3].equalsIgnoreCase("cache80")){
										cache_mode = 1;
									}
									else if(args[nextArg+3].equalsIgnoreCase("cache_client100")){
										cache_mode = 2;
									}
									else if(args[nextArg+3].equalsIgnoreCase("cache_server100")){
										cache_mode = 3;
									}
									else if(args[nextArg+3].equalsIgnoreCase("double_cache")){
										cache_mode = 4;
									}
								}
								catch (ArrayIndexOutOfBoundsException e){}
							}
							Command com = 0 != cache_mode ?
								new Command(args[nextArg], new String[]{""+first, ""+last, args[nextArg+3]}):
								new Command(args[nextArg], new String[]{""+first, ""+last});
							queue.add(com);
						}
						else{
							printHelp();
						}
						if(first == last)
							nextArg+=2;
						else
							nextArg+=3;
						if(0 != cache_mode)
							nextArg++;
					}
					catch (NumberFormatException | ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("index")){
					boolean wait = false;
					try{
						if(args[nextArg+1].equalsIgnoreCase("w") || 
						   args[nextArg+1].equalsIgnoreCase("wait")){
							wait = true;
							nextArg++;
						}
					}
					catch(ArrayIndexOutOfBoundsException e){}
					Command com = new Command("index", wait ? new String[]{"w"} : new String[0]);
					queue.add(com);
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase("reset") ||
					args[nextArg].equalsIgnoreCase("print") ||
					args[nextArg].equalsIgnoreCase("clear")){
					Command com = new Command(args[nextArg], new String[0]);
					queue.add(com);
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase("ip") ||
					args[nextArg].equalsIgnoreCase("path") ||
					args[nextArg].equalsIgnoreCase("logdir")){
					try{
						queue.add(new Command(args[nextArg], new String[]{args[nextArg+1]}));
						nextArg += 2;
					}
					catch(ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("threads")){
					try{
						int tmp = Integer.parseInt(args[nextArg+1]);
						queue.add(new Command(args[nextArg], new String[]{""+tmp}));
						nextArg += 2;
					}
					catch(ArrayIndexOutOfBoundsException | NumberFormatException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("cache") ||
					args[nextArg].equalsIgnoreCase("nocache")){
					//useCache = true;
					queue.add(new Command(args[nextArg], new String[0]));
					nextArg++;
				}
			}
			MIE mie = null;
			for(Command command: queue){
				if(command.getOp().equalsIgnoreCase("ip")){
					ip = command.getArgs()[0];
				}
				else if(command.getOp().equalsIgnoreCase("path")){
					datasetPath = command.getArgs()[0];
				}
				else if(command.getOp().equalsIgnoreCase("logdir")){
					logDirPath = command.getArgs()[0];
				}
				else if(command.getOp().equalsIgnoreCase("cache")){
					useCache = true;
				}
				else if(command.getOp().equalsIgnoreCase("nocache")){
					useCache = false;
				}
				else if(command.getOp().equalsIgnoreCase("compare")){
					compareCBIR();
				}
				else if(command.getOp().equalsIgnoreCase("threads")){
					n_threads = Integer.parseInt(command.getArgs()[0]);
				}
				else if(command.getOp().equalsIgnoreCase("script")){
					File script = new File(command.getArgs()[0]);
					if(script.exists()){
						TestSet test = new TestSet(script);
						test.start();
						System.out.printf(test.toString());
					}
				}
				else if(command.getOp().equalsIgnoreCase("print")){
					if(null == mie){
						mie = new MIEClient(ip, useCache);
					}
					Map<String,String> stats = mie.printServerStatistics();
					for(String key: stats.keySet()){
						System.out.println(key+": "+stats.get(key));
					}
					networkTime += mie.getNetworkTime();
					printStats();
				}
				else if(command.getOp().equalsIgnoreCase("reset") ||
					command.getOp().equalsIgnoreCase("index") ||
					command.getOp().equalsIgnoreCase("clear") ||
					command.getOp().startsWith("add") ||
					command.getOp().startsWith("get") ||
					command.getOp().startsWith("search")){
					if(null == mie){
						mie = new MIEClient(ip, useCache);
					}
					runThreads(n_threads, command, ip, useCache, monitor);
				}
			}
			if(searchStats.hasData()){
				System.out.println(searchStats.toString());
			}
			SortedSet<Pair<Long,String>> fullLog = new TreeSet<Pair<Long,String>>(
				new Comparator<Pair<Long,String>>(){
					public int compare(Pair<Long,String> p1, Pair<Long,String> p2){
						//deliberatly doesn't return 0 if equals so all entries are added
						return p1.getKey() <= p2.getKey() ? -1 : 1;
					}
				});
			for(List<Pair<Long,String>> log: threadLogs)
				for(Pair<Long,String> entry: log)
					fullLog.add(entry);
			try{
				PrintWriter logger = new PrintWriter(new File(logDirPath, "log"));
				for(Pair<Long,String> entry: fullLog){
					logger.println(entry.getKey()+": "+entry.getValue());
				}
				logger.close();
			}
			catch(FileNotFoundException e){
				System.out.println("Couldn't create log");
			}
		}
	}

	private static void runThreads(int n_threads, Command com, String ip, boolean useCache, 
		Monitor monitor){
		String[] imgs = new File(datasetPath, "imgs").list();
		String[] txts = new File(datasetPath, "tags").list();
		int maxUnstructuredDocs = imgs.length > txts.length ? txts.length : imgs.length;
		int maxMimeDocs = new File(datasetPath, "mime").list().length;
		int maxDMimeDocs = new File(datasetPath, "dmime").list().length;
		ClientThread[] clients = new ClientThread[n_threads];
		int maxDocs = -1;
		if(com.getOp().startsWith("add") || com.getOp().startsWith("get") ||
			com.getOp().startsWith("search") && com.getArgs().length>1){
			if(com.getOp().equalsIgnoreCase("add") || com.getOp().equalsIgnoreCase("get") ||
				com.getOp().equalsIgnoreCase("search")){
				maxDocs = maxUnstructuredDocs;
			}
			else if(com.getOp().equalsIgnoreCase("addmime") ||
				com.getOp().equalsIgnoreCase("getmime") ||
				com.getOp().equalsIgnoreCase("searchmime")){
				maxDocs = maxMimeDocs;
			}
			else if(com.getOp().equalsIgnoreCase("adddmime") ||
				com.getOp().equalsIgnoreCase("getdmime") ||
				com.getOp().equalsIgnoreCase("searchdmime")){
				maxDocs = maxDMimeDocs;
			}		
		}
		for(int i = 0; i < n_threads; i++){
			Command c = com;
			if(-1 != maxDocs){
				int min = Integer.parseInt(com.getArgs()[0]);
				int max = Integer.parseInt(com.getArgs()[1]);
				int nDocs = max-min + 1;
				if(maxDocs < nDocs)
					nDocs = maxDocs;
				int reqDocs = nDocs % n_threads == 0 ? nDocs / n_threads : nDocs / n_threads +1;
				int first = i * reqDocs;
				int last = first + reqDocs - 1;
				if(last > max)
					last = max;
				if(com.getArgs().length == 2)
					c = new Command(com.getOp(), new String[]{""+first, ""+last});
				else
					c = new Command(com.getOp(), new String[]{""+first, ""+last, com.getArgs()[2]});
			}
			System.out.printf("Starting thread %d with command %s", i, c.getOp());
			if(c.getArgs().length > 0){
				System.out.printf(" with args:");
				for(String s: c.getArgs())
					System.out.printf(" %s", s);
			}
			System.out.println();
			clients[i] = new ClientThread(i, c, ip, useCache, monitor);
			clients[i].start();
		}
		monitor.start();
		long start = System.nanoTime();
		try{
			for(int i = 0; i < n_threads; i++){
				System.out.printf("Waiting for thread %d\n", i);
				clients[i].join();
			}
			totalTime += System.nanoTime()-start;
			for(int i = 0; i < n_threads; i++){
				threadLogs.add(clients[i].getLog());
				searchStats.merge(clients[i].getSearchStats());
			}
		}
		catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	private static void compareCBIR() throws NoSuchAlgorithmException, IOException, InvalidKeyException,
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
		for(int i = 0; i < 1000; i++){
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
		for(int i = 0; i < 1000; i++){
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
		for(int i = 0; i < 1000; i++){
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
		System.exit(0);
	}
	
	private static void printStats(){
		indexTime = TimeSpec.getIndexTime();
		featureExtractionTime = TimeSpec.getFeatureTime();
		encryptionTime = TimeSpec.getEncryptionTime();
		long encryptionSymmetricTime = TimeSpec.getEncryptionSymmetricTime();
		long encryptionCbirTime = TimeSpec.getEncryptionCbirTime();
		long encryptionMiscTime = TimeSpec.getEncryptionMiscTime();
		System.out.printf("CBIR encryption: %f\nSymmetric encryption: %f\nMisc: %f\n",
			encryptionCbirTime/1000000000f, encryptionSymmetricTime/1000000000f,
			encryptionMiscTime/1000000000f);
		System.out.printf("featureTime: %f indexTime: %f cryptoTime: %f cloudTime: %f total_time: %.6f\n",
				featureExtractionTime/1000000000f, indexTime/1000000000f, encryptionTime/1000000000f, 
				networkTime/1000000000f, totalTime/1000000000f);
	}
	
	protected static void printSearchResults(List<SearchResult> res, int id){
		System.out.println("Searching for: "+id);
		for(SearchResult result: res){
			System.out.printf("id: %s score: %.6f\n", result.getId(), result.getScore());
		}
	}
	
	protected static byte[] readImg(int id) throws IOException{
		FileInputStream in = new FileInputStream(datasetPath + "/imgs/im"+id+".jpg");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	protected static byte[] readTxt(int id) throws IOException{
		FileInputStream in = new FileInputStream(datasetPath + "/tags/tags"+id+".txt");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}

	protected static byte[] readMime(int id) throws IOException{
		FileInputStream in = new FileInputStream(datasetPath + "/mime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	protected static byte[] readDMime(int id) throws IOException{
		FileInputStream in = new FileInputStream(datasetPath + "/dmime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	protected static void writeFile(String name, byte[] buffer) throws IOException{
		FileOutputStream out = new FileOutputStream(name);
		out.write(buffer);
		out.close();
	}
	
	private static void printHelp(){
		System.out.println("Must supply an operation:");
		System.out.println("add[[d]mime] <first> [last]");
		System.out.println("index");
		System.out.println("search[[d]mime] <first> [last]");
		System.out.println("get[mime] <first> [last]");
		System.out.println("print");
		System.exit(1);
	}

	protected static int[] getIds(int min, int max){
		int[] ids = new int[max-min+1];
		boolean[] used = new boolean[max-min+1];
		List<Integer> not_used = new LinkedList<Integer>();
		Random rng = new Random(53234);
		for(int i = min; i < max; i++)
			not_used.add(i-min);
		for(int i = 0; i < ids.length; i++){
			double req = rng.nextDouble();
			if(req>0.8 || i < 1){
				int index = (int)(rng.nextDouble()*not_used.size());
				ids[i] = not_used.remove(index);
				used[ids[i]-min] = true;
			}
			else{
				int index = (int)(rng.nextDouble()*i);
				ids[i] = ids[index];
			}
			/*if(i < 200){
				ids[i] = not_used.remove((int)(Math.random()*not_used.size()));
			}
			else{
				ids[i] = ids[(int)(Math.random()*200)];
			}*/
		}
		double repeats = 0;
		Map<Integer,Integer> count = new HashMap<Integer,Integer>();
		for(int i = 0; i < ids.length; i++){
			if(count.containsKey(ids[i])){
				count.put(ids[i], count.get(ids[i])+1);
				repeats++;
			}
			else{
				count.put(ids[i], 1);
			}
		}
		for(Integer key: count.keySet()){
			//System.out.println(key+":"+count.get(key));
		}
		//System.out.println("Total different docs: "+count.keySet().size());
		//System.out.println("total: "+ids.length+" repeats: "+repeats+" repeat ratio; "+repeats/ids.length);
		return ids;
	}

}

class ClientThread extends Thread{

	private Command com;
	private int threadId;
	private MIE mie;
	private Monitor monitor;
	private List<Pair<Long,String>> log;
	private SearchStats searchStats;

	ClientThread(int id, Command com, String ip, boolean useCache, Monitor monitor){
		try{
			this.mie = new MIEClient(ip, useCache);
		}
		catch(NoSuchAlgorithmException | NoSuchPaddingException | IOException e){
			this.mie = null;
		}
		this.com = com;
		threadId = id;
		this.monitor = monitor;
		log = new LinkedList<Pair<Long,String>>();
		searchStats = new SearchStats();
	}

	protected List<Pair<Long,String>> getLog() {
		return log;
	}

	protected SearchStats getSearchStats() {
		return searchStats;
	}

	public void run(){
		monitor.ready();
		System.out.printf("Thread %d started\n", threadId);
		try{
			if(com.getOp().startsWith("add") || com.getOp().startsWith("search")){
				int first = Integer.parseInt(com.getArgs()[0]);
				int last = com.getArgs().length == 2 ? Integer.parseInt(com.getArgs()[1]) : first;
				if(com.getOp().equalsIgnoreCase("add")){
					addUnstructered(first, last);
				}
				else if(com.getOp().equalsIgnoreCase("addDMime")){
					addDMime(first, last);
				}
				else if(com.getOp().equalsIgnoreCase("addMime")){
					addMime(first, last);
				}
				else if(com.getOp().equalsIgnoreCase("search")){
					searchUnstructured(first, last);
				}
				else if(com.getOp().startsWith("searchd")){
					searchDMime(first, last);
				}
				else if(com.getOp().equalsIgnoreCase("searchMime")){
					searchMime(first, last);
				}
			}
			else if(com.getOp().startsWith("get")){
				int first = Integer.parseInt(com.getArgs()[0]);
				int last = com.getArgs().length >= 2 ? Integer.parseInt(com.getArgs()[1]) : first;
				int[] ids;
				Set<Integer> added = new HashSet<Integer>();
				Cache c = ((MIEClient)mie).cache;
				int cache_mode = 0;
				if(first != last){
					try{
						if(com.getArgs()[2].equalsIgnoreCase("cache80")){
							cache_mode = 1;
						}
						else if(com.getArgs()[2].equalsIgnoreCase("cache_client100")){
							cache_mode = 2;
						}
						else if(com.getArgs()[2].equalsIgnoreCase("cache_server100")){
							cache_mode = 3;
						}
						else if(com.getArgs()[2].equalsIgnoreCase("double_cache")){
							cache_mode = 4;
						}
					}
					catch (ArrayIndexOutOfBoundsException e){}
					switch(cache_mode){
						case(1):
							ids = Main.getIds(first, last);
							break;
						case(2):
							ids = Main.getIds(first, last);
							for(int i: ids){
								if(!added.contains(i)){
									byte[] img = Main.readImg(i);
									byte[] txt = Main.readTxt(i);
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
						case(3):
							ids = Main.getIds(first, last);
							for(int i: ids){
								if(!added.contains(i)){
									mie.getUnstructuredDoc(i+"", true);
									added.add(i);
								}
							}
							mie.clearServerTimes();
							//TODO: this reset makes times inaccurate with concurrency
							TimeSpec.reset();
							c.clear();
							c.resetStats();
							break;
						case(4):
							//need to do 3 steps for 80% hit rate on both caches
							//first: get a list of the unique documents going to be
							//retrieved
							//second: retrieve ~80% of those unique documents to
							//populate the server cache
							//third: clear the client cache
							ids = Main.getIds(first, last);
							for(int i: ids){
								if(!added.contains(i)){
									added.add(i);
								}
							}
							int n = added.size();
							double nc = n/10.0*8.0;
							Integer[] intg = added.toArray(new Integer[0]);
							for(int i = 0; i < nc; i++){
								mie.getUnstructuredDoc(intg[i]+"", true);
							}
							mie.clearServerTimes();
							TimeSpec.reset();
							c.clear();
							c.resetStats();
							break;
						default:
							ids = new int[last-first+1];
							for(int i = first; i <=last; i++){
								ids[i-first] = i;
							}
					}
				}
				else{
					ids = new int[1];
					ids[0] = first;
				}
				if(com.getOp().equalsIgnoreCase("get")){
					getUnstructured(ids);
				}
				else if(com.getOp().equalsIgnoreCase("getMime")){
					getMime(ids);
				}
			}
			else if(com.getOp().equalsIgnoreCase("index")){
				boolean wait = com.getArgs().length > 0 &&
					(com.getArgs()[0].equalsIgnoreCase("w") ||
					com.getArgs()[0].equalsIgnoreCase("wait")) ? true : false;
				mie.index(true, wait);
			}
			else if(com.getOp().equalsIgnoreCase("reset")){
				mie.resetServerCache();
			}
			else if(com.getOp().equalsIgnoreCase("clear")){
				mie.clearServerTimes();
			}
		}
		catch(IOException | MessagingException e){
			e.printStackTrace();
		}
	}

	private void getMime(int[] ids) throws IOException{
		for(int i = 0; i < ids.length; i++){
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" get "+i));
			byte[] file = mie.getMime(ids[i]+"", true);
			Main.writeFile(i+".mime", file);
		}
	}

	private void getUnstructured(int[] ids) throws IOException{
		for(int i = 0; i < ids.length; i++){
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" get "+i));
			byte[][] file = mie.getUnstructuredDoc(ids[i]+"", true);
			Main.writeFile(i+".jpg", file[0]);
			Main.writeFile(i+".txt", file[1]);
		}
	}

	private void addUnstructered(int first, int last) throws IOException{
		for(int i = first; i <= last; i++){
			byte[] img = Main.readImg(i);
			byte[] txt = Main.readTxt(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" add "+i));
			mie.addUnstructredDoc(""+i, img, txt);
		}
	}

	private void addMime(int first, int last) throws IOException,
		MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = Main.readMime(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" add "+i));
			mie.addMime(""+i, mime);
		}
	}

	private void addDMime(int first, int last) throws IOException,
		MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = Main.readDMime(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" add "+i));
			mie.addMime(""+i, mime);
		}
	}

	private void searchUnstructured(int first, int last) throws IOException{
		for(int i = first; i <= last; i++){
			byte[] img = Main.readImg(i);
			byte[] txt = Main.readTxt(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" search "+i));
			processResults(mie.searchUnstructuredDocument(img, txt, 0), i);
		}
	}

	private void searchMime(int first, int last) throws IOException,
		MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = Main.readMime(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" searchMime "+i));
			processResults(mie.searchMime(mime, 0), i);
		}
	}

	private void searchDMime(int first, int last) throws IOException,
		MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = Main.readDMime(i);
			long t = System.currentTimeMillis();
			log.add(new Pair<Long,String>(t, "Thread "+threadId+" searchDMime "+i));
			processResults(mie.searchMime(mime, 0), i);
		}
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
			Main.printSearchResults(res, i);
		}
		else{
			searchStats.miss();
		}
	}
}
