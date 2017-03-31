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
import mie.utils.DataSerie.Unit;
import mie.utils.DataSerie.Stat;

public class GNUPlot {

	//Errors
	private static final String MISSING_CLOSING_MARK = "Missing closing quotation marks on title";
	private static final String OUTPUT_DIR_ERROR = "Couldn't find or create directory %s";
	private static final String EXTRA_INPUT_ERROR = "Problem reading extra input file %s";
	//GNUPlot related constants
	private static final String BASE_SCRIPT =
		"set datafile commentschars '%s'\n"+
		"set title '%s'\n"+
		"set style data histogram\n"+
		"set style histogram cluster gap 1\n"+
		"set style fill solid border -1\n"+
		"set offsets graph 0, 0, 0.05, 0.05\n"+
		"%s\n"+
		"set xlabel '%s'\n"+
		"set ylabel '%s'\n"+
		"set term %s\n"+
		"set output '%s.%s'\n"+
		"plot for [DS=2:%d:1] '%s' using DS:xticlabels(1)";
	private static final String LEGEND_ON = 
		"set key autotitle columnhead\n"+
		"set key outside";
	private static final String LEGEND_OFF =
		"set key off";
	private static final String EXTRA_DATA_FILE_LINE =
		", \\\n	 for [DS=2:%d:1] '%s' using "+
		"DS:xticlabels(1)";
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
	private static final String Y_AXIS = "y";
	private static final String SERIES = "serie";
	private static final String SERIE_NAME = "seriename";
	private static final String TITLE = "title";
	private static final String OUTPUT_FILE = "outfile";
	private static final String OUTPUT_DIR = "outdir";
	private static final String EXTRA_INPUT_DIR = "extradir";
	private static final String EXTRA_DATA = "extra";
	private static final String KEYWORD_VALUE_SPLIT = ":";
	private static final String VALUE_SPLIT = "#";
	private static final String UNIT_SPLIT = "/";
	private static final String TITLE_DELIMITER = "\"";

	//Labels
	private static final String OPS_X_LABEL = "Execution profile";
	private static final String THREADS_X_LABEL = "Number of threads";
	private static final String UNDEFINED_LABEL = "";
	private static final String LABELS_LABEL = "v";

	private enum GraphOption {
		OPS(true, "ops", new Stat[]{Stat.TOTAL_OPERATIONS}),
		THREADS(true, "threads", new Stat[]{Stat.TOTAL_THREADS}),
		//Valid Y axis
		TIME(false,"time",new Stat[]{Stat.TOTAL_TIME}),
		OPS_PER_TIME(false, "ops/time",new Stat[]{Stat.TOTAL_OPERATIONS, Stat.TOTAL_TIME}),
		UPLOAD(false, "upload", new Stat[]{Stat.UPLOAD}),
		SEARCH(false, "search",new Stat[]{Stat.SEARCH}),
		DOWNLOAD(false, "download", new Stat[]{Stat.DOWNLOAD}),
		UPLOAD_PER_TIME(false, "upload/time", new Stat[]{Stat.UPLOAD, Stat.TOTAL_TIME}),
		SEARCH_PER_TIME(false, "search/time", new Stat[]{Stat.SEARCH, Stat.TOTAL_TIME}),
		DOWNLOAD_PER_TIME(false, "download/time", new Stat[]{Stat.DOWNLOAD, Stat.TOTAL_TIME}),
		HIT_RATIO(false, "precision", new Stat[]{Stat.HIT_RATIO}),
		SCORE(false, "score", new Stat[]{Stat.AVERAGE_SCORE});

		private boolean aIsValidX;
		private String aUserString;
		private Stat[] aStat;
		private Unit[] aUnit;

		GraphOption(boolean isValidX, String userString, Stat[] stat) {
			aIsValidX = isValidX;
			aUserString = userString;
			aStat = stat;
			aUnit = new Unit[aStat.length];
			for(int i = 0; i < aStat.length; i++)
				aUnit[i] = aStat[i].getDefaultUnit();
		}

		protected double getStatValue(DataSerie ds) {
			double[] values = new double[aStat.length];
			for(int i = 0; i < values.length; i++){
				if(null == aUnit[i])
					values[i] = ds.getStat(aStat[i]);
				else
					values[i] = ds.getStat(aStat[i], aUnit[i]);
			}
			if(1 == values.length)
				return values[0];
			else
				return values[0]/values[1];
		}

		protected String getXLabel() {
			if(this == OPS)
				return OPS_X_LABEL;
			else if(this == THREADS)
				return THREADS_X_LABEL;
			else
				return null;
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

		protected boolean setUnits(String units) {
			String[] unitsSplit = units.split(UNIT_SPLIT);
			if(unitsSplit.length != aStat.length)
				return false; 
			int i = 0;
			for(String unit: unitsSplit){
				if(1 < unit.length())
					return false;
				Unit u = Unit.getUnit(unit.charAt(0));
				if(null == u)
					return false;
				aUnit[i++] = u;
			}
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
	private String aTitle;
	private String aXLabel;
	private String aYLabel;
	private String aFormat;
	private String aFileName;
	private String aInputName;
	private String aScriptName;
	private String aSerieName;
	private File aOutputDir;
	private File aExtraInputDir;
	private boolean aOpsTime;
	private List<DataSerie> aDataSeries;
	private List<String> aExtras;
	private GraphOption aYStat;
	private GraphOption aSeries;

	public GNUPlot(String graphOps, List<DataSerie> dataSeries) {
		aGraphOps = graphOps;
		aTitle = DEFAULT_GRAPH_TITLE;
		aXLabel = DEFAULT_X_LABEL;
		aYLabel = DEFAULT_Y_LABEL;
		aFormat = DEFAULT_FORMAT;
		aFileName = DEFAULT_OUT_FILENAME;
		long time = System.currentTimeMillis();
		aInputName = String.format(DATASET_FILE_FORMAT, ""+time);
		aScriptName = String.format(SCRIPT_FILE_FORMAT, ""+time);
		aSerieName = null;
		aOutputDir = new File(DEFAULT_DIR);
		aExtraInputDir = new File(DEFAULT_DIR);
		aOpsTime = false;
		aDataSeries = dataSeries;
		aExtras = new LinkedList<String>();
		aYStat = null;
		aSeries = null;
	}

	public void execute() throws IOException, ScriptErrorException {
		parseOptions();
		SortedMap<String,SortedSet<DataSerie>> data = createDataTable();
		String error = writeScript(data.get(LABELS_LABEL).size()+1);
		writeDataset(data, error);
		if(null == error)
			createGraph();
	}

	private SortedMap<String, SortedSet<DataSerie>> createDataTable() {
		SortedMap<String,SortedSet<DataSerie>> data = new TreeMap<String, SortedSet<DataSerie>>(
			new Comparator<String>(){
			//this will compare serie labels to make up the columns
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
		//iterate over all dataseries, make up columns as needed
		for(DataSerie ds: aDataSeries){
			//get first line and add serie label if required
			SortedSet<DataSerie> seriesLabels = data.get(LABELS_LABEL);
			if(null == seriesLabels){
				seriesLabels = createNewLine();
				data.put(LABELS_LABEL, seriesLabels);
			}
			seriesLabels.add(ds);
			//get correct line and data point to it
			String key = UNDEFINED_LABEL;
			if(aXLabel.equalsIgnoreCase(THREADS_X_LABEL)){
				key = String.format("%.0f",ds.getStat(Stat.TOTAL_THREADS));
			}
			else if(aXLabel.equalsIgnoreCase(OPS_X_LABEL)){
				key = ds.getTitle();
			}
			SortedSet<DataSerie> serie = data.get(key);
			if(null == serie){
				serie = createNewLine();
				data.put(key, serie);
			}
			serie.add(ds);
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
					series1 = ""+o1.getStat(Stat.TOTAL_THREADS);
					series2 = ""+o2.getStat(Stat.TOTAL_THREADS);
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
		StringBuffer titleBuffer = new StringBuffer();
		boolean title = false;
		for(String option: rawOptions){
			if(title){
				if(!processTitle(option, titleBuffer)){
					title = false;
					aTitle = titleBuffer.toString();
				}
				continue;
			}
			String[] opValues = option.split(KEYWORD_VALUE_SPLIT);
			if(1 == opValues.length)
				throw new ScriptErrorException(String.format(TestSetGenerator.ARG_ERROR, option));
			if(opValues[0].equalsIgnoreCase(TITLE)){
				if(opValues[1].startsWith(TITLE_DELIMITER)){
					title = true;
					titleBuffer.append(opValues[1].substring(1, opValues[1].length()));
				}
				else{
					aTitle = opValues[1];
				}
			}
			else if(opValues[0].equalsIgnoreCase(SERIE_NAME)){
				aSerieName = opValues[1];
			}
			else if(opValues[0].equalsIgnoreCase(OUTPUT_FILE)){
				aFileName = opValues[1];
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
				GraphOption xOp = GraphOption.getGraphOption(opValues[1]);
				if(null == xOp){
					throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
						option));
				}
				aXLabel = xOp.getXLabel();
				if(null == aXLabel){
					throw new ScriptErrorException(String.format(TestSetGenerator.INVALID_ARGUMENT,
						option));
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
				String[] yAxisOptions = opValues[1].split(VALUE_SPLIT);
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
				aYLabel = yOp.toString();
			}
		}
		if(title){
			throw new ScriptErrorException(MISSING_CLOSING_MARK);
		}
	}

	private boolean processTitle(String word) {
		return false;
	}

	private void writeDataset(SortedMap<String,SortedSet<DataSerie>> data, String error)
		throws IOException {
		StringBuffer buffer = new StringBuffer();
		if(null != error){
			buffer.append(GNUPLOT_COMMENT+" "+error+"\n");
		}
		for(String xTic: data.keySet()){
			buffer.append(xTic);
			SortedSet<DataSerie> dataPoints = data.get(xTic);
			for(DataSerie ds: dataPoints){
				buffer.append("\t");
				String value = null;
				if(xTic.equalsIgnoreCase(LABELS_LABEL)){
					if(null != aSerieName){
						value = aSerieName;
					}
					else if(aSeries == GraphOption.THREADS){
						value = String.format("%.0f", ds.getStat(Stat.TOTAL_THREADS));
					}
					else if(aSeries == GraphOption.OPS){
						value = ds.getTitle();
					}
					else{
						value = ds.getTitle();	
					}
				}
				else{
					value = ""+aYStat.getStatValue(ds);
				}
				buffer.append(value);
			}
			buffer.append("\n");
		}
		PrintWriter writer = new PrintWriter(new File(aOutputDir, aInputName));
		writer.print(buffer.toString());
		writer.close();
	}

	private String writeScript(int nColumns) throws IOException {
		aScriptName = String.format(SCRIPT_FILE_FORMAT, aFileName);
		aInputName = String.format(DATASET_FILE_FORMAT, aFileName);
		String outputFile = new File(aOutputDir, aFileName).toString();
		String inputData = new File(aOutputDir, aInputName).toString();
		StringBuffer buffer = new StringBuffer();
		String legend = 2 < nColumns || !aExtras.isEmpty() ? LEGEND_ON : LEGEND_OFF;
		buffer.append(String.format(BASE_SCRIPT, GNUPLOT_COMMENT, aTitle, legend, aXLabel,
			aYLabel, aFormat, outputFile, aFormat, nColumns, inputData));
		for(String fileName: aExtras){
			File extraInput = new File(aExtraInputDir, fileName);
			try{
				BufferedReader reader = new BufferedReader(new FileReader(extraInput));
				String headers = GNUPLOT_COMMENT;
				while((headers = reader.readLine()).startsWith(GNUPLOT_COMMENT));
				int columns = headers.split("\t").length;
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

	private boolean processTitle(String word, StringBuffer buffer) {
		if(!word.endsWith(TITLE_DELIMITER)){
			buffer.append(" "+word);
			return true;
		}
		else{
			buffer.append(" "+word.substring(0, word.length()-1));
			return false;
		}
	}
}