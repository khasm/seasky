package mie;

public class SearchResult {
	
	private final String id;
	private final double score;
	
	public SearchResult(String id, double score){
		this.id = id;
		this.score = score;
	}

	public String getId() {
		return id;
	}

	public double getScore() {
		return score;
	}

}
