package mie.utils;

public class SearchStats {

	private int hit;
	private int miss;
	private int total;
	private int maxI;
	private int minI;
	private String maxId;
	private String minId;
	private double max;
	private double min;
	private double average;

	public SearchStats() {
		hit = 0;
		miss = 0;
		total = 0;
		maxI = -1;
		minI = -1;
		maxId = "";
		minId = "";
		max = Double.MIN_VALUE;
		min = Double.MAX_VALUE;
		average = 0;
	}

	public void hit() {
		hit++;
		total++;
	}

	public void miss() {
		miss++;
		total++;
	}

	public void addScore(double score) {
		average += score;
	}

	public void setMinMax(int i, String id, double score) {
		if(max < score){
			maxI = i;
			maxId = id;
			max = score;
		}
		if(min > score){
			minI = i;
			minId = id;
			min = score;
		}
	}

	public void merge(SearchStats otherStats) {
		hit += otherStats.hit;
		miss += otherStats.miss;
		total += otherStats.total;
		if(max < otherStats.max){
			maxI = otherStats.maxI;
			maxId = otherStats.maxId;
			max = otherStats.max;
		}
		if(min > otherStats.min){
			minI = otherStats.minI;
			minId = otherStats.minId;
			min = otherStats.min;
		}
		average += otherStats.average;
	}

	public boolean hasData() {
		return 0 < total;
	}

	@Override
	public String toString() {
		return String.format("Hits: %d Misses: %d Hit Ratio: %.6f\nAverage: %.6f\nMax: %d %s %.6f\n"+
			"Min: %d %s %.6f", hit, miss, ((double)hit/(double)total)*100, average/total, maxI,
			maxId, max,minI, minId, min);
	}

}