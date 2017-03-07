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
import java.io.FileNotFoundException;
import java.lang.Runtime;
import java.lang.Process;

public class Ph2 implements DatasetParser {

	private static final String[] ASYMMETRY = {"Fully Symmetric", "Symmetric in 1 axes",
		"Fully Asymmetric"};
	private static final String[] COLORS = {"White", "Red", "Light Brown", "Dark Brown",
		"Blue Gray", "Black"};
	private static final String[] CLINICAL_DIAGNOSIS = {"Common Nevus", "Atypical Nevus",
		"Melanoma"};
	private static final String ABSENT = "A";
	private static final String ATYPICAL = "AT";
	private static final String PRESENT = "P";
	private static final String TYPICAL = "T";

	private static final String ABSENT_LONG = "Absent";
	private static final String ATYPICAL_LONG = "Atypical";
	private static final String PRESENT_LONG = "Present";
	private static final String TYPICAL_LONG = "Typical";

	private static final String DIAGNOSIS_TXT = "PH2_dataset.txt";
	private static final String BMP = ".bmp";
	private static final String JPG = ".jpg";

	private static final int DIAGNOSIS = 1;
	private static final int CLINICAL_DIAGNOSIS_CAT = 2;
	private static final int ASYMMETRY_CAT = 3;
	private static final int PIGMENT = 4;
	private static final int DOTS = 5;
	private static final int STREAKS = 6;
	private static final int REGRESSION = 7;
	private static final int VEIL = 8;
	private static final int COLORS_CAT = 9;

	private String name;
	private int current;
	private int max;
	private File dataset;
	private File[] documents;
	private Map<String,String> diagnosis;
	private String[] categories;

	public Ph2() {
		name = "PH2 Dataset: http://www.fc.up.pt/addi/ph2%20database.html";
		current = 0;
		max = -1;
		dataset = new File("");
		documents = null;
		diagnosis = new HashMap<String,String>();
		categories = null;
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
				return false;
			}
		}
		return current < max ? true : false;
	}

	public Pair<String,Pair<List<byte[]>,List<String>>> getNextDoc() {
		File docDir = documents[current++];
		List<byte[]> imgList = new LinkedList<byte[]>();
		List<String> txtList = new LinkedList<String>();
		File[] imgTypesList = docDir.listFiles();
		for(File dir: imgTypesList){
			File[] imgs = dir.listFiles();
			for(File img: imgs){
				if(img.getName().endsWith(BMP)){
					String imgPath = img.toString();
					try{
						/*Runtime rt = Runtime.getRuntime();
						Process pr = rt.exec("mogrify -format jpg "+imgPath);
						imgPath = imgPath.substring(0, imgPath.length()-BMP.length())+JPG;*/
						FileInputStream in = new FileInputStream(imgPath);
						//while(in.available() == 0);
						byte[] imgBytes = new byte[in.available()];
						if(imgBytes.length == 0)
							System.err.println(docDir.getName() +" failed");
						in.read(imgBytes);
						in.close();
						imgList.add(imgBytes);
					}
					catch(FileNotFoundException e){
						File f = new File(imgPath);
						if(f.exists()){
							try{
								FileInputStream in = new FileInputStream(imgPath);
								while(in.available() == 0);
								byte[] imgBytes = new byte[in.available()];
								if(imgBytes.length == 0)
									System.err.println(docDir.getName() +" failed");
								in.read(imgBytes);
								in.close();
								imgList.add(imgBytes);
							}
							catch(IOException e2){
								System.err.println("Double Error on "+imgPath);
								e2.printStackTrace();		
							}
						}
						else{
							System.err.println(f.toString()+" doesn't exist");
						}
					}
					catch(IOException e){
						System.err.println("Error on "+imgPath);
						e.printStackTrace();
					}
				}
			}
		}
		String input = diagnosis.get(docDir.getName());
		if(null != input){
			String txt = "";
			int index = 0;
			int index2 = 2;
			int count = 0;
			while((index = input.indexOf(" |", index2)) != -1){
				txt += categories[count]+": ";
				String tmp = input.substring(index2, index).trim();
				switch(count){
					case(DIAGNOSIS):
					if(tmp.equals(""))
						txt += "none";
					else
						txt += tmp;
					break;
					case(CLINICAL_DIAGNOSIS_CAT):
					txt += CLINICAL_DIAGNOSIS[Integer.parseInt(tmp)];
					break;
					case(ASYMMETRY_CAT):
					txt += ASYMMETRY[Integer.parseInt(tmp)];
					break;
					case(PIGMENT):
					case(DOTS):
					case(STREAKS):
					case(REGRESSION):
					case(VEIL):
					if(tmp.equalsIgnoreCase(ABSENT))
						txt += ABSENT_LONG;
					else if(tmp.equalsIgnoreCase(ATYPICAL))
						txt += ATYPICAL_LONG;
					else if(tmp.equalsIgnoreCase(PRESENT))
						txt += PRESENT_LONG;
					else if(tmp.equalsIgnoreCase(TYPICAL))
						txt += TYPICAL_LONG;
					break;
					case(COLORS_CAT):
					int i = 0;
					int i2 = 0;
					while((i = tmp.indexOf("  ", i2)) != -1){
						String tmp2 = tmp.substring(i2, i);
						txt += COLORS[Integer.parseInt(tmp2)-1] + ",";
						i2 = i+2;
					}
					String tmp2 = tmp.substring(i2);
					txt += COLORS[Integer.parseInt(tmp2)-1];
					break;
					default:
					txt += tmp;
				}
				txt += "; ";
				if(input.charAt(index+2) == '|') {
					index2 = index+3;
				}
				else{
					index2 = index+2;
				}
				count++;
			}
			txtList.add(txt);
		}
		Pair<List<byte[]>,List<String>> contents = new Pair<List<byte[]>,List<String>>(imgList, txtList);
		return new Pair<String,Pair<List<byte[]>,List<String>>>(docDir.getName(), contents);
	}

	private void initialize() throws IOException {
		File imgDir = new File(dataset, "PH2_Dataset_images");
		documents = imgDir.listFiles();
		max = documents.length;
		BufferedReader reader = new BufferedReader(new FileReader(new File(dataset, DIAGNOSIS_TXT)));
		String input;
		while((input = reader.readLine()) != null){
			if(input.startsWith("||") && !input.contains("Name")){
				//String diag = input.substring(12);
				String name = input.substring(3, 9);
				diagnosis.put(name, input);
			}
			else if(input.startsWith("||") && input.contains("Name")){
				int index = 0;
				int index2 = 2;
				List<String> tmpList = new LinkedList<String>();
				while((index = input.indexOf(" |", index2)) != -1){
					String tmp = input.substring(index2, index).trim();
					tmpList.add(tmp);
					if(input.charAt(index+2) == '|') {
						index2 = index+3;
					}
					else{
						index2 = index+2;
					}
				}
				categories = tmpList.toArray(new String[0]);
			}
		}
	}
}