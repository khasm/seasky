import javafx.util.Pair;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.Runtime;
import java.lang.Process;

public class Stare implements DatasetParser {

	private final static String PPM = ".ppm";
	private final static String JPG = ".jpg";
	private final static String TXT = "all-mg-codes.txt";

	private String name;
	private int current;
	private int max;
	private File datasetDir;
	private File[] imgs;
	private Map<String,String> txts;


	public Stare() {
		name = "STARE: http://cecas.clemson.edu/~ahoover/stare/";
		current = 0;
		max = -1;
		datasetDir = new File("");
		imgs = null;
		txts = new HashMap<String,String>();
	}

	public String getDescription() {
		return name;
	}

	public void setDatasetDir(File dir) {
		datasetDir = dir;
	}

	public boolean hasNext() {
		if(null == imgs){
			try{
				initialize();
			}
			catch(IOException e){
				e.printStackTrace();
				return false;
			}
		}
		return current < max ? true : false;
	}

	public Pair<String,Pair<List<byte[]>,List<String>>> getNextDoc() {
		File imgFile = imgs[current++];
		List<byte[]> imgList = new LinkedList<byte[]>();
		List<String> txtList = new LinkedList<String>();
		try{
			FileInputStream imgReader = new FileInputStream(imgFile);
			while(imgReader.available() == 0);
			byte[] img = new byte[imgReader.available()];
			imgReader.read(img);
			imgReader.close();
			imgList.add(img);
		}
		catch(IOException e){
			System.err.println("Error reading "+imgFile.toString());
			e.printStackTrace();
		}
		String imgName = imgFile.getName().substring(0, imgFile.getName().length()-PPM.length());
		String diagnosis = txts.get(imgName);
		if(null != diagnosis){
			txtList.add(diagnosis);
		}
		//System.out.println(imgName+"\t"+imgList.size()+"\t"+diagnosis);
		Pair<List<byte[]>,List<String>> contents = new Pair<List<byte[]>,List<String>>(imgList, txtList);
		return new Pair<String,Pair<List<byte[]>,List<String>>>(imgName, contents);
	}

	private void initialize() throws IOException {
		File imgDir = new File(datasetDir, "all-images");
		File imgDirNew = new File(datasetDir, "all-images");
		File[] files = imgDirNew.listFiles();
		imgs = new File[files.length];
		max = 0;
		for(File img: files){
			if(img.getName().endsWith(PPM))
				imgs[max++] = img;
		}
		BufferedReader reader = new BufferedReader(new FileReader(new File(datasetDir, TXT)));
		String input;
		while((input = reader.readLine()) != null){
			int nameEnd = input.indexOf("\t");
			String name = input.substring(0, nameEnd);
			txts.put(name, input);
		}
	}
}