import java.util.List;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import javax.mail.MessagingException;

public class UnstructuredDocumentWriter implements DocumentWriter {

	private File outputDir;
	private int current;
	private String name;

	public UnstructuredDocumentWriter() {
		name = "Unstructured Document Writer";
		outputDir = new File("");
		current = 0;
	}

	public String getDescription() {
		return name;
	}

	public void setOutDir(File outDir) {
		outputDir = outDir;
	}

	public void writeDocument(String docName, List<byte[]> imgs, List<String> txts) throws 
		MessagingException, IOException {
		File imgDir = new File(outputDir, "imgs");
		if(!imgDir.exists()){
			imgDir.mkdirs();
		}
		File txtDir = new File(outputDir, "tags");
		if(!txtDir.exists()){
			txtDir.mkdirs();
		}
		for(byte[] img: imgs){
			FileOutputStream out = new FileOutputStream(new File(imgDir, "im"+current+".jpg"));
			out.write(img);
			out.close();
			String tag = "";
			for(String txt: txts){
				tag += txt +"\n";
			}
			out = new FileOutputStream(new File(txtDir, "tags"+current+".txt"));
			out.write(tag.getBytes());
			out.close();
			current++;
		}
	}
}