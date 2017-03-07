import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.List;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.lang.ClassLoader;
import java.lang.ClassNotFoundException;
import java.lang.InstantiationException;
import java.lang.IllegalAccessException;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.JavaFileObject;
import javax.tools.DiagnosticCollector;
import javax.tools.Diagnostic;
import javax.tools.StandardJavaFileManager;
import javax.mail.MessagingException;
import javafx.util.Pair;

public class Main {

	private static final String CLASS_EXTENSION = ".class";
	private static final String SOURCE_EXTENSION = ".java";
	private static final String LIB_EXTENSION = ".jar";
	private static final String DEFAULT_IN_DIR = "dataset";
	private static final String DEFAULT_OUT_DIR = "output";
	private static final String PARSER_DIR = "parsers";
	private static final String WRITER_DIR = "writers";
	private static final String CONVERTER_DIR = "converters";
	private static final String DIR_CONF = "dirs.txt";
	private static final String IN_DIR_CONF = "inputDir=";
	private static final String OUT_DIR_CONF = "outputDir=";
	private static final String LIB_DIR = "libs";

	private static final String DIR_NOT_FOUND = "%s directory not found\n";
	private static final String LIST_OPTIONS = "%d) %s\n";
	private static final String INVALID_OPTION = "Invalid selection";
	private static final String MIME_ERR = "Error processing %s\n";
	private static final String SELECTION = "Select a %s:\n";
	private static final String PARSER_CAT = "parser";
	private static final String WRITER_CAT = "writer";
	private static final String CONVERTER_CAT = "converter";

	public static void main(String[] args) throws MalformedURLException, IOException {
		File parserDir = new File(PARSER_DIR);
		if(!parserDir.exists()){
			System.out.format(DIR_NOT_FOUND, PARSER_DIR);
			System.exit(1);
		}
		File writerDir = new File(WRITER_DIR);
		if(!writerDir.exists()){
			System.out.format(DIR_NOT_FOUND, WRITER_DIR);
			System.exit(1);
		}
		File converterDir = new File(CONVERTER_DIR);
		if(!converterDir.exists()){
			System.out.format(DIR_NOT_FOUND, CONVERTER_DIR);
			System.exit(1);
		}
		//set input/output dirs
		File dirs = new File(DIR_CONF);
		File inDir = new File(DEFAULT_IN_DIR);
		File outDir = new File(DEFAULT_OUT_DIR);
		//load available parsers
		String[] parsers = getOptions(parserDir);
		//load available writers
		String[] writers = getOptions(writerDir);
		//load available converters
		String[] converters = getOptions(converterDir);
		if(parsers.length == 0){
			System.out.println("No parsers found");
			System.exit(1);
		}
		if(writers.length == 0){
			System.out.println("No writers found");
			System.exit(1);	
		}
		if(converters.length == 0){
			System.out.println("No converters found");
			System.exit(1);	
		}
		int selection = selectOption(parsers, PARSER_CAT);
		DatasetParser parser = (DatasetParser)loadClass(parserDir, parsers[selection]);
		selection = selectOption(converters, CONVERTER_CAT);
		Converter converter = (Converter)loadClass(converterDir, converters[selection]);
		selection = selectOption(writers, WRITER_CAT);
		DocumentWriter writer = (DocumentWriter)loadClass(writerDir, writers[selection]);
		parser.setDatasetDir(inDir);
		writer.setOutDir(outDir);
		System.out.printf("%s\n%s\n%s\n", parser.getDescription(), converter.getDescription(),
			writer.getDescription());
		int count = 0;
		while(parser.hasNext()){
			Pair<String,Pair<List<byte[]>,List<String>>> contents = parser.getNextDoc();
			List<byte[]> jpgImgs = new LinkedList<byte[]>();
			for(byte[] iImg: contents.getValue().getKey()){
				byte[] oImg = converter.convert(iImg);
				if(null != oImg)
					jpgImgs.add(oImg);
			}
			try{
				writer.writeDocument(""+count, jpgImgs,
					contents.getValue().getValue());
				count++;
			}
			catch(MessagingException e){
				System.err.format(MIME_ERR, contents.getKey());
				e.printStackTrace();
			}
		}
	}

	private static int selectOption(String[] options, String category) {
		int selection = -1;
		Scanner in = new Scanner(System.in);
		//select parser
		while(selection == -1){
			System.out.format(SELECTION, category);
			for(int index = 0; index < options.length; index++){
				System.out.format(LIST_OPTIONS, index+1, options[index]);
			}
			String input = in.nextLine();
			if(input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit"))
				System.exit(0);
			try{
				int tmp = Integer.parseInt(input);
				if(tmp >= 1 && tmp <= options.length)
					selection = tmp-1;
				else{
					System.out.println(INVALID_OPTION);	
				}
			}
			catch(NumberFormatException e) {
				System.out.println(INVALID_OPTION);
			}
		}
		return selection;
	}

	private static Object loadClass(File classDir, String className) throws MalformedURLException {
		//get class loader ready
		URL classesUrl = classDir.toURI().toURL();
		URL[] tmpUrls = new URL[]{classesUrl};
		ClassLoader classLoader = new URLClassLoader(tmpUrls);
		try{
			Class optionClass = classLoader.loadClass(className);
			Object optionInstance = optionClass.newInstance();
			return optionInstance;
		}
		catch(ClassNotFoundException | InstantiationException | IllegalAccessException e){
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}

	private static String[] getOptions(File optionsDir) throws IOException {
		//search for all classes available to load
		File[] files = optionsDir.listFiles();
		//TODO: handle directories for classes inside java packages
		Map<String,File> ready = new HashMap<String,File>();
		Map<String,File> source = new HashMap<String,File>();
		for(File file: files){
			if(file.toString().endsWith(CLASS_EXTENSION)){
				String optionName = file.getName();
				optionName = optionName.substring(0, optionName.length()-CLASS_EXTENSION.length());
				if(!source.containsKey(optionName)){
					ready.put(optionName, file);
				}
				else{
					if(file.lastModified() >= source.get(optionName).lastModified()){
						source.remove(optionName);
						ready.put(optionName, file);
					}
				}
			}
			if(file.toString().endsWith(SOURCE_EXTENSION)){
				String optionName = file.getName();
				optionName = optionName.substring(0, optionName.length()-SOURCE_EXTENSION.length());
				if(!ready.containsKey(optionName)){
					source.put(optionName, file);
				}
				else{
					if(file.lastModified() >= ready.get(optionName).lastModified()){
						ready.remove(optionName);
						source.put(optionName, file);
					}
				}
			}
		}
		Map<String,File> compiled = compile(source);
		String[] options = new String[ready.size()+compiled.size()];
		int index = 0;
		for(File file: ready.values()){
			String className = file.toString();
			className = className.substring(className.lastIndexOf(File.separator)+1,
				className.length()-CLASS_EXTENSION.length());
			options[index++] = className;
		}
		for(File file: compiled.values()){
			String className = file.toString();
			className = className.substring(className.lastIndexOf(File.separator)+1,
				className.length()-CLASS_EXTENSION.length());
			options[index++] = className;
		}
		return options;
	}

	private static Map<String,File> compile(Map<String,File> source) throws IOException {
		File[] files = source.values().toArray(new File[0]);
		Map<String,File> ready = new HashMap<String,File>();
		for(File file: files){
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			DiagnosticCollector<JavaFileObject> diagnostics = new 
				DiagnosticCollector<JavaFileObject>();
			StandardJavaFileManager fileManager = compiler.getStandardFileManager
				(diagnostics, null, null);
			Iterable<? extends JavaFileObject> compilationUnits = 
				fileManager.getJavaFileObjectsFromFiles(Arrays.asList(new File[]{file}));
			String libs = "bin";
			List<String> extLibs = getLibraries();
			for(String lib: extLibs)
				libs += ":"+lib;
			System.out.println(libs);
			String[] options = new String[]{"-cp", libs};
			Iterable<String> compileOptions = Arrays.asList(options);
			boolean done = compiler.getTask(null, fileManager, diagnostics, /*compileOptions*/null, null, 
				compilationUnits).call();
			for ( Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
				System.out.format("%s:%s:%d %s%n", diagnostic.getKind().toString(),
					diagnostic.getSource().getName(), diagnostic.getLineNumber(),
					diagnostic.getMessage(null));
			fileManager.close();
			if(done){
				String fileName = file.toString();
				String optionName = file.getName();
				ready.put(optionName.substring(0, optionName.length()-SOURCE_EXTENSION.length())
					+CLASS_EXTENSION, new File(fileName.substring(0, fileName.length()-
					SOURCE_EXTENSION.length())+CLASS_EXTENSION));
			}
		}
		return ready;
	}

	private static List<String> getLibraries() throws IOException{
		File libsRoot = new File(LIB_DIR);
		List<String> libs = new LinkedList<String>();
		if(libsRoot.isDirectory()){
			Queue<File> files = new LinkedList<File>(Arrays.asList(libsRoot.listFiles()));
			while(!files.isEmpty()){
				File file = files.poll();
				if(file.isDirectory())
					files.addAll(Arrays.asList(file.listFiles()));
				else if(file.getName().endsWith(LIB_EXTENSION))
					libs.add(file.toString());
			}
		}
		return libs;
	}
}