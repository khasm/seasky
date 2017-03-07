import java.util.List;
import java.io.IOException;
import java.io.File;
import javax.mail.MessagingException;

public interface DocumentWriter extends Component {

	public void setOutDir(File outputDir);
	
	public void writeDocument(String docName, List<byte[]> imgs, List<String> txts) throws
		MessagingException, IOException;
}