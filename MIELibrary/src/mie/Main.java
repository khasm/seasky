package mie;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;

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

public class Main {

	static long networkTime = 0;
	static long indexTime = 0;
	static long encryptionTime = 0;
	static long featureExtractionTime = 0;
	static long totalTime = 0;
	
	public static void main(String[] args) throws IOException, MessagingException,
		NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
		BadPaddingException {
		if(args.length == 0){
			printHelp();
		}
		else{
			if(args[0].equalsIgnoreCase("compare")){
				compareCBIR();
			}
			MIE mie;
			int nextArg = 0;
			if(args[0].equals("ip")){
				try{
					mie = new MIEClient(args[1]);
					nextArg = 2;
				} 
				catch (ArrayIndexOutOfBoundsException e){
					mie = null;
					printHelp();
				}
			}
			else{
				mie = new MIEClient();
			}
			while(nextArg < args.length){
				if(args[nextArg].startsWith("add")){
					try{
						int first = Integer.parseInt(args[nextArg+1]);
						int last = first;
						try{
							last = Integer.parseInt(args[nextArg+2]);
						} catch (NumberFormatException | ArrayIndexOutOfBoundsException e){}
						long start = System.nanoTime();
						if(args[nextArg].equalsIgnoreCase("add")){
							addUnstructered(mie, first, last);
						}
						else if(args[nextArg].startsWith("addd")){
							addDMime(mie, first, last);
						}
						else if(args[nextArg].equalsIgnoreCase("addMime")){
							addMime(mie, first, last);
						}
						else{
							printHelp();
						}
						totalTime = System.nanoTime()-start;
						if(first == last)
							nextArg+=2;
						else
							nextArg+=3;
					}
					catch (NumberFormatException | ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("index")){
					long start = System.nanoTime();
					mie.index(true);
					totalTime = System.nanoTime()-start;
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase("reset")){
					long start = System.nanoTime();
					mie.resetServerCache();
					totalTime = System.nanoTime()-start;
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase("search")){
					try{
						int first = Integer.parseInt(args[nextArg+1]);
						int last = first;
						try{
							last = Integer.parseInt(args[nextArg+2]);
						} catch (NumberFormatException | ArrayIndexOutOfBoundsException e){}
						long start = System.nanoTime();
						if(args[nextArg].equalsIgnoreCase("search")){
							searchUnstructured(mie, first, last);
						}
						else if(args[nextArg].startsWith("searchd")){
							searchDMime(mie, first, last);
						}
						else if(args[nextArg].equalsIgnoreCase("searchMime")){
							searchMime(mie, first, last);
						}
						else{
							printHelp();
						}
						totalTime = System.nanoTime()-start;
						if(first == last)
							nextArg+=2;
						else
							nextArg+=3;
					}
					catch (NumberFormatException | ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("get")){
					try{
						int first = Integer.parseInt(args[nextArg+1]);
						int last = first;
						try{
							last = Integer.parseInt(args[nextArg+2]);
						} catch (NumberFormatException | ArrayIndexOutOfBoundsException e){}
						int[] ids;
						if(first != last){
							/*ids = getIds(first, last);
							Set<Integer> added = new HashSet<Integer>();
							Cache c = ((MIEClient)mie).cache;
							int size = 0;
							for(int i: ids){
								if(!added.contains(i)){
									/*byte[] img = readImg(i);
									byte[] txt = readTxt(i);
									ByteBuffer buffer = ByteBuffer.allocate(4+img.length+txt.length);
									buffer.putInt(img.length);
									buffer.put(img);
									buffer.put(txt);
									c.addToCache(i+"", buffer.array());
									added.add(i);
									//size += img.length + txt.length;
								}
							}
							//System.out.println("Cached: "+size);
							/*int n = added.size();
							double nc = n/10.0*8.0;
							Integer[] intg = added.toArray(new Integer[0]);
							for(int i = 0; i < nc; i++){
								mie.getUnstructuredDoc(intg[i]+"");
							}
							mie.clearServerTimes();
							networkTime = 0;
							TimeSpec.reset();
							Map<String,String> stats = mie.printServerStatistics();
							for(String key: stats.keySet()){
								System.out.println(key+": "+stats.get(key));
							}
							c.clear();
							c.resetStats();
							System.out.println("Added "+nc+" of "+n+" to server cache");*/
							ids = new int[last-first+1];
							for(int i = first; i <=last; i++){
								ids[i-first] = i;
							}
						}
						else{
							ids = new int[1];
							ids[0] = first;
						}
						long start = System.nanoTime();
						if(args[nextArg].equalsIgnoreCase("get")){
							getUnstructured(mie, ids);
						}
						else if(args[nextArg].equalsIgnoreCase("getMime")){
							getMime(mie, first, last);
						}
						else{
							printHelp();
						}
						totalTime = System.nanoTime()-start;
						if(first == last)
							nextArg+=2;
						else
							nextArg+=3;
					}
					catch (NumberFormatException | ArrayIndexOutOfBoundsException e){
						printHelp();
					}
				}
				else if(args[nextArg].equalsIgnoreCase("print")){
					Map<String,String> stats = mie.printServerStatistics();
					for(String key: stats.keySet()){
						System.out.println(key+": "+stats.get(key));
					}
					nextArg++;
					printStats();
				}
				else if(args[nextArg].equalsIgnoreCase("clear")){
					mie.clearServerTimes();
					nextArg++;
				}
			}
			//printStats();
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
	
	
	private static void addUnstructered(MIE mie, int first, int last) throws IOException{
		for(int i = first; i <= last; i++){
			byte[] img = readImg(i);
			byte[] txt = readTxt(i);
			mie.addUnstructredDoc(""+i, img, txt);
		}
	}
	
	private static void addMime(MIE mie, int first, int last) throws IOException, MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = readMime(i);
			mie.addMime(""+i, mime);
		}
	}
	
	private static void addDMime(MIE mie, int first, int last) throws IOException, MessagingException{
		for(int i = first; i <= last; i++){
			byte[] mime = readDMime(i);
			mie.addMime(""+i, mime);
		}
	}
	
	private static void searchUnstructured(MIE mie, int first, int last) throws IOException{
		int hit = 0, miss = 0, total = 0, maxi = -1, mini = -1;
		String maxid = "", minid = "";
		double max = Double.MIN_VALUE, min = Double.MAX_VALUE, average = 0;
		for(int i = first; i <= last; i++){
			byte[] img = readImg(i);
			byte[] txt = readTxt(i);
			List<SearchResult> res = mie.searchUnstructuredDocument(img, txt, 0);
			System.out.println("#results: "+res.size());
			total++;
			if(!res.isEmpty()){
				SearchResult r = res.get(0);
				if(r.getId().equals(""+i)){
					hit++;
				}
				else{
					miss++;
				}
				average += r.getScore();
				if(r.getScore() > max){
					maxi = i;
					maxid = r.getId();
					max = r.getScore();
				}
				if(r.getScore() < min){
					mini = i;
					minid = r.getId();
					min = r.getScore();
				}
				//printSearchResults(res, i);
			}
			else{
				miss++;
			}
		}
		average /= total;
		System.out.println("Hits: "+hit+" Misses: "+miss+" Hit Ratio: "+((double)hit/(double)total)*100);
		System.out.printf("Average: %.6f\n",average);
		System.out.printf("Max: %d %s %.6f\n", maxi, maxid, max);
		System.out.printf("Min: %d %s %.6f\n", mini, minid, min);
	}
	
	private static void searchMime(MIE mie, int first, int last) throws IOException, MessagingException{
		int hit = 0, miss = 0, total = 0, maxi = -1, mini = -1;
		String maxid = "", minid = "";
		double max = Double.MIN_VALUE, min = Double.MAX_VALUE, average = 0;
		for(int i = first; i <= last; i++){
			byte[] mime = readMime(i);
			List<SearchResult> res = mie.searchMime(mime, 0); 
			total++;
			if(!res.isEmpty()){
				SearchResult r = res.get(0);
				if(r.getId().equals(""+i)){
					hit++;
				}
				else{
					miss++;
				}
				average += r.getScore();
				if(r.getScore() > max){
					maxi = i;
					maxid = r.getId();
					max = r.getScore();
				}
				if(r.getScore() < min){
					mini = i;
					minid = r.getId();
					min = r.getScore();
				}
				printSearchResults(res, i);
			}
			else{
				miss++;
			}
		}
		average /= total;
		System.out.println("Hits: "+hit+" Misses: "+miss+" Hit Ration: "+((double)hit/(double)total)*100);
		System.out.printf("Average: %.6f\n",average);
		System.out.printf("Max: %d %s %.6f\n", maxi, maxid, max);
		System.out.printf("Min: %d %s %.6f\n", mini, minid, min);
	}
	
	private static void searchDMime(MIE mie, int first, int last) throws IOException, MessagingException{
		int hit = 0, miss = 0, total = 0, maxi = -1, mini = -1;
		String maxid = "", minid = "";
		double max = Double.MIN_VALUE, min = Double.MAX_VALUE, average = 0;
		for(int i = first; i <= last; i++){
			byte[] mime = readDMime(i);
			List<SearchResult> res = mie.searchMime(mime, 0); 
			total++;
			if(!res.isEmpty()){
				SearchResult r = res.get(0);
				if(r.getId().equals(""+i)){
					hit++;
				}
				else{
					miss++;
				}
				average += r.getScore();
				if(r.getScore() > max){
					maxi = i;
					maxid = r.getId();
					max = r.getScore();
				}
				if(r.getScore() < min){
					mini = i;
					minid = r.getId();
					min = r.getScore();
				}
				printSearchResults(res, i);
			}
			else{
				miss++;
			}
		}
		average /= total;
		System.out.println("Hits: "+hit+" Misses: "+miss+" Hit Ration: "+((double)hit/(double)total)*100);
		System.out.printf("Average: %.6f\n",average);
		System.out.printf("Max: %d %s %.6f\n", maxi, maxid, max);
		System.out.printf("Min: %d %s %.6f\n", mini, minid, min);
	}
	
	private static void printSearchResults(List<SearchResult> res, int id){
		System.out.println("Searching for: "+id);
		for(SearchResult result: res){
			System.out.printf("id: %s score: %.6f\n", result.getId(), result.getScore());
		}
	}
	
	private static void getUnstructured(MIE mie, int[] ids) throws IOException{
		for(int i = 0; i < ids.length; i++){
			byte[][] file = mie.getUnstructuredDoc(ids[i]+"", true);
			writeFile(i+".jpg", file[0]);
			writeFile(i+".txt", file[1]);
		}
	}
	
	private static void getMime(MIE mie, int first, int last) throws IOException{
		for(int i = first; i <= last; i++){
			byte[] file = mie.getMime(i+"", true);
			writeFile(i+".mime", file);
		}
	}
	
	private static byte[] readImg(int id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im"+id+".jpg");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private static byte[] readTxt(int id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags"+id+".txt");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
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

	private static byte[] readMime(int id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_mime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private static byte[] readDMime(int id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_dmime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private static void writeFile(String name, byte[] buffer) throws IOException{
		FileOutputStream out = new FileOutputStream(name);
		out.write(buffer);
		out.close();
	}
	
	private static int[] getIds(int min, int max){
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
