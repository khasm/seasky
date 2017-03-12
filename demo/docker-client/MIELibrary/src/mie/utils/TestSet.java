package mie.utils;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Queue;
import java.util.LinkedList;

public class TestSet {

	//options
	private static final String IP_OPTION = "ip";
	private static final String THREADS_OPTION = "threads";
	private static final String LOD_DIR_OPTION = "logdir";
	private static final String DATASET_OPTION = "path";
	private static final String MODE_OPTION = "mode";
	private static final String OP_SEQUENCE = "ops";
	//default option values
	private static final String DEFAULT_IP = "locahost";
	private static final String DEFAULT_DATASET_DIR = "/datasets";
	private static final String DEFAULT_LOG_DIR = "/logs";
	private static final int DEFAULT_THREADS = 1;
	//option values
	private static final String EXPLICIT_MODE = "explicit";
	private static final String DESCRIPTIVE_MODE = "descriptive";
	private static final String UNDEFINED_MODE = "";
	//error strings
	private static final String PARSING_ERROR_FORMAT = "Parsing error for %s on line %d: %s";
	private static final String ARG_ERROR = "Too %s arguments for %s";
	private static final String FEW_ERROR = "few";
	private static final String MANY_ERROR = "many";
	private static final String INVALID_COMMAND = "%s is not a recognized command";
	private static final String UNDEFINED_MODE_ERROR = "Script mode is undefined";
	private static final String UNRECOGNIZED_MODE = "The %s mode is not recognized";
	//valid commands
	private static final String ADD = "add";
	private static final String ADD_MIME = "addmime";
	private static final String GET = "get";
	private static final String GET_MIME = "getmime";
	private static final String SEARCH = "search";
	private static final String SEARCH_MIME = "searchmime";
	private static final String INDEX = "index";
	private static final String RESET = "reset";
	private static final String CLEAR = "clear";
	//command args
	private static final String INDEX_WAIT_SHORT = "w";
	private static final String INDEX_WAIT_LONG = "wait";
	//script keywords
	private static final String SINGLE_LINE_COMMENT = "//";
	private static final String MULTI_LINE_COMMENT_BEGIN = "/*";
	private static final String MULTI_LINE_COMMENT_END = "*/";
	private static final String KEY_VALUE_SEPARATOR = "=";
	private static final String OPS_SEQUENCE_SPLIT_REGEX = "[,;]";
	private static final String COMMAND_ARGS_SEPARATOR = " ";

	private File script;
	private Queue<Command> operations;
	private String ip;
	private File datasetDir;
	private File logDir;
	private int nThreads;
	private String mode;

	public TestSet(File script) {
		this.script = script;
		ip = DEFAULT_IP;
		datasetDir = new File(DEFAULT_DATASET_DIR);
		logDir = new File(DEFAULT_LOG_DIR);
		nThreads = DEFAULT_THREADS;
		mode = UNDEFINED_MODE;
		operations = new LinkedList<Command>();
	}

	public void start() throws IOException {
		parseScript();
		//TODO:
		System.out.printf("ip: %s\nthreads: %d\nlogs: %s\ndataset: %s\nmode: %s\n",
			ip, nThreads, logDir.toString(), datasetDir.toString(), mode);
		for(Command c: operations){
			System.out.printf("%s", c.getOp());
			for(String arg: c.getArgs())
				System.out.printf(" %s", arg);
			System.out.println();
		}
	}

	private void parseScript() throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(script));
		String line;
		int comment = 0;
		int lineNumber = 0;
		String opsSequence = "";
		while(null != (line = reader.readLine())){
			lineNumber++;
			if(line.endsWith(MULTI_LINE_COMMENT_END) && 0 < comment){
				comment--;
				continue;
			}
			else if(0 < comment){
				continue;
			}
			else if(line.startsWith(SINGLE_LINE_COMMENT) || 0 < comment){
				continue;
			}
			else if(line.startsWith(MULTI_LINE_COMMENT_BEGIN)){
				comment++;
				continue;
			}
			else if(line.endsWith(MULTI_LINE_COMMENT_END) && 0 == comment){
				throw new IOException(String.format(
					PARSING_ERROR_FORMAT, script.getName(), lineNumber, line));
			}
			int index = line.indexOf(KEY_VALUE_SEPARATOR);
			String option;
			String newLine;
			if(-1 == index){
				throw new IOException(
					String.format(PARSING_ERROR_FORMAT, script.getName(), lineNumber, line));
			}
			else{
				option = line.substring(index+1);
				newLine = line.substring(0, index).toLowerCase();
			}
			if(newLine.equals(IP_OPTION)){
				ip = option;
			}
			else if(newLine.equals(THREADS_OPTION)){
				try{
					nThreads = Integer.parseInt(option);
				}
				catch(NumberFormatException e){
					throw new IOException(
						String.format(PARSING_ERROR_FORMAT, script.getName(), lineNumber, line));		
				}
			}
			else if(newLine.equals(LOD_DIR_OPTION)){
				logDir = new File(option);
			}
			else if(newLine.equals(DATASET_OPTION)){
				datasetDir = new File(option);
			}
			else if(newLine.equals(MODE_OPTION)){
				mode = option;
			}
			else if(newLine.equals(OP_SEQUENCE)){
				opsSequence = option;
			}
		}
		if(mode.equalsIgnoreCase(UNDEFINED_MODE)){
			throw new IOException(UNDEFINED_MODE_ERROR);
		}
		if(mode.equalsIgnoreCase(EXPLICIT_MODE)){
			//TODO
		}
		else if(mode.equalsIgnoreCase(DESCRIPTIVE_MODE)){
			parseCommands(opsSequence);
		}
		else{
			throw new IOException(String.format(UNRECOGNIZED_MODE, mode));
		}
	}

	private void parseCommands(String opsSequence) throws IOException {
		String[] rawOps = opsSequence.split(OPS_SEQUENCE_SPLIT_REGEX);
		for(String s: rawOps){
			s = s.trim();
			if(s.isEmpty())
				continue;
			String[] args = s.split(COMMAND_ARGS_SEPARATOR);
			if(1 == args.length){
				if(args[0].equalsIgnoreCase(ADD)||args[0].equalsIgnoreCase(ADD_MIME)||
				   args[0].equalsIgnoreCase(GET)||args[0].equalsIgnoreCase(GET_MIME)||
				   args[0].equalsIgnoreCase(SEARCH)||args[0].equalsIgnoreCase(SEARCH_MIME)){
					throw new IOException(String.format(ARG_ERROR, FEW_ERROR, s));
				}
				else if(args[0].equalsIgnoreCase(INDEX)||args[0].equalsIgnoreCase(RESET)||
					args[0].equalsIgnoreCase(CLEAR)){
					operations.add(new Command(args[0]));
				}
				else{
					throw new IOException(String.format(INVALID_COMMAND, s));
				}
			}
			else{
				if(args[0].equalsIgnoreCase(ADD)||args[0].equalsIgnoreCase(ADD_MIME)||
				   args[0].equalsIgnoreCase(GET)||args[0].equalsIgnoreCase(GET_MIME)||
				   args[0].equalsIgnoreCase(SEARCH)||args[0].equalsIgnoreCase(SEARCH_MIME)){
					if(3 < args.length){
						throw new IOException(String.format(ARG_ERROR, MANY_ERROR, s));
					}
					String[] cArgs = new String[args.length-1];
					for(int i = 1; i < args.length; i++)
						cArgs[i-1] = args[i];
					operations.add(new Command(args[0], cArgs));
				}
				else if(args[0].equalsIgnoreCase(INDEX)){
					if(2 < args.length){
						throw new IOException(String.format(ARG_ERROR, MANY_ERROR, s));
					}
					if(args[1].equalsIgnoreCase(INDEX_WAIT_SHORT)||
						args[1].equalsIgnoreCase(INDEX_WAIT_LONG)){
						operations.add(new Command(args[0], new String[]{INDEX_WAIT_SHORT}));
					}
				}
				else if(args[0].equalsIgnoreCase(RESET)||args[0].equalsIgnoreCase(CLEAR)){
					throw new IOException(String.format(ARG_ERROR, MANY_ERROR, s));
				}
				else{
					throw new IOException(String.format(INVALID_COMMAND, s));
				}
			}
		}
	}
}