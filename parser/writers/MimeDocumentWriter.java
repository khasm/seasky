import java.util.Properties;
import java.util.List;
import java.util.Iterator;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.MessagingException;

public class MimeDocumentWriter implements DocumentWriter {

	private File outputDir;
	private String name;

	public MimeDocumentWriter() {
		name = "Mime Document Writer";
		outputDir = new File("");
	}

	public String getDescription() {
		return name;
	}

	public void setOutDir(File outDir) {
		outputDir = outDir;
	}

	public void writeDocument(String docName, List<byte[]> imgs, List<String> txts) throws 
		MessagingException, IOException {
		//set up message
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s);
		MimeMultipart content = new MimeMultipart();
		//add images
		Iterator<byte[]> imgIt = imgs.iterator();
		while(imgIt.hasNext()){
			byte[] img = imgIt.next();
			MimeBodyPart mp = new MimeBodyPart();
			mp.setContent(img, "image/jpeg");
			content.addBodyPart(mp);
		}
		//add text
		Iterator<String> txtIt = txts.iterator();
		while(txtIt.hasNext()){
			String txt = txtIt.next();
			MimeBodyPart mp = new MimeBodyPart();
			mp.setContent(txt, "text/plain");
			content.addBodyPart(mp);
		}
		msg.setContent(content);
		//write mime document
		File doc = new File(outputDir, docName+".mime");
		FileOutputStream out = new FileOutputStream(doc);
		msg.writeTo(out);
		out.close();
	}
}