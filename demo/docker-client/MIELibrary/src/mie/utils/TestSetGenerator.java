package mie.utils;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

public class TestSetGenerator {

	//error strings
	private static final String PARSING_ERROR_FORMAT = "Parsing error for %s on line %d: %s";
	private static final String INVALID_COMMAND = "%s is not a recognized command";
	private static final String ARG_ERROR = "Too %s arguments for %s";
	private static final String FEW_ERROR = "few";
	private static final String INVALID_ARGUMENT = "Invalid argument found in %s";
	//script keywords
	private static final String SINGLE_LINE_COMMENT = "//";
	private static final String MULTI_LINE_COMMENT_BEGIN = "/*";
	private static final String MULTI_LINE_COMMENT_END = "*/";
	private static final String KEY_VALUE_SEPARATOR = "[= ]";
	private static final String VALUE_SEPARATOR = ",";
	protected static final String LIMIT_SEPARATOR = "-";
	//options
	private static final String IP_OPTION = "ip";
	private static final String THREADS_OPTION = "threads";
	private static final String DATASET_OPTION = "path";
	private static final String MODE_OPTION = "mode";
	private static final String OP_SEQUENCE = "ops";
	private static final String STEP = "step";
	private static final String OUTPUT = "output";
	protected static final String CACHE_OPTION = "cache";
	protected static final String COMPARE = "compare";
	//options values
	private static final String OUT_PRINT = "print";
	private static final String OUT_NONE = "none";
	protected static final String EXPLICIT_MODE = "explicit";
	protected static final String DESCRIPTIVE_MODE = "descriptive";
	protected static final String CACHE_CLIENT = "client";
	protected static final String CACHE_SERVER = "server";
	protected static final String NO_CACHE = "none";
	//default values
	private static final String DEFAULT_MODE = EXPLICIT_MODE;
	private static final String DEFAULT_IP = "localhost";
	private static final String DEFAULT_DATASET_DIR = "/datasets";
	private static final String DEFAULT_CACHE = NO_CACHE;
	private static final String DEFAULT_THREADS = "1";
	private static final String DEFAULT_OUTPUT = OUT_PRINT;
	//testset variable settings
	private static final int TOTAL_VARIABLES = 3;
	private static final int THREAD_VARIABLE_INDEX = 0;
	private static final int CACHE_VARIABLE_INDEX = 1;
	private static final int OPS_VARIABLE_INDEX = 2;

	private String mode;
	private String ip;
	private String output;
	private File dataset;
	private String[] args;
	private File script;
	private List<TestSet> testSets;

	private TestSetGenerator(String[] args, File script) {
		mode = DEFAULT_MODE;
		ip = DEFAULT_IP;
		dataset = new File(DEFAULT_DATASET_DIR);
		this.args = args;
		this.script = script;
		testSets = new LinkedList<TestSet>();
		output = DEFAULT_OUTPUT;
	}

	public TestSetGenerator(String[] args) {
		this(args, null);
	}

	public TestSetGenerator(File file) {
		this(null, file);
	}

	public void generateTestSets() throws IOException, ScriptErrorException {
		if(null != script)
			parseScript();
		int nextArg = 0;
		String[][] testSetVariables = new String[TOTAL_VARIABLES][];
		boolean ops = false;
		StringBuffer opsBuffer = new StringBuffer();
		List<String> todoOps = new LinkedList<String>();
		boolean compare = false;
		while(nextArg < args.length){
			try{
				if(args[nextArg].equalsIgnoreCase(COMPARE)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					compare = true;
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase(THREADS_OPTION)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					nextArg = processThreadOption(testSetVariables, nextArg);
				}
				else if(args[nextArg].equalsIgnoreCase(MODE_OPTION)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					mode = args[nextArg+1];
					if(!mode.equalsIgnoreCase(EXPLICIT_MODE) &&
						!mode.equalsIgnoreCase(DESCRIPTIVE_MODE)){
						throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
							args[nextArg]));
					}
					nextArg += 2;
				}
				else if(args[nextArg].equalsIgnoreCase(IP_OPTION)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					ip = args[nextArg+1];
					nextArg += 2;
				}
				else if(args[nextArg].equalsIgnoreCase(CACHE_OPTION)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					nextArg = processCacheOption(testSetVariables, nextArg);
				}
				else if(args[nextArg].equalsIgnoreCase(DATASET_OPTION)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					dataset = new File(args[nextArg+1]);
					nextArg += 2;
				}
				else if(args[nextArg].equalsIgnoreCase(OP_SEQUENCE)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					ops = true;
					nextArg++;
				}
				else if(args[nextArg].equalsIgnoreCase(OUTPUT)){
					if(ops)
						ops = processOps(todoOps, opsBuffer);
					output = args[nextArg+1];
					if(!output.equalsIgnoreCase(OUT_PRINT)){
						throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
							args[nextArg]));
					}
					nextArg += 2;
				}
				//convenience for command line arguments
				else if(args[nextArg].equalsIgnoreCase(OUT_PRINT)){
					output = args[nextArg++];
				}
				else if(ops){
					opsBuffer.append(args[nextArg++]);
					opsBuffer.append(" ");
				}
				else{
					throw new ScriptErrorException(String.format(INVALID_COMMAND, args[nextArg]));
				}
			}
			catch(ArrayIndexOutOfBoundsException e){
				throw new ScriptErrorException(String.format(ARG_ERROR, FEW_ERROR,
					args[nextArg]));
			}
		}
		if(compare){
			String comp_op = COMPARE + TestSet.OPS_SEQUENCE_SPLIT_REGEX.charAt(1) + TestSet.NO_WIPE;
			TestSet t = new TestSet(ip, EXPLICIT_MODE, dataset, comp_op, 1, NO_CACHE, false);
			t.start();
		}
		if(ops)
			ops = processOps(todoOps, opsBuffer);
		testSetVariables[OPS_VARIABLE_INDEX] = todoOps.toArray(new String[0]);
		if(null == testSetVariables[THREAD_VARIABLE_INDEX])
			testSetVariables[THREAD_VARIABLE_INDEX] = new String[]{DEFAULT_THREADS};
		if(null == testSetVariables[CACHE_VARIABLE_INDEX])
			testSetVariables[CACHE_VARIABLE_INDEX] = new String[]{DEFAULT_CACHE};
		for(String threads: testSetVariables[THREAD_VARIABLE_INDEX]){
			for(String cache: testSetVariables[CACHE_VARIABLE_INDEX]){
				for(String op: testSetVariables[OPS_VARIABLE_INDEX]){
					boolean o = !output.equalsIgnoreCase(OUT_NONE) ? true : false;
					testSets.add(new TestSet(ip, mode, dataset, op, Integer.parseInt(threads),
						cache, o));
				}
			}
		}
		for(TestSet t: testSets){
			t.start();
			if(output.equalsIgnoreCase(OUT_PRINT)){
				System.out.println(t);
			}
		}
	}

	private int processThreadOption(String[][] testSetVariables, int nextArg)
		throws ScriptErrorException {
		int ret = nextArg + 2;
		try{
			String nThreadsRaw = args[nextArg+1];
			int step = 0;
			if(nextArg+2 < args.length){
				if(args[nextArg+2].equalsIgnoreCase(STEP)){
					try{
						step = Integer.parseInt(args[nextArg+3]);
						if(0 == step)
							throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
								args[nextArg+2]+" "+args[nextArg+3]));
						ret = nextArg + 4;
					}
					catch(NumberFormatException e){
						throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
							args[nextArg+2]+" "+args[nextArg+3]));
					}
				}
			}
			String[] values = nThreadsRaw.split(VALUE_SEPARATOR);
			Set<String> threadValues = new HashSet<String>();
			for(String value: values){
				String[] limits = value.split(LIMIT_SEPARATOR);
				if(1 == limits.length){
					threadValues.add(limits[0]);
				}
				else if(2 == limits.length){
					try{
						int first = Integer.parseInt(limits[0]);
						int last = Integer.parseInt(limits[1]);
						int stepi = 0 == step ? 1 : step;
						if(0 > stepi || first > last){
							if(first < last){
								int tmp = first;
								first = last;
								last = tmp;
							}
							if(0 < stepi)
								stepi = -1*stepi;
							while(first >= last){
								threadValues.add(""+first);
								if(first == last)
									break;
								else
									first += stepi;
								if(first < last)
									first = last;
							}
						}
						else{
							if(first > last){
								int tmp = first;
								first = last;
								last = tmp;	
							}
							if(0 > stepi)
								stepi = -1*stepi;
							while(first <= last){
								threadValues.add(""+first);
								if(first == last)
									break;
								else
									first += stepi;
								if(first > last)
									first = last;
							}
						}
					}
					catch(NumberFormatException e){
						throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
							args[nextArg]+" "+args[nextArg+1]));	
					}
				}
				else{
					throw new ScriptErrorException(String.format(INVALID_ARGUMENT,
						args[nextArg]+" "+args[nextArg+1]));
				}
			}
			testSetVariables[THREAD_VARIABLE_INDEX] = threadValues.toArray(new String[0]);
		}
		catch(ArrayIndexOutOfBoundsException e){
			throw new ScriptErrorException(String.format(ARG_ERROR, FEW_ERROR,
				args[nextArg]));
		}
		return ret;
	}

	private int processCacheOption(String[][] testSetVariables, int nextArg)
		throws ScriptErrorException {
		Set<String> tmp = new HashSet<String>();
		String[] values = args[nextArg+1].split(VALUE_SEPARATOR);
		for(String value: values){
			/*String[] limits = value.split(LIMIT_SEPARATOR);
			for(String limit: limits){
				if(limit.equalsIgnoreCase(CACHE_CLIENT) || limit.equalsIgnoreCase(NO_CACHE) ||
					limit.equalsIgnoreCase(CACHE_SERVER))
					tmp.add(limit);
				else
					throw new ScriptErrorException(String.format(INVALID_ARGUMENT, args[nextArg]));
			}*/
			tmp.add(value);
		}
		testSetVariables[CACHE_VARIABLE_INDEX] = tmp.toArray(new String[0]);
		return nextArg + 2;
	}

	private boolean processOps(List<String> todoOps, StringBuffer opsBuffer) {
		todoOps.add(opsBuffer.toString());
		opsBuffer.delete(0, opsBuffer.length());
		return false;
	}

	private void parseScript() throws IOException, ScriptErrorException {
		StringBuffer buffer = removeComments();
		String[] lines = buffer.toString().split("\n");
		List<String> tmpArgs = new LinkedList<String>();
		int index = 0;
		int lineNumber = 0;
		for(String line: lines){
			lineNumber++;
			String[] values = line.split(KEY_VALUE_SEPARATOR); 
			if(1 == values.length){
				throw new ScriptErrorException(
					String.format(PARSING_ERROR_FORMAT, script.getName(), lineNumber, line));
			}
			else{
				for(String value: values){
					if(!value.isEmpty())
						tmpArgs.add(value);
				}
			}
		}
		args = tmpArgs.toArray(new String[0]);
	}

	private StringBuffer removeComments() throws IOException, ScriptErrorException {
		BufferedReader reader = new BufferedReader(new FileReader(script));
		String line;
		int comment = 0;
		int lineNumber = 0;
		StringBuffer buffer = new StringBuffer();
		int index;
		while(null != (line = reader.readLine())){
			lineNumber++;
			line +=  '\n';
			index = 0;
			while(index < line.length()){
				if(line.regionMatches(index, MULTI_LINE_COMMENT_BEGIN, 0,
					MULTI_LINE_COMMENT_BEGIN.length())){
					if(0 == comment)
						buffer.append(line.substring(0, index));
					comment++;
					line = line.substring(index+MULTI_LINE_COMMENT_BEGIN.length());
					index = 0;
				}
				else if(line.regionMatches(index, SINGLE_LINE_COMMENT, 0,
					SINGLE_LINE_COMMENT.length())){
					if(0 < comment){
						index += SINGLE_LINE_COMMENT.length();
					}
					else{
						if(0 == index){
							line = "";
						}
						else{
							line = line.substring(0, index) + '\n';
						}
					}
				}
				else if(line.regionMatches(index, MULTI_LINE_COMMENT_END, 0,
					MULTI_LINE_COMMENT_END.length())){
					if(0 == comment)
						throw new ScriptErrorException(String.format(
							PARSING_ERROR_FORMAT, script.getName(), lineNumber, line));
					comment--;
					if(line.endsWith(MULTI_LINE_COMMENT_END + '\n'))
						line = "";
					else
						line = line.substring(index+MULTI_LINE_COMMENT_END.length());
					index = 0;
				}
				else{
					index++;
				}
			}
			if(0 == comment)
				buffer.append(line);
		}
		return buffer;
	}
}