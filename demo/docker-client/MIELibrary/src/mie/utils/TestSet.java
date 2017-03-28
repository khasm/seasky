package mie.utils;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Queue;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;

import mie.MIE;
import mie.MIEClient;
import mie.crypto.TimeSpec;

public class TestSet {

	//error strings
	private static final String ARG_ERROR = "Too %s arguments for %s";
	private static final String FEW_ERROR = "few";
	private static final String MANY_ERROR = "many";
	private static final String UNRECOGNIZED_MODE = "The %s mode is not recognized";
	private static final String INVALID_DOCUMENT_TYPE = "The document type is not valid: %s";
	private static final String INVALID_ARGUMENT = "Invalid argument found in %s";
	private static final String INVALID_COMMAND = "%s is not a recognized command";
	//valid commands
	public static final String ADD = "add";
	public static final String ADD_MIME = "addmime";
	public static final String GET = "get";
	public static final String GET_MIME = "getmime";
	public static final String SEARCH = "search";
	public static final String SEARCH_MIME = "searchmime";
	public static final String INDEX = "index";
	public static final String RESET = "reset";
	public static final String CLEAR = "clear";
	public static final String SYNC = "sync";
	public static final String WAIT = "wait";
	public static final String BASE = "base";
	public static final String OPERATIONS = "operations";
	public static final String NO_WIPE = "nowipe";
	public static final String WIPE = "wipe";
	//command args
	private static final String INDEX_WAIT_SHORT = "w";
	private static final String INDEX_WAIT_LONG = "wait";
	private static final char TYPE_UNSTRUCTURED = 'u';
	private static final char TYPE_MIME = 'm';
	protected static final String CACHE_80_USER = "cache80";
	protected static final String CACHE_CLIENT_100_USER = "client100";
	protected static final String CACHE_SERVER_100_USER = "server100";
	protected static final String CACHE_DOUBLE_USER = "double";
	protected static final int CACHE_DISABLED = 0;
	protected static final int CACHE_80 = 1;
	protected static final int CACHE_CLIENT_100 = 2;
	protected static final int CACHE_SERVER_100 = 3;
	protected static final int CACHE_DOUBLE = 4;
	//script keywords
	protected static final String OPS_SEQUENCE_SPLIT_REGEX = "[,;]";
	private static final String COMMAND_ARGS_SEPARATOR = " ";
	//queue automatic generation
	private static final int PREFIX_DIVISION = 1000;
	private static final char TYPE_UNDEFINED = '«';
	private static final int N_OP_TYPES = 6; //if adding more operation types adjust this value
	private static final int ADD_INDEX = 0;
	private static final int SEARCH_INDEX = 1;
	private static final int GET_INDEX = 2;
	private static final int ADD_MIME_INDEX = 3;
	private static final int SEARCH_MIME_INDEX = 4;
	private static final int GET_MIME_INDEX = 5;

	private String aIp;
	private String aMode;
	private String aOpsSequence;
	private String aStats;
	private String aCache;
	private File aDatasetDir;
	private Queue<Command> aOperations;
	private int aNThreads;
	private int aNOperations;
	private long aBytesUpload;
	private long aBytesSearch;
	private long aBytesDownload;
	private boolean aIndexWait;
	private boolean aCompare;
	private boolean aWipe;
	private boolean aOutput;
	private SearchStats aSearchStats;
	private Monitor aMonitor;

	public TestSet(String ip, String mode, File datasetDir, String ops, int threads, String cache,
		boolean output) {
		aIp = ip;
		aMode = mode;
		aOpsSequence = ops;
		aStats = "";
		aCache = cache;
		aDatasetDir = datasetDir;
		aOperations = new LinkedList<Command>();
		aNThreads = threads;
		aNOperations = 0;
		aCompare = false;
		aWipe = mode.equalsIgnoreCase(TestSetGenerator.DESCRIPTIVE_MODE) ? true : false;
		aOutput = output;
		aBytesUpload = 0;
		aBytesSearch = 0;
		aBytesDownload = 0;
		aIndexWait = false;
		aSearchStats = new SearchStats();
		aMonitor = new Monitor(aNThreads);
	}

	public void start() throws IOException, ScriptErrorException {
		boolean clientCache = false;
		boolean serverCache = false;
		String[] limits = aCache.split(TestSetGenerator.LIMIT_SEPARATOR);
		for(String limit: limits){
			if(limit.equalsIgnoreCase(TestSetGenerator.CACHE_CLIENT)){
				clientCache = true;
			}
			else if(limit.equalsIgnoreCase(TestSetGenerator.CACHE_SERVER)){
				serverCache = true;
			}
			else if(!limit.equalsIgnoreCase(TestSetGenerator.NO_CACHE)){
				throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
					TestSetGenerator.CACHE_OPTION));
			}
		}
		parseCommands();
		MIE client = null;
		try{
			client = new MIEClient(aIp, false);
			client.setServerCache(serverCache);
		}
		catch(NoSuchAlgorithmException | NoSuchPaddingException e){
			e.printStackTrace();
			return;
		}
		if(aMode.equalsIgnoreCase(TestSetGenerator.EXPLICIT_MODE)){
			runExplicit(clientCache);
		}
		else if(aMode.equalsIgnoreCase(TestSetGenerator.DESCRIPTIVE_MODE)){
			runDescriptive(clientCache);
		}
		else{
			throw new ScriptErrorException(String.format(UNRECOGNIZED_MODE, aMode));
		}
		if(aOutput)
			stats(client);
		if(aWipe)
			client.wipe();
	}

	public String dump() {
		String dump = String.format("Ip: %s\nThreads: %d\nDatasetDir: %s\nMode: %s\nCache: %s\nOps: ",
			aIp, aNThreads, aDatasetDir.toString(), aMode, aCache);
		for(Command c: aOperations){
			dump += c.getOp()+" ";
			for(String a: c.getArgs()){
				dump += a+" ";
			}
		}
		return dump;
	}

	@Override
	public String toString() {
		return aStats;
	}

	private void stats(MIE client) {
		float networkTime = 0;
		StringBuffer serverSide = new StringBuffer();
		float indexWaitValue = 0;
		networkTime = client.getNetworkTime()/1000000000f;
		Map<String,String> stats = client.printServerStatistics();
		for(String key: stats.keySet()){
			String value = stats.get(key);
			serverSide.append(String.format("%s: %s\n", key, value));
			if(key.equalsIgnoreCase("Train time")||key.equalsIgnoreCase("Network feature time")||
				key.equalsIgnoreCase("Network index time")||key.equalsIgnoreCase("Index time")){
				indexWaitValue += Float.parseFloat(value);
			}
		}
		if(aIndexWait)
			networkTime -= indexWaitValue;
		long indexTime = TimeSpec.getIndexTime();
		long featureExtractionTime = TimeSpec.getFeatureTime();
		long encryptionTime = TimeSpec.getEncryptionTime();
		long encryptionSymmetricTime = TimeSpec.getEncryptionSymmetricTime();
		long encryptionCbirTime = TimeSpec.getEncryptionCbirTime();
		long encryptionMiscTime = TimeSpec.getEncryptionMiscTime();
		float totalTime = aMonitor.getTotalTime();
		String encryption = String.format("CBIR encryption: %f\nSymmetric encryption: %f\nMisc: %f\n",
			encryptionCbirTime/1000000000f, encryptionSymmetricTime/1000000000f,
			encryptionMiscTime/1000000000f);
		String clientSide = String.format(
				"featureTime: %f indexTime: %f cryptoTime: %f cloudTime: %f %s\nThroughput:\n"+
				"Total Bytes uploaded: %d Total Bytes searched: %d Total Bytes downloaded: %d\n"+
				"Bytes uploaded/s: %.6f Bytes searched/s: %.6f Bytes downloaded/s: %.6f\n"+
				"Total operations: %d Operations/s: %.6f",
				featureExtractionTime/1000000000f, indexTime/1000000000f, encryptionTime/1000000000f,
				networkTime, aMonitor.toString(), aBytesUpload, aBytesSearch, aBytesDownload,
				aBytesUpload/totalTime, aBytesSearch/totalTime, aBytesDownload/totalTime,
				aNOperations, aNOperations/totalTime);
		aStats = serverSide.toString() + encryption + clientSide;
	}

	private void parseCommands() throws ScriptErrorException {
		String[] rawOps = aOpsSequence.split(OPS_SEQUENCE_SPLIT_REGEX);
		for(String s: rawOps){
			s = s.trim();
			if(s.isEmpty())
				continue;
			String[] args = s.split(COMMAND_ARGS_SEPARATOR);
			if(1 == args.length){
				if(args[0].equalsIgnoreCase(ADD)||args[0].equalsIgnoreCase(ADD_MIME)||
				   args[0].equalsIgnoreCase(GET)||args[0].equalsIgnoreCase(GET_MIME)||
				   args[0].equalsIgnoreCase(SEARCH)||args[0].equalsIgnoreCase(SEARCH_MIME)||
				   args[0].equalsIgnoreCase(BASE)||args[0].equalsIgnoreCase(OPERATIONS)||
				   args[0].equalsIgnoreCase(WAIT)){
					throw new ScriptErrorException(String.format(ARG_ERROR, FEW_ERROR, s));
				}
				else if(args[0].equalsIgnoreCase(INDEX)||args[0].equalsIgnoreCase(RESET)||
					args[0].equalsIgnoreCase(CLEAR)||args[0].equalsIgnoreCase(SYNC)){
					aOperations.add(new Command(args[0]));
				}
				else if(args[0].equalsIgnoreCase(TestSetGenerator.COMPARE)){
					aCompare = true;
				}
				else if(args[0].equalsIgnoreCase(NO_WIPE)){
					aWipe = false;
				}
				else if(args[0].equalsIgnoreCase(WIPE)){
					aWipe = true;
				}
				else{
					throw new ScriptErrorException(String.format(INVALID_COMMAND, s));
				}
			}
			else{
				if(args[0].equalsIgnoreCase(ADD)||args[0].equalsIgnoreCase(ADD_MIME)||
				   args[0].equalsIgnoreCase(GET)||args[0].equalsIgnoreCase(GET_MIME)||
				   args[0].equalsIgnoreCase(SEARCH)||args[0].equalsIgnoreCase(SEARCH_MIME)||
				   args[0].equalsIgnoreCase(BASE)){
					if(4 < args.length){
						throw new ScriptErrorException(String.format(ARG_ERROR, MANY_ERROR, s));
					}
					String[] cArgs = new String[args.length-1];
					for(int i = 1; i < args.length; i++)
						cArgs[i-1] = args[i];
					aOperations.add(new Command(args[0], cArgs));
				}
				else if(args[0].equalsIgnoreCase(OPERATIONS)||args[0].equalsIgnoreCase(WAIT)){
					if(2 < args.length){
						throw new ScriptErrorException(String.format(ARG_ERROR, MANY_ERROR, s));
					}
					else{
						aOperations.add(new Command(args[0], new String[]{args[1]}));
					}
				}
				else if(args[0].equalsIgnoreCase(INDEX)){
					if(2 < args.length){
						throw new ScriptErrorException(String.format(ARG_ERROR, MANY_ERROR, s));
					}
					if(args[1].equalsIgnoreCase(INDEX_WAIT_SHORT)||
						args[1].equalsIgnoreCase(INDEX_WAIT_LONG)){
						aOperations.add(new Command(args[0], new String[]{args[1]}));
						aIndexWait = true;
					}
					else{
						throw new ScriptErrorException(String.format(INVALID_ARGUMENT, s));
					}
				}
				else if(args[0].equalsIgnoreCase(RESET)||args[0].equalsIgnoreCase(CLEAR)||
					args[0].equalsIgnoreCase(SYNC)){
					throw new ScriptErrorException(String.format(ARG_ERROR, MANY_ERROR, s));
				}
				else{
					throw new ScriptErrorException(String.format(INVALID_COMMAND, s));
				}
			}
		}
	}

	private void runExplicit(boolean cache) throws IOException {
		if(aCompare)
			runCompare();
		aMonitor = new Monitor(aNThreads);
		ClientThread[] threads = new ClientThread[aNThreads];
		for(int i = 0; i < aNThreads; i++){
			try{
				MIE client = new MIEClient(aIp, cache);
				threads[i] = new ClientThread(aOperations, i, aMonitor, client, aDatasetDir, 0);
				threads[i].start();
			}
			catch(NoSuchAlgorithmException | NoSuchPaddingException e){
				e.printStackTrace();
			}
		}
		aMonitor.start();
		try{
			for(int i = 0; i < aNThreads; i++){
				System.out.printf("Waiting for thread %d\n", i);
				threads[i].join();
				aBytesDownload += threads[i].getBytesDownload();
				aBytesUpload += threads[i].getBytesUpload();
				aBytesSearch += threads[i].getBytesSearch();
				aNOperations += threads[i].getNOperations();
			}
		}
		catch(InterruptedException e){
			e.printStackTrace();
		}
		aMonitor.end();
		for(int i = 0; i < aNThreads; i++){
			aSearchStats.merge(threads[i].getSearchStats());
		}
		if(aSearchStats.hasData())
			System.out.println(aSearchStats.toString());
	}

	private void runCompare() {
		Monitor m = new Monitor(1);
		Queue<Command> q = new LinkedList<Command>();
		q.add(new Command(TestSetGenerator.COMPARE));
		ClientThread t = new ClientThread(q, 0, m, null, aDatasetDir, 0);
		t.start();
		m.start();
		try{
			t.join();
			m.end();
		}
		catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	@SuppressWarnings("fallthrough")
	private void runDescriptive(boolean cache) throws ScriptErrorException, IOException {
		Command base = null;
		Map<String, Double> ratios = new HashMap<String, Double>();
		double total = 0;
		int nOp = 0;
		int cacheMode = CACHE_DISABLED;
		for(Command com: aOperations){
			String op = com.getOp();
			if(op.equalsIgnoreCase(BASE)){
				if(null == base)
					base = com;
			}
			else if(op.equalsIgnoreCase(OPERATIONS)){
				try{
					nOp = Integer.parseInt(com.getArgs()[0]);
				}
				catch(NumberFormatException e){
					throw new ScriptErrorException(String.format(INVALID_ARGUMENT, op+
						COMMAND_ARGS_SEPARATOR+com.getArgs()[0]));
				}
			}
			else{
				String[] args = com.getArgs();
				double arg;
				try{
					arg = Double.parseDouble(args[0]);
				}
				catch(NumberFormatException e){
					throw new ScriptErrorException(String.format(INVALID_ARGUMENT, op+
						COMMAND_ARGS_SEPARATOR+args[0]));
				}
				if(op.equalsIgnoreCase(GET) || op.equalsIgnoreCase(GET_MIME)){
					if(2 == args.length){
						if(args[1].equalsIgnoreCase(CACHE_80_USER)){
							cacheMode = CACHE_80;
						}
						else if(args[1].equalsIgnoreCase(CACHE_CLIENT_100_USER)){
							cacheMode = CACHE_CLIENT_100;
						}
						else if(args[1].equalsIgnoreCase(CACHE_SERVER_100_USER)){
							cacheMode = CACHE_SERVER_100;
						}
						else if(args[1].equalsIgnoreCase(CACHE_DOUBLE_USER)){
							cacheMode = CACHE_DOUBLE;
						}
						else{
							throw new ScriptErrorException(String.format(INVALID_ARGUMENT, op+
								COMMAND_ARGS_SEPARATOR+args[0]+COMMAND_ARGS_SEPARATOR+args[1]));
						}
					}
				}
				else if(!op.equalsIgnoreCase(ADD)&&!op.equalsIgnoreCase(ADD_MIME)&&
					!op.equalsIgnoreCase(SEARCH)&&!op.equalsIgnoreCase(SEARCH_MIME)){
					throw new ScriptErrorException(String.format(INVALID_COMMAND, op));
				}
				total += arg;
				Double value = ratios.get(op);
				if(null == value){
					ratios.put(op, arg);
				}
				else{
					ratios.put(op, value + arg);
				}
			}
		}
		Pair<ClientThread,Integer> tmpPair = setupBase(base);
		ClientThread baseWorker = tmpPair.getKey();
		int nDocs = null == tmpPair.getValue() ? 0 : tmpPair.getValue();
		//normalize probabilities representation
		for(String key: ratios.keySet()){
			ratios.put(key, ratios.get(key)/total);
		}
		//calculate number of each type of operation
		int[] opDistributionAbs = new int[N_OP_TYPES];
		int newNOp = 0;
		for(int i = 0; i < N_OP_TYPES; i++){
			double prob = getProbability(i, ratios);
			opDistributionAbs[i] = (int)(prob * nOp);
			newNOp += opDistributionAbs[i];
		}
		//create threads with randomized operations
		aMonitor = new Monitor(aNThreads);
		ClientThread[] threads = new ClientThread[aNThreads];
		double[] opSplitProb = new double[N_OP_TYPES-1];
		Set<Integer> toAdd = new HashSet<Integer>();
		for(int i = 0; i < aNThreads; i++){
			Queue<Command> threadQueue = new LinkedList<Command>();
			int[] nOpDist = opDistributionAbs.clone();
			int tmpNOp = newNOp;
			//adjust probability limits
			updateCumulativeFrequencies(opSplitProb, ratios);
			Map<String,Double> tmpRatios = null;
			Random rng = new Random(System.nanoTime());
			int idsU[] = CACHE_DISABLED == cacheMode ? null : getIds(0, nDocs,
				opDistributionAbs[GET_INDEX]);
			int idsM[] = CACHE_DISABLED == cacheMode ? null : getIds(0, nDocs,
				opDistributionAbs[GET_MIME_INDEX]);
			if(null != idsU){
				for(int iu: idsU){
					toAdd.add(iu);
				}
			}
			if(null != idsM){
				for(int im: idsM){
					toAdd.add(im);
				}
			}
			int indexU = 0;
			int indexM = 0;
			while(0 < tmpNOp){
				//generate next operation
				double d = rng.nextDouble();
				int pi = 0;
				while(pi < N_OP_TYPES-1){
					if(d < opSplitProb[pi])
						break;
					else
						pi++;
				}
				int[] ids = null;
				int index = 0;
				switch(pi){
					case(GET_INDEX):
					ids = idsU;
					index = indexU++;
					break;
					case(GET_MIME_INDEX):
					ids = idsM;
					index = indexM++;
				}
				threadQueue.add(generateCommand(pi, nDocs, ids, index));
				//update ratios
				nOpDist[pi]--;
				tmpNOp--;
				tmpRatios = updateRatios(tmpNOp, nOpDist, tmpRatios);
				updateCumulativeFrequencies(opSplitProb, tmpRatios);
			}
			try{
				MIE client = new MIEClient(aIp, cache);
				threads[i] = new ClientThread(threadQueue, i, aMonitor, client, aDatasetDir,
					(i+1)*PREFIX_DIVISION);
				threads[i].start();
			}
			catch(NoSuchAlgorithmException | NoSuchPaddingException e){
				e.printStackTrace();
				break;
			}
		}
		try{
			baseWorker.join();
			boolean complete = false;
			switch(cacheMode){
				case(CACHE_SERVER_100):
				complete = true;
				case(CACHE_DOUBLE):
				setupServerCache(toAdd, complete, aIp);
			}
		}
		catch(InterruptedException e){
			e.printStackTrace();
			return;
		}
		System.out.println("Base worker finished");
		aMonitor.start();
		try{
			for(int i = 0; i < aNThreads; i++){
				System.out.printf("Waiting for thread %d\n", i);
				threads[i].join();
				aBytesDownload += threads[i].getBytesDownload();
				aBytesUpload += threads[i].getBytesUpload();
				aBytesSearch += threads[i].getBytesSearch();
				aNOperations += threads[i].getNOperations();
			}
		}
		catch(InterruptedException e){
			e.printStackTrace();
		}
		aMonitor.end();
		for(int i = 0; i < aNThreads; i++){
			aSearchStats.merge(threads[i].getSearchStats());
		}
		if(aSearchStats.hasData())
			System.out.println(aSearchStats.toString());
	}

	private Command generateCommand(int probIndex, int nDocs, int[] ids, int index)
		throws ScriptErrorException {
		String op = getOpType(probIndex);
		char mode = TYPE_UNDEFINED;
		int maxDocs = -1;
		int arg = -1;
		switch(probIndex){
			case(ADD_INDEX):
			maxDocs = getMaxDocs(TYPE_UNSTRUCTURED);
			break;
			case(ADD_MIME_INDEX):
			maxDocs = getMaxDocs(TYPE_MIME);
			break;
			case(GET_INDEX):
			case(GET_MIME_INDEX):
			if(null != ids)
				arg = ids[index];
		}
		if(-1 == arg){
			int limit = -1 != maxDocs ? maxDocs : nDocs;
			Random rng = new Random(System.nanoTime());
			arg = (int)(rng.nextDouble()*limit);
		}
		return new Command(op, new String[]{""+arg});
	}

	private void updateCumulativeFrequencies(double[] opSplitProb, Map<String,Double> ratios) {
		double prob = 0;
		for(int pi = 0; pi < N_OP_TYPES-1; pi++){
			prob += getProbability(pi, ratios);
			opSplitProb[pi] = prob;
		}
	}

	private Map<String,Double> updateRatios(int newNOp, int[] opDistAbs,
		Map<String,Double> ratios) {
		if(null == ratios)
			ratios = new HashMap<String,Double>();
		for(int i = 0; i < N_OP_TYPES; i++){
			double newRatio = (double)opDistAbs[i]/(double)newNOp;
			ratios.put(getOpType(i), newRatio);
		}
		return ratios;
	}

	private double getProbability(int index, Map<String,Double> ratios) {
		Double tmp = ratios.get(getOpType(index));
		return null == tmp ? 0 : tmp;
	}

	private String getOpType(int index) {
		String op = null;
		switch(index){
			case(ADD_INDEX):
			op = ADD;
			break;
			case(SEARCH_INDEX):
			op = SEARCH;
			break;
			case(GET_INDEX):
			op = GET;
			break;
			case(ADD_MIME_INDEX):
			op = ADD_MIME;
			break;
			case(SEARCH_MIME_INDEX):
			op = SEARCH_MIME;
			break;
			case(GET_MIME_INDEX):
			op = GET_MIME;
			break;
		}
		return op;
	}

	private Pair<ClientThread, Integer> setupBase(Command base) throws ScriptErrorException,
			IOException {
		if(null == base)
			return new Pair<ClientThread, Integer>(null, null);
		String arg = base.getArgs()[0];
		int index = arg.length()-1;
		char mode = arg.charAt(index);
		int nDocs;
		try{
			nDocs = Integer.parseInt(arg.substring(0, index))-1;
		}
		catch(NumberFormatException e){
			throw new ScriptErrorException(String.format(INVALID_ARGUMENT, base.getOp()+
						COMMAND_ARGS_SEPARATOR+base.getArgs()[0]));
		}
		int maxDocs = getMaxDocs(mode)-1;
		Queue<Command> baseSetup = new LinkedList<Command>();
		String op = "";
		switch(mode){
			case(TYPE_UNSTRUCTURED):
			op = ADD;
			break;
			case(TYPE_MIME):
			op = ADD_MIME;
			break;
			default:
			throw new ScriptErrorException(String.format(INVALID_DOCUMENT_TYPE, arg));
		}
		if(nDocs > maxDocs)
			nDocs = maxDocs;
		baseSetup.add(new Command(op, new String[]{"0", ""+nDocs}));
		baseSetup.add(new Command(INDEX, new String[]{INDEX_WAIT_SHORT}));
		try{
			MIE client = new MIEClient(aIp, false);
			Monitor m = new Monitor(1);
			ClientThread thread = new ClientThread(baseSetup, 0, m, client, aDatasetDir, 0);
			thread.start();
			m.start();
			System.out.println("Setting up base");
			return new Pair<ClientThread,Integer>(thread, nDocs);
		}
		catch(NoSuchAlgorithmException | NoSuchPaddingException e){
			e.printStackTrace();
			return new Pair<ClientThread,Integer>(null, nDocs);
		}
	}

	private int getMaxDocs(char type) throws ScriptErrorException {
		int maxDocs = 0;
		switch(type){
			case(TYPE_UNSTRUCTURED):
			String[] imgs = new File(aDatasetDir, "imgs").list();
			String[] txts = new File(aDatasetDir, "tags").list();
			maxDocs = imgs.length > txts.length ? txts.length : imgs.length;
			break;
			case(TYPE_MIME):
			maxDocs = new File(aDatasetDir, "mime").list().length;
			break;
			default:
			throw new ScriptErrorException(String.format(INVALID_DOCUMENT_TYPE, ""+type));
		}
		return maxDocs;
	}

	protected static void setupServerCache(Set<Integer> toAdd, boolean complete, String ip) {
		MIE mie = null;
		try{
			mie = new MIEClient(ip, false);
		}
		catch(NoSuchAlgorithmException | NoSuchPaddingException | IOException e){
			e.printStackTrace();
			return;
		}
		if(complete){
			for(Integer i: toAdd){
				mie.getUnstructuredDoc(i+"", true);
			}
			mie.clearServerTimes();
			mie.clearCache();
			TimeSpec.reset();
		}
		else{
			int n = toAdd.size();
			double nc = n/10.0*8.0;
			Integer[] intg = toAdd.toArray(new Integer[0]);
			for(int i = 0; i < nc; i++){
				mie.getUnstructuredDoc(intg[i]+"", true);
			}
		}
		mie.clearServerTimes();
		mie.clearCache();
		TimeSpec.reset();
	}

	public static int[] getIds(int min, int max, int length){
		int[] ids = new int[length];
		if(0 == length)
			return ids;
		List<Integer> not_used = new LinkedList<Integer>();
		Random rng = new Random(53234);
		int tmp = max - min + 1;
		//put all values between min and max in an array
		for(int i = 0; i < tmp; i++)
			not_used.add(min + i);
		//this algorithm will be biased towards the first random selected
		//to help offset that, the first ~5% of ids will always be random
		//and different, with a minimum of 1 time
		int randomizer = (int)Math.nextUp(length / 20);
		for(int i = 0; i < ids.length; i++){
			double req = rng.nextDouble();
			//pick a not used value 20% of the time (always pick one for the first X times)
			if(req > 0.8 || i < randomizer){
				int index = (int)(rng.nextDouble()*not_used.size());
				ids[i] = not_used.remove(index);
			}
			//pick an already used value 80% of the time
			else{
				int index = (int)(rng.nextDouble()*i);
				ids[i] = ids[index];
			}
		}
		return ids;
	}
}