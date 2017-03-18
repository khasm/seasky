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

import javax.mail.MessagingException;

import mie.SearchResult;
import mie.crypto.TimeSpec;
import mie.MIE;
import mie.MIEClient;
import mie.Cache;

public class ClientThread extends Thread {

	private static final String UNSTRUCTURED_IMG_NAME_FORMAT = "/imgs/im%s.jpg";
	private static final String UNSTRUCTURED_TXT_NAME_FORMAT = "/tags/tags%s.txt";
	private static final String MIME_DOC_NAME_FORMAT = "/mime/%s.mime";

	private MIE mie;
	private Monitor monitor;
	private SearchStats searchStats;
	private File datasetDir;
	private Queue<Command> commands;
	private int threadId;
	private int nOperations;
	private long bytesUpload;
	private long bytesSearch;
	private long bytesDownload;

	public ClientThread(Queue<Command> commands, int threadId, Monitor monitor, MIE mie,
		File datasetDir) {
		this.commands = commands;
		this.threadId = threadId;
		this.mie = mie;
		this.monitor = monitor;
		this.datasetDir = datasetDir;
		searchStats = new SearchStats();
		bytesUpload = 0;
		bytesSearch = 0;
		bytesDownload = 0;
		nOperations = 0;
	}

	public void run() {
		monitor.ready();
		System.out.println("Thread "+threadId+" started");
		for(Command command: commands){
			String op = command.getOp().toLowerCase();
			try{
				if(op.startsWith(TestSet.ADD) || op.startsWith(TestSet.SEARCH)||
					op.startsWith(TestSet.GET)){
					int first = Integer.parseInt(command.getArgs()[0]);
					int last = command.getArgs().length == 2 ?
						Integer.parseInt(command.getArgs()[1]) : first;
					int nThreads = monitor.getNThreads();
					if(1 < nThreads){
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
						int reqDocs = nDocs % nThreads == 0 ? nDocs / nThreads : nDocs / nThreads +1;
						first = threadId * reqDocs;
						int max = first + reqDocs - 1;
						if(max > last)
							max = last;
						last = max;
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
						int cacheMode = 0;
						if(first != last && 2 < command.getArgs().length){
							if(command.getArgs()[2].equalsIgnoreCase("cache80")){
								cacheMode = 1;
							}
							else if(command.getArgs()[2].equalsIgnoreCase("cache_client100")){
								cacheMode = 2;
							}
							else if(command.getArgs()[2].equalsIgnoreCase("cache_server100")){
								cacheMode = 3;
							}
							else if(command.getArgs()[2].equalsIgnoreCase("double_cache")){
								cacheMode = 4;
							}
						}
						int[] ids = setupGet(cacheMode, first, last);
						System.out.println("Thread "+threadId+": "+ids[0]+" "+ids[ids.length-1]);
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
			}
			catch(IOException | MessagingException e){
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
			mie.addUnstructredDoc(""+i, img, txt);
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
			mie.addMime(""+i, mime);
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
		case(1):
			ids = getIds(first, last);
			break;
		case(2):
			ids = getIds(first, last);
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
		case(3):
			ids = getIds(first, last);
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
			ids = getIds(first, last);
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
		return ids;
	}

	private int[] getIds(int min, int max){
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
		}
		return ids;
	}

	private void printSearchResults(List<SearchResult> res, int id){
		System.out.println("Searching for: "+id);
		for(SearchResult result: res){
			System.out.printf("id: %s score: %.6f\n", result.getId(), result.getScore());
		}
	}
}