package mie.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.MacSpi;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;
import org.opencv.highgui.Highgui;

public final class CBIRDense extends MacSpi {
	
	private CBIRDKeySpec key;
	private int m, k, featureDetectorType, descriptorExtractorType;
	private float delta;
	private FeatureDetector detector;
	private DescriptorExtractor extractor;
	private Mat descriptors;

	private byte[] buffer;
	private float[][] encoded_features;
	
	private static int cores;

	/*variables only used in extractfeatures but need to be declared here
	  so the garbage collector doesn't collect them and free the c++ memory
	  allocations ahead of time
	*/
	private MatOfKeyPoint keypoints;
	private Mat img;
	
	static{
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		cores = Runtime.getRuntime().availableProcessors();
	}

	@Override
	protected byte[] engineDoFinal() {
		///create mat from buffer
		extractFeatures();
		long startBenchmark = System.nanoTime(); ///start crypto benchmark
		int rows = descriptors.rows();
		//System.out.println("Rows: "+rows);
		encoded_features = new float[rows][];
		int rowsPerThread = (int)Math.ceil((double)rows/(double)cores);
		Thread[] threads = new Thread[cores];
		for(int i = 0; i < cores; i++){
			int start = i*rowsPerThread;
			int last = start+rowsPerThread;
			if(last > rows){
				last = rows;
			}
			//System.out.println("Thread["+i+"]: ["+start+", "+last+"[");
			threads[i] = new EncryptionThread(start, last);
			threads[i].start();
		}
		for(int i = 0; i < cores; i++){
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		ByteBuffer byte_buffer = ByteBuffer.allocate(encoded_features.length*m*4+9);
		byte_buffer.put(Utils.CBIRD);
		byte_buffer.putInt(rows);
		byte_buffer.putInt(m);
		for(int i = 0; i < rows; i++)
			for(int j = 0; j < m; j++)
				byte_buffer.putFloat(encoded_features[i][j]);
		encoded_features = null;
		long time = System.nanoTime()-startBenchmark;///end crypto benchmark
		TimeSpec.addCbirEncryptionTime(time);
		engineReset();
		return byte_buffer.array();
	}

	@Override
	protected int engineGetMacLength() {
		extractFeatures();
		///add space for an extra byte that contains the number of rows
		return descriptors.rows()*m*4+9;
	}
	
	private void extractFeatures(){
		///create mat from buffer
		if(null == descriptors){
			long start = System.nanoTime(); ///begin featureExtractor benchmark
			Mat img_buffer = new Mat(1, buffer.length, CvType.CV_8S);
			try{
			    img_buffer.put(0, 0, buffer);
			}catch(UnsupportedOperationException e){
				///not so silently ignored anymore
				e.printStackTrace();
			}
			img = Highgui.imdecode(img_buffer, Highgui.CV_LOAD_IMAGE_COLOR);
			///detect features
			keypoints = new MatOfKeyPoint();
			detector.detect(img, keypoints);
			long time = System.nanoTime()-start;///end featureExtractor benchmark
			TimeSpec.addFeatureTime(time);
			///extract features
			descriptors = new Mat();
			start = System.nanoTime(); ///begin index benchmark
			extractor.compute(img, keypoints, descriptors);

			time = System.nanoTime()-start; ///end index benchmark
			TimeSpec.addIndexTime(time);
		}
	}

	@Override
	protected void engineInit(Key key, AlgorithmParameterSpec params)
			throws InvalidKeyException, InvalidAlgorithmParameterException {
		if(key == null){
			throw new NullPointerException();
		}
		CBIRDParameterSpec pspec = (CBIRDParameterSpec)params;
		if(params == null){
			pspec = new CBIRDParameterSpec();
		}
		else if(params instanceof CBIRDParameterSpec){
			pspec = (CBIRDParameterSpec)params;
		}
		else{
			throw new InvalidAlgorithmParameterException("Wrong parameter spec");
		}
		CBIRDKeySpec mieKey;
		if(key instanceof CBIRDKeySpec){
			mieKey = (CBIRDKeySpec)key;
			descriptorExtractorType = pspec.getDescriptorExtractorType();
			featureDetectorType = pspec.getFeatureDetectorType();
			extractor = DescriptorExtractor.create(descriptorExtractorType);
			detector = FeatureDetector.create(featureDetectorType);
			CBIRDKeySpec s1 = new CBIRDKeySpec(mieKey.getA(), mieKey.getW());
			this.key = s1;
			m = pspec.getM();
			k = pspec.getK();
			delta = pspec.getDelta();
			buffer = new byte[0];
			descriptors = null;
		}
		else
			throw new InvalidKeyException();
	}

	@Override
	protected void engineReset() {
		buffer = new byte[0];
		descriptors = null;
	}

	@Override
	protected void engineUpdate(byte input) {
		byte[] buffer = new byte[1];
		buffer[0] = input;
		engineUpdate(buffer, 0, 1);
	}

	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		int buffer_count = buffer.length;
		byte[] tmp = new byte[buffer.length+len];
		for(int i = 0; i< buffer.length ; i++)
			tmp[i] = buffer[i];
		buffer = tmp;
		System.arraycopy(input, offset, buffer, buffer_count, len);
		descriptors = null;
	}
	
	private class EncryptionThread extends Thread{
		
		private int start, last;
		
		protected EncryptionThread(int start, int last) {
			this.start = start;
			this.last = last;
		}

		@Override
		public void run() {
			int cols = descriptors.cols();
			float[] feature = new float[cols];
			float[] f = new float[1];
			float[][] keyA = key.getA();
			float[] keyW = key.getW();
			for(int i = start; i < last; i++){
				for(int j = 0; j < cols; j++){
					descriptors.get(i, j, f);
					feature[j] = f[0];
				}
				float[] encoded = new float[m];
				for(int i2 = 0; i2 < m; i2++){
					for(int j = 0; j < k; j++){
						encoded[i2] += keyA[i2][j] * feature[j];
					}
					encoded[i2] += keyW[i2];
					encoded[i2] /= delta;
					if((int)encoded[i2]%2 != 0)
						encoded[i2] = 0;
					else
						encoded[i2] = 1;
				}
				encoded_features[i] = encoded;
			}
		}
	}
}
