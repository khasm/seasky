package mie.crypto;

import java.security.SecureRandom;

import org.opencv.core.Core;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;

public final class CBIRDParameterSpec implements CBIRParameterSpec {
	
	private int m, k, featureDetectorType, descriptorExtractorType;
	private float delta;
	
	static{
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}
	
	public CBIRDParameterSpec(){
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.SURF);
		m = 64;
		delta = 0.5f;
		k = extractor.descriptorSize();
		featureDetectorType = FeatureDetector.SURF;
		descriptorExtractorType = DescriptorExtractor.SURF;
	}
	
	protected CBIRDParameterSpec(int m, SecureRandom random){
		this(m, FeatureDetector.SURF, DescriptorExtractor.SURF, 0.5f);
	}
	
	protected CBIRDParameterSpec(int m, int featureDetectorType, int descriptorExtractorType, float delta){
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.SURF);
		this.m = m;
		this.k = extractor.descriptorSize();
		this.featureDetectorType = featureDetectorType;
		this.descriptorExtractorType = descriptorExtractorType;
		this.delta = delta;
	}
	
	public int getM(){
		return m;
	}
	
	public int getK(){
		return k;
	}
	
	public float getDelta(){
		return delta;
	}
	
	public int getFeatureDetectorType(){
		return featureDetectorType;
	}

	public int getDescriptorExtractorType(){
		return descriptorExtractorType;
	}

}
