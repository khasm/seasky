import javafx.util.Pair;
import java.util.List;
import java.io.File;

public interface DatasetParser extends Component {

	public void setDatasetDir(File dir);

	public boolean hasNext();

	public Pair<String,Pair<List<byte[]>,List<String>>> getNextDoc();
}