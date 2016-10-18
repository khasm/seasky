package mie.crypto;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;

public final class CBIRDKeySpec extends CBIRKeySpec {
	
	private static final long serialVersionUID = -8786858322490357316L;
	private float[][] a;
	private float[] w;
	
	public CBIRDKeySpec(byte[] key, int m, int k) throws InvalidKeyException{
		this(key, 0, key.length, m, k);
	}
	
	public CBIRDKeySpec(byte[]key, CBIRDParameterSpec param) throws InvalidKeyException{
		this(key, 0, key.length, param.getM(), param.getK());
	}
	
	public CBIRDKeySpec(byte[]key, int offset, int length, CBIRDParameterSpec param) throws InvalidKeyException{
		this(key, offset, length, param.getM(), param.getK());
	}
	
	public CBIRDKeySpec(byte[] key, String format) throws InvalidKeyException{
		if(format.equalsIgnoreCase("RAW")){
			ByteBuffer bb = ByteBuffer.wrap(key, 0, 8);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			int m = bb.getInt();
			int k =  bb.getInt();
			readKeyFromArray(key, 8, key.length-8, m, k, true);
		}
		else
			throw new InvalidKeyException("Encoding not recognized");
	}
	
	public CBIRDKeySpec(byte[] key, int offset, int length, int m, int k) throws InvalidKeyException{
		if(key == null) 
			throw new NullPointerException();
		readKeyFromArray(key, offset, length, m, k, false);
	}
	
	public CBIRDKeySpec(float[][] a, float[] w){
		this.a = a.clone();
		this.w = w.clone();
	}
	
	private void readKeyFromArray(byte[] key, int offset, int length, int m, int k, boolean littleEndian) throws InvalidKeyException{
		ByteBuffer buffer = ByteBuffer.wrap(key, offset, length);
		if(littleEndian){
			buffer.order(ByteOrder.LITTLE_ENDIAN);
		}
		a = new float[m][k];
		w = new float[m];
		try{
			for(int i = 0; i < m; i++)
				for(int j = 0; j < k; j++)
					a[i][j] = buffer.getFloat();
			for(int i = 0; i < m; i++)
				w[i] = buffer.getFloat();
		}
		catch(BufferUnderflowException e){
			throw new InvalidKeyException("Format not recognized");
		}
	}
	
	public float[][] getA(){
		return a.clone();
	}

	public float[] getW(){
		return w.clone();
	}
	
	public int getM(){
		return w.length;
	}
	
	public int getK(){
		return a[0].length;
	}

	@Override
	public String getAlgorithm() {
		return "CBIRD";
	}

	@Override
	public byte[] getEncoded() {
		int m = w.length;
		int k = a[0].length;
		ByteBuffer buffer = ByteBuffer.allocate(k*m*4+m*4+8);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(m);
		buffer.putInt(k);
		for(int i = 0; i < m; i++){
			for(int j = 0; j < k; j++){
				buffer.putFloat(a[i][j]);
			}
		}
		for(int i = 0; i < m; i++){
			buffer.putFloat(w[i]);
		}
		return buffer.array();
	}
}
