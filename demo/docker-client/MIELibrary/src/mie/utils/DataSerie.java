package mie.utils;

import java.util.Map;
import java.util.HashMap;

public class DataSerie {

	private String aTitle;
	private DataPoint[] aPoints;

	public DataSerie(String title, DataPoint[] points) {
		aTitle = title;
		aPoints = points;
	}

	public String getTitle() {
		return aTitle;
	}

	public double getStat(DataPoint.Stat stat) {
		return getStat(stat, stat.getDefaultUnit());
	}

	public double getStat(DataPoint.Stat stat, DataPoint.Unit prefix) {
		double average = 0;
		for(DataPoint dp: aPoints){
			average += dp.getStat(stat, prefix);
		}
		return average/aPoints.length;
	}

	public double getStatMax(DataPoint.Stat stat) {
		return getStatMax(stat, stat.getDefaultUnit());
	}

	public double getStatMax(DataPoint.Stat stat, DataPoint.Unit prefix) {
		double max = Double.MIN_VALUE;
		for(DataPoint dp: aPoints){
			double value = dp.getStat(stat, prefix);
			if(value > max)
				max = value;
		}
		return max;
	}

	public double getStatMin(DataPoint.Stat stat) {
		return getStatMax(stat, stat.getDefaultUnit());
	}

	public double getStatMin(DataPoint.Stat stat, DataPoint.Unit prefix) {
		double min = Double.MAX_VALUE;
		for(DataPoint dp: aPoints){
			double value = dp.getStat(stat, prefix);
			if(value < min)
				min = value;
		}
		return min;
	}
}