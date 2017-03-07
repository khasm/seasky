import javafx.util.Pair;
import java.util.List;
import java.util.LinkedList;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Flickr implements DatasetParser {

	private String name;
	private int current;
	private int max;
	private File dataset;

	public Flickr() {
		name = "Flick-MIR";
		current = 0;
		max = -1;
		dataset = new File("");
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
		File imgFile = new File(dataset, "flickr_imgs/im"+current+".jpg");
		File txtFile = new File(dataset, "flickr_tags/tags"+current+".txt");
		List<byte[]> imgList = new LinkedList<byte[]>();
		List<String> txtList = new LinkedList<String>();
		try{
			FileInputStream in = new FileInputStream(imgFile);
			byte[] img = new byte[in.available()];
			in.read(img);
			in.close();
			in = new FileInputStream(txtFile);
			byte[] txt = new byte[in.available()];
			in.read(txt);
			in.close();
			imgList.add(img);
			txtList.add(new String(txt));
		}
		catch(IOException e){
			System.err.println("Error reading "+current);
			e.printStackTrace();
		}
		Pair<List<byte[]>,List<String>> contents = new Pair<List<byte[]>,List<String>>(imgList, txtList);
		return new Pair<String,Pair<List<byte[]>,List<String>>>(""+current++, contents);
	}

	private void initialize() throws IOException {
		File imgDir = new File(dataset, "flickr_imgs");
		File[] imgs = imgDir.listFiles();
		File txtDir = new File(dataset, "flickr_tags");
		File[] txts = txtDir.listFiles();
		if(imgs.length > txts.length)
			max = txts.length;
		else
			max = imgs.length;
	}

}