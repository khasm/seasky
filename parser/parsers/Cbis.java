import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Array;
import javafx.util.Pair;

public class Cbis implements DatasetParser {

	private static final String PREFIX_NAME = "Calc-Training_";
	private static final String DICOM = ".dcm";
	private static final String ANNOTATIONS = "calc_case_description_train_set.csv";
	private static final int ID = 0;
	private static final int SIDE = 2;
	private static final int VIEW = 3;

	private String name;
	private File dataset;
	private int current;
	private int max;
	private List<Pair<String,List<File>>> imgFiles;
	private String[] categories;
	private Map<String,String> annotations;

	public Cbis() {
		name = "Curated Breast Imaging Subset of the Digital Database for Screening Mammography:"+
			" https://wiki.cancerimagingarchive.net/display/Public/CBIS-DDSM";
		dataset = new File("");
		max = -1;
		current = 0;
		imgFiles = new ArrayList<Pair<String,List<File>>>();
		categories = new String[0];
		annotations = new HashMap<String,String>();
	}

	public String getDescription() {
		return name;
	}

	public void setDatasetDir(File dir) {
		dataset = dir;
	}

	public boolean hasNext() {
		if(-1 == max){
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
		String name = imgFiles.get(current).getKey();
		List<File> imgTmp = imgFiles.get(current++).getValue();
		List<byte[]> imgList = new LinkedList<byte[]>();
		try{
			for(File img: imgTmp){
				FileInputStream in = new FileInputStream(img);
				byte[] imgBytes = new byte[in.available()];
				in.read(imgBytes);
				in.close();
				imgList.add(imgBytes);
			}
		}
		catch (IOException e){
			e.printStackTrace();
		}
		List<String> txtList = new LinkedList<String>();
		txtList.add(annotations.get(name));
		Pair<List<byte[]>,List<String>> contents = new Pair<List<byte[]>,List<String>>(imgList, txtList);
		return new Pair<String,Pair<List<byte[]>,List<String>>>(name, contents);
	}

	private void initialize() throws IOException {
		File dir = new File(dataset, "DOI");
		File[] docs = dir.listFiles();
		max = docs.length;
		for(File doc: docs){
			String docName = doc.getName().substring(PREFIX_NAME.length());
			List<File> imgs = getImgs(doc);
			Pair<String,List<File>> docImgs = new Pair<String,List<File>>(docName, imgs);
			imgFiles.add(docImgs);
		}
		BufferedReader reader = new BufferedReader(new FileReader(new File(dataset, ANNOTATIONS)));
		String input;
		while((input = reader.readLine()) != null){
			if(input.startsWith("P_")){
				int count = 0;
				int index = 0;
				String docName = "";
				String description = "";
				while(-1 != index){
					int index2 = input.indexOf(",", index);
					String value = "";
					if(-1 != index2){
						value = input.substring(index, index2);
						index = index2 + 1;
					}
					else{
						value = input.substring(index);
						index = index2;
					}
					if(ID == count || SIDE == count)
						docName += value + "_";
					else if(VIEW == count)
						docName += value;
					if(categories.length == count-1){
						description += categories[count++] +": "+value;
					}
					else{
						description += categories[count++] +": "+value+"; ";
					}
				}
				annotations.put(docName, description);
			}
			else{
				List<String> tmp = new LinkedList<String>();
				int index = 0;
				while(-1 != index){
					int index2 = input.indexOf(",", index);
					String cat = "";
					if(-1 != index2){
						cat = input.substring(index, index2);
						index = index2 + 1;
					}
					else{
						cat = input.substring(index);
						index = index2;
					}
					tmp.add(cat);
				}
				categories = tmp.toArray(categories);
			}
		}
	}

	private List<File> getImgs(File doc) throws IOException {
		List<File> imgs = new LinkedList<File>();
		if(doc.isDirectory()){
			File[] contents = doc.listFiles();
			for(File file: contents){
				imgs.addAll(getImgs(file));
			}
		}
		else if(doc.isFile() && doc.getName().endsWith(DICOM)){
			imgs.add(doc);
		}
		return imgs;
	}
}