package mie.utils;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Comparator;
import mie.utils.DataPoint.Unit;
import mie.utils.DataPoint.Stat;

public class GNUPlot {

	//Errors
	private static final String MISSING_CLOSING_MARK = "Missing closing quotation marks on title";
	private static final String OUTPUT_DIR_ERROR = "Couldn't find or create directory %s";
	private static final String EXTRA_INPUT_ERROR = "Problem reading extra input file %s";
	private static final String INVALID_EXTRA_X_AXIS = "Incompatible X axis options.";
	//GNUPlot related constants
	private static final String BASE_SCRIPT =
		"set datafile commentschars '%s'\n"+
		"set title '%s'\n"+
		"set style data histogram\n"+
		"set bars fullwidth\n"+
		"set style histogram errorbars gap 1\n"+
		"set style fill solid border -1\n"+
		"set offsets graph 0, 0, 0.05, 0.05\n"+
		"%s\n"+
		"set xlabel '%s'\n"+
		"set xtics rotate by -45\n"+
		"set ylabel '%s'\n"+
		"set yrange [0<*:]\n"+
		"set term %s\n"+
		"set output '%s.%s'\n"+
		"plot for [DS=2:%d:2] '%s' using DS:DS+1:xticlabels(1)";
	private static final String LEGEND_ON = 
		"set key autotitle columnhead\n"+
		"set key outside";
	private static final String LEGEND_OFF =
		"set key off";
	private static final String EXTRA_DATA_FILE_LINE =
		", \\\n	 for [DS=2:%d:2] '%s' using "+
		"DS:DS+1:xticlabels(1)";
	private static final String GNUPLOT_COMMENT = "#";
	private static final String DATASET_FILE_FORMAT = "%s.dat";
	private static final String SCRIPT_FILE_FORMAT = "%s.gnuplot";
	//Default constants
	private static final String DEFAULT_GRAPH_TITLE = "";
	private static final String DEFAULT_X_LABEL = "";
	private static final String DEFAULT_Y_LABEL = "";
	private static final String DEFAULT_FORMAT = "png";
	private static final String DEFAULT_OUT_FILENAME = "graph";
	private static final String DEFAULT_DIR = "";
	//Parser keywords
	private static final String X_AXIS = "x";
	private static final String X_LABEL = "xlabel";
	private static final String Y_AXIS = "y";
	private static final String Y_LABEL = "ylabel";
	private static final String SERIES = "serie";
	private static final String SERIE_NAME = "seriename";
	private static final String TITLE = "title";
	private static final String OUTPUT_FILE = "outfile";
	private static final String OUTPUT_DIR = "outdir";
	private static final String EXTRA_INPUT_DIR = "extradir";
	private static final String EXTRA_DATA = "extra";
	private static final String KEYWORD_VALUE_SPLIT = ":";
	private static final String VALUES_SPLIT = ",";
	private static final String VALUE_UNIT_SPLIT = "#";
	private static final String UNIT_SPLIT = "/";
	private static final char STRING_DELIMITER = '"';
	private static final char STRING_DELIMITER_ESCAPE = '\\';

	//Labels
	private static final String OPS_X_LABEL = "Execution profile";
	private static final String THREADS_X_LABEL = "Number of threads";
	private static final String UNDEFINED_LABEL = "";
	private static final String LABELS_LABEL = "v";

	private enum GraphOption {
		OPS("ops", new Stat[]{Stat.TOTAL_OPERATIONS}),
		THREADS("threads", new Stat[]{Stat.TOTAL_THREADS}),

		OPS_PER_TIME("ops/time",new Stat[]{Stat.TOTAL_OPERATIONS, Stat.TOTAL_TIME}),
		//bandwidth
		UPLOAD("upload", new Stat[]{Stat.UPLOAD}),
		SEARCH("search",new Stat[]{Stat.SEARCH}),
		DOWNLOAD("download", new Stat[]{Stat.DOWNLOAD}),
		UPLOAD_PER_TIME("upload/time", new Stat[]{Stat.UPLOAD, Stat.TOTAL_TIME}),
		SEARCH_PER_TIME("search/time", new Stat[]{Stat.SEARCH, Stat.TOTAL_TIME}),
		DOWNLOAD_PER_TIME("download/time", new Stat[]{Stat.DOWNLOAD, Stat.TOTAL_TIME}),
		//times
		TIME("time", new Stat[]{Stat.TOTAL_TIME}),
		//client times
		CLIENT_INDEX("client_index_time", new Stat[]{Stat.CLIENT_INDEX}),
		FEATURE_EXTRACTION("feature_extraction", new Stat[]{Stat.FEATURE_EXTRACTION}),
		ENCRYPTION_TOTAL("encryption_time", new Stat[]{Stat.ENCRYPTION_SYMMETRIC}),
		ENCRYPTION_SYMMETRIC("symmetric_time", new Stat[]{Stat.ENCRYPTION_SYMMETRIC}),
		ENCRYPTION_CBIR("cbir_time", new Stat[]{Stat.ENCRYPTION_CBIR}),
		ENCRYPTION_MISC("misc_time", new Stat[]{Stat.ENCRYPTION_MISC}),
		CLIENT_NETWORK_TIME("client_network_time", new Stat[]{Stat.CLIENT_NETWORK_TIME}),
		//server times
		SERVER_INDEX("server_index_time", new Stat[]{Stat.SERVER_INDEX}),
		TRAIN_TIME("train_time", new Stat[]{Stat.TRAIN_TIME}),
		SEARCH_TIME("search_time", new Stat[]{Stat.SEARCH_TIME}),
		SERVER_NETWORK_TIME("server_network_time", new Stat[]{Stat.SERVER_NETWORK_TIME}),
		NETWORK_FEATURE_TIME("network_feature_time", new Stat[]{Stat.NETWORK_FEATURE_TIME}),
		NETWORK_INDEX_TIME("network_index_time", new Stat[]{Stat.NETWORK_INDEX_TIME}),
		NETWORK_ADD_TIME("network_add_time", new Stat[]{Stat.NETWORK_ADD_TIME}),
		NETWORK_GET_TIME("network_get_time", new Stat[]{Stat.NETWORK_GET_TIME}),
		NETWORK_PARALLEL_ADD("network_parallel_add", new Stat[]{Stat.NETWORK_PARALLEL_ADD}),
		NETWORK_PARALLEL_GET("network_parallel_get", new Stat[]{Stat.NETWORK_PARALLEL_GET}),
		NETWORK_UPLOAD_TIME("network_upload_time", new Stat[]{Stat.NETWORK_UPLOAD_TIME}),
		NETWORK_DOWNLOAD_TIME("network_download_time", new Stat[]{Stat.NETWORK_DOWNLOAD_TIME}),
		NETWORK_PARALLEL_UPLOAD("network_parallel_upload",
			new Stat[]{Stat.NETWORK_PARALLEL_UPLOAD}),
		NETWORK_PARALLEL_DOWNLOAD("network_parallel_download",
			new Stat[]{Stat.NETWORK_PARALLEL_DOWNLOAD}),
		//search
		HIT_RATIO("precision", new Stat[]{Stat.HIT_RATIO}),
		SCORE("score", new Stat[]{Stat.AVERAGE_SCORE});


		private String aUserString;
		private Stat[] aStat;
		private Unit[] aUnit;

		GraphOption(String userString, Stat[] stat) {
			aUserString = userString;
			aStat = stat;
			aUnit = new Unit[aStat.length];
			for(int i = 0; i < aStat.length; i++)
				aUnit[i] = aStat[i].getDefaultUnit();
		}

		protected String getUserString() {
			return aUserString;
		}

		protected double getStatValue(DataSerie ds) {
			return ds.getStat(aStat, aUnit);
		}

		protected double getStandardDeviation(DataSerie ds) {
			return ds.getStandardDeviation(aStat, aUnit);
		}

		protected String getXLabel() {
			if(this == THREADS)
				return THREADS_X_LABEL;
			else
				return OPS_X_LABEL;
		}

		protected static GraphOption getGraphOption(String option) {
			GraphOption op = null;
			int i = 0;
			GraphOption[] values = GraphOption.values();
			while(null == op && i < values.length){
				if(option.equalsIgnoreCase(values[i].aUserString))
					op = values[i];
				else
					i++;
			}
			return op;
		}

		protected String getUnits() {
			String ret = null;
			if(2 == aUnit.length){
				if(null != aUnit[0])
					ret = aUnit[0].getId()+"/"+aUnit[1].getId();
				else
					ret = aStat[0].getKey()+"/"+aUnit[1].getId();
			}
			else if(null != aUnit[0]){
				ret = ""+aUnit[0].getId();
			}
			return ret;
		}

		protected String getStatsKey() {
			return 2 == aStat.length ? aStat[0].getKey()+"/"+aStat[1].getKey() :
				""+aStat[0].getKey();
		}

		protected boolean setUnits(String units) {
			if(null == units)
				return true;
			String[] unitsSplit = units.split(UNIT_SPLIT);
			if(unitsSplit.length != aStat.length)
				return false; 
			int i = 0;
			Unit[] tmp = new Unit[aUnit.length];
			for(String unit: unitsSplit){
				if(1 < unit.length())
					return false;
				Unit u = Unit.getUnit(unit.charAt(0));
				if(null == u)
					return false;
				tmp[i++] = u;
			}
			aUnit = tmp;
			return true;
		}

		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append(aStat[0].getKey());
			if(2 == aStat.length){
				if(null == aUnit[0]){
					buffer.append("/"+aUnit[1].toString());
				}
			}
			if(null != aUnit[0]){
				buffer.append(" (");
				if(2 == aUnit.length){
					buffer.append(aUnit[0].toString()+"/"+aUnit[1].toString());
				}
				else
					buffer.append(aUnit[0]);
				buffer.append(")");
			}
			return buffer.toString();
		}
	}

	private String aGraphOps;
	private String aFormat;
	private String aInputName;
	private String aScriptName;
	private StringBuffer aFileName;
	private StringBuffer aSerieName;
	private StringBuffer aTitle;
	private StringBuffer aXLabel;
	private StringBuffer aYLabel;
	private File aOutputDir;
	private File aExtraInputDir;
	private boolean aCreateLines;
	private List<DataSerie> aDataSeries;
	private List<String> aExtras;
	private GraphOption aYStat;
	private GraphOption[] aXStat;
	private GraphOption aSeries;

	public GNUPlot(String graphOps, List<DataSerie> dataSeries) {
		aGraphOps = graphOps;
		aFormat = DEFAULT_FORMAT;
		long time = System.currentTimeMillis();
		aInputName = String.format(DATASET_FILE_FORMAT, ""+time);
		aScriptName = String.format(SCRIPT_FILE_FORMAT, ""+time);
		aFileName = new StringBuffer();
		aSerieName = new StringBuffer();
		aTitle = new StringBuffer();
		aXLabel = new StringBuffer();
		aYLabel = new StringBuffer();
		aOutputDir = new File(DEFAULT_DIR);
		aExtraInputDir = new File(DEFAULT_DIR);
		aCreateLines = false;
		aDataSeries = dataSeries;
		aExtras = new LinkedList<String>();
		aYStat = null;
		aXStat = null;
		aSeries = null;
	}

	public void execute() throws IOException, ScriptErrorException {
		parseOptions();
		String units = aYStat.getUnits();
		for(GraphOption op: aXStat){
			op.setUnits(units);
		}
		SortedMap<String,SortedSet<DataSerie>> data = createDataTable();
		String error = writeScript(data.get(LABELS_LABEL).size()*2);
		writeDataset(data, error);
		if(null == error)
			createGraph();
	}

	private SortedMap<String, SortedSet<DataSerie>> createDataTable() throws ScriptErrorException {
		SortedMap<String,SortedSet<DataSerie>> data = new TreeMap<String, SortedSet<DataSerie>>(
			new Comparator<String>(){
			//this will compare serie labels to make up the lines
			public int compare(String o1, String o2) {
				if(null == o1 && null == o2)
					return 0;
				else if(null == o1)
					return 1;
				else if(null == o2)
					return -1;
				boolean o1S = o1.equalsIgnoreCase(LABELS_LABEL);
				boolean o2S = o2.equalsIgnoreCase(LABELS_LABEL);
				if(o1S && o2S)
					return 0;
				else if(o1S)
					return -1;
				else if(o2S)
					return 1;
				else
					return o1.compareTo(o2);
			}
		});
		//create all lines if they are already known
		for(int i = 0; i < aXStat.length; i++){
			//X axis will have the number of threads or operations profile, create lines later
			if(aXStat[i] == GraphOption.THREADS || aXStat[i] == GraphOption.OPS){
				if(aXStat.length ==  1){
					aCreateLines = true;
					break;
				}
				else{
					throw new ScriptErrorException(INVALID_EXTRA_X_AXIS);
				}
			}
			data.put(aXStat[i].getUserString(), createNewLine());
		}
		for(DataSerie ds: aDataSeries){
			//get first line and add serie label if required
			SortedSet<DataSerie> seriesLabels = data.get(LABELS_LABEL);
			if(null == seriesLabels){
				seriesLabels = createNewLine();
				data.put(LABELS_LABEL, seriesLabels);
			}
			seriesLabels.add(ds);
			if(aCreateLines){
				//get correct line
				String key = UNDEFINED_LABEL;
				if(aXStat[0] == GraphOption.THREADS){
					key = String.format("%.0f",ds.getStat(new Stat[]{Stat.TOTAL_THREADS}));
				}
				else if(aXStat[0] == GraphOption.OPS){
					key = ds.getTitle();
				}
				SortedSet<DataSerie> serie = data.get(key);
				if(null == serie){
					serie = createNewLine();
					data.put(key, serie);
				}
				serie.add(ds);
			}
			else{
				//iterate over all lines and put the dataserie in it
				for(GraphOption x: aXStat){
					data.get(x.getUserString()).add(ds);
				}
			}
		}
		return data;
	}

	private SortedSet<DataSerie> createNewLine() {
		return new TreeSet<DataSerie>(new Comparator<DataSerie>(){
			public int compare(DataSerie o1, DataSerie o2) {
				if(null == o1 && null == o2)
					return 0;
				else if(null == o1)
					return 1;
				else if(null == o2)
					return -1;
				//compare xtics
				String series1 = UNDEFINED_LABEL;
				String series2 = UNDEFINED_LABEL;
				if(aSeries == GraphOption.THREADS){
					series1 = ""+o1.getStat(new Stat[]{Stat.TOTAL_THREADS});
					series2 = ""+o2.getStat(new Stat[]{Stat.TOTAL_THREADS});
				}
				else if(aSeries == GraphOption.OPS){
					series1 = o1.getTitle();
					series2 = o2.getTitle();
				}
				return series1.compareTo(series2);
			}
		});
	}

	private void parseOptions() throws ScriptErrorException {
		String[] rawOptions = aGraphOps.split(TestSetGenerator.POST_PROCESS_SEPARATOR);
		StringBuffer stringBuffer = new StringBuffer();
		boolean string = false;
		for(String option: rawOptions){
			if(string){
				int r = processString(option, stringBuffer);
				if(0 == r)
					string = false;
				else if(-1 == r)
					throw new ScriptErrorException(MISSING_CLOSING_MARK);
				continue;
			}
			String[] opValues = option.split(KEYWORD_VALUE_SPLIT);
			if(1 == opValues.length)
				throw new ScriptErrorException(String.format(TestSetGenerator.ARG_ERROR, option));
			if(opValues[0].equalsIgnoreCase(TITLE)){
				if(opValues[1].startsWith(""+STRING_DELIMITER)){
					string = true;
					stringBuffer = aTitle;
					stringBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aTitle.append(opValues[1]);
				}
			}
			else if(opValues[0].equalsIgnoreCase(SERIE_NAME)){
				if(opValues[1].startsWith(""+STRING_DELIMITER)){
					string = true;
					stringBuffer = aSerieName;
					stringBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aSerieName.append(opValues[1]);
				}
			}
			else if(opValues[0].equalsIgnoreCase(OUTPUT_FILE)){
				if(opValues[1].startsWith(""+STRING_DELIMITER)){
					string = true;
					stringBuffer = aFileName;
					stringBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aFileName.append(opValues[1]);
				}
			}
			else if(opValues[0].equalsIgnoreCase(X_LABEL)){
				if(opValues[1].startsWith(""+STRING_DELIMITER)){
					string = true;
					stringBuffer = aXLabel;
					stringBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aXLabel.append(opValues[1]);
				}
			}
			else if(opValues[0].equalsIgnoreCase(Y_LABEL)){
				if(opValues[1].startsWith(""+STRING_DELIMITER)){
					string = true;
					stringBuffer = aYLabel;
					stringBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aYLabel.append(opValues[1]);
				}
			}
			else if(opValues[0].equalsIgnoreCase(OUTPUT_DIR)){
				File outdir = new File(opValues[1]);
				if((!outdir.exists() && !outdir.mkdirs())||
					!outdir.isDirectory())
					throw new ScriptErrorException(String.format(OUTPUT_DIR_ERROR, opValues[1]));
				aOutputDir = outdir;
			}
			else if(opValues[0].equalsIgnoreCase(EXTRA_INPUT_DIR)){
				File extraDir = new File(opValues[1]);
				if(!extraDir.isDirectory())
					throw new ScriptErrorException(String.format(OUTPUT_DIR_ERROR, opValues[1]));
				aExtraInputDir = extraDir;
			}
			else if(opValues[0].equalsIgnoreCase(EXTRA_DATA)){
				aExtras.add(opValues[1]);
			}
			else if(opValues[0].equalsIgnoreCase(X_AXIS)){
				String[] xAxisOptions = opValues[1].split(VALUES_SPLIT);
				aXStat = new GraphOption[xAxisOptions.length];
				for(int i = 0; i < xAxisOptions.length; i++){
					GraphOption xOp = GraphOption.getGraphOption(xAxisOptions[i]);
					if(null == xOp){
						throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
							option));
					}
					aXStat[i] = xOp;
				}
			}
			else if(opValues[0].equalsIgnoreCase(SERIES)){
				aSeries = GraphOption.getGraphOption(opValues[1]);
				if(null == aSeries){
					throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
						option));
				}
			}
			else if(opValues[0].equalsIgnoreCase(Y_AXIS)){
				String[] yAxisOptions = opValues[1].split(VALUE_UNIT_SPLIT);
				if(2 < yAxisOptions.length)
					throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
						option));
				GraphOption yOp = GraphOption.getGraphOption(yAxisOptions[0]);
				if(null == yOp)
					throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
						option));
				if(2 == yAxisOptions.length){
					if(!yOp.setUnits(yAxisOptions[1]))
						throw new ScriptErrorException(String.format(
							TestSetGenerator.INVALID_ARGUMENT, option));
				}
				aYStat = yOp;
				aYLabel.append(yOp.toString());
			}
		}
		if(string){
			throw new ScriptErrorException(MISSING_CLOSING_MARK);
		}
	}

	private void writeDataset(SortedMap<String,SortedSet<DataSerie>> data, String error)
		throws IOException {
		StringBuffer buffer = new StringBuffer();
		if(null != error){
			buffer.append(GNUPLOT_COMMENT+" "+error+"\n");
		}
		boolean writeHeader = 2 < data.get(LABELS_LABEL).size()+1 || !aExtras.isEmpty() ? 
			true : false;
		for(String xTic: data.keySet()){
			GraphOption keyStat = null;
			boolean writen = false;
			if(xTic.equalsIgnoreCase(LABELS_LABEL)){
				if(writeHeader){
					writen = true;
					buffer.append("\""+LABELS_LABEL+"\"");
				}
			}
			else if(aCreateLines){
				writen = true;
				keyStat = aYStat;
				buffer.append("\""+xTic+"\"");
			}
			else{
				writen = true;
				keyStat = GraphOption.getGraphOption(xTic);
				buffer.append("\""+keyStat.getStatsKey()+"\"");
			}
			SortedSet<DataSerie> dataPoints = data.get(xTic);
			for(DataSerie ds: dataPoints){
				if(writen)
					buffer.append("\t");
				writen = false;
				String value = null;
				if(xTic.equalsIgnoreCase(LABELS_LABEL)){
					if(writeHeader){
						buffer.append("\t");
						if(0 < aSerieName.length()){
							value = aSerieName.toString();
						}
						else if(aSeries == GraphOption.THREADS){
							value = String.format("%.0f", ds.getStat(
								new Stat[]{Stat.TOTAL_THREADS}));
						}
						else if(aSeries == GraphOption.OPS){
							value = "\""+ds.getTitle()+"\"";
						}
						else{
							value = "\""+ds.getTitle()+"\"";
						}
					}
				}
				else{
					value = ""+keyStat.getStatValue(ds)+"\t"+keyStat.getStandardDeviation(ds);
				}
				if(null != value){
					writen = true;
					buffer.append(value);
				}
			}
			if(writen)
				buffer.append("\n");
		}
		PrintWriter writer = new PrintWriter(new File(aOutputDir, aInputName));
		writer.print(buffer.toString());
		writer.close();
	}

	private String writeScript(int nColumns) throws IOException {
		aScriptName = String.format(SCRIPT_FILE_FORMAT, aFileName);
		aInputName = String.format(DATASET_FILE_FORMAT, aFileName);
		String outputFile = new File(aOutputDir, aFileName.toString()).toString();
		String inputData = new File(aOutputDir, aInputName).toString();
		StringBuffer buffer = new StringBuffer();
		String legend = 2 < nColumns || !aExtras.isEmpty() ? LEGEND_ON : LEGEND_OFF;
		//add default x axis label if not specified
		if(0 == aXLabel.length() && 1 == aXStat.length){
			if(aXStat[0] == GraphOption.THREADS)
				aXLabel.append(THREADS_X_LABEL);
			else if(aXStat[0] == GraphOption.OPS)
				aXLabel.append(OPS_X_LABEL);
		}
		buffer.append(String.format(BASE_SCRIPT, GNUPLOT_COMMENT, aTitle, legend, aXLabel,
			aYLabel, aFormat, outputFile, aFormat, nColumns, inputData));
		for(String fileName: aExtras){
			File extraInput = new File(aExtraInputDir, fileName);
			try{
				BufferedReader reader = new BufferedReader(new FileReader(extraInput));
				String headers = GNUPLOT_COMMENT;
				while((headers = reader.readLine()).startsWith(GNUPLOT_COMMENT));
				int columns = headers.split("\t").length - 1;
				buffer.append(String.format(EXTRA_DATA_FILE_LINE, columns, extraInput.toString()));
			}
			catch(IOException e){
				return String.format(EXTRA_INPUT_ERROR, extraInput.toString());
			}
		}
		PrintWriter writer = new PrintWriter(new File(aOutputDir, aScriptName));
		writer.print(buffer.toString());
		writer.close();
		return null;
	}

	private void createGraph() throws IOException {
		ProcessBuilder builder = new ProcessBuilder("gnuplot",
			new File(aOutputDir, aScriptName).toString());
		builder.redirectError(ProcessBuilder.Redirect.INHERIT);
		builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		Process pr = builder.start();
		try{
			pr.waitFor();
		}
		catch(InterruptedException e){
			e.printStackTrace();
		}
	}

	private int processString(String word, StringBuffer buffer) {
		int index = word.indexOf(STRING_DELIMITER);
		if(-1 == index){
			buffer.append(" "+word);
			return 1;
		}
		else{
			int n = 0;
			int i = index-1;
			while(0 <= i && STRING_DELIMITER_ESCAPE == word.charAt(i--))
				n++;
			if(0 == n%2){
				//delimiter is not escaped
				if(word.length()-1 == index){
					buffer.append(" "+word.substring(0, word.length()-1));
					return 0;
				}
				else
					return -1;
			}
			else{
				//ignore delimiter
				buffer.append(" "+word);
				return 1;
			}
		}
	}
}