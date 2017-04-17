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

	public double getStat(DataPoint.Stat[] stat) {
		DataPoint.Unit[] units = new DataPoint.Unit[stat.length];
		for(int i = 0; i < stat.length; i++)
			units[i] = stat[i].getDefaultUnit();
		return getStat(stat, units);
	}

	public double getStat(DataPoint.Stat[] stat, DataPoint.Unit[] prefix) {
		double average = 0;
		for(DataPoint dp: aPoints){
			average += stat.length == 1 ? dp.getStat(stat[0], prefix[0])
				: dp.getStat(stat[0], prefix[0])/dp.getStat(stat[1], prefix[1]);
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

	public double getStandardDeviation(DataPoint.Stat[] stat) {
		DataPoint.Unit[] units = new DataPoint.Unit[stat.length];
		for(int i = 0; i < stat.length; i++)
			units[i] = stat[i].getDefaultUnit();
		return getStandardDeviation(stat, units);
	}

	public double getStandardDeviation(DataPoint.Stat[] stat, DataPoint.Unit[] prefix) {
		double average = getStat(stat, prefix);
		double tmpDeviation = 0;
		for(DataPoint dp: aPoints){
			tmpDeviation += Math.pow((stat.length == 1 ? dp.getStat(stat[0], prefix[0])
				: dp.getStat(stat[0], prefix[0])/dp.getStat(stat[1], prefix[1])) - average, 2);
		}
		return Math.sqrt(tmpDeviation/aPoints.length);
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