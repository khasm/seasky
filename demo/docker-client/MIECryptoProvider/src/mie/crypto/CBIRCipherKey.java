package mie.crypto;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public abstract class CBIRCipherKey implements KeySpec, SecretKey {


	private static final long serialVersionUID = 2551899340923794890L;
	
	private CBIRKeySpec cbirKey;
	private SecretKey cipherKey;
	private String algorithm;
	
	protected CBIRCipherKey(CBIRKeySpec cbirKey, SecretKey cipherKey) {
		this.cbirKey = cbirKey;
		this.cipherKey = cipherKey;
		String cbirMode = "";
		if(cbirKey instanceof CBIRDKeySpec){
			cbirMode = "CBIRD:";
		}
		else if(cbirKey instanceof CBIRSKeySpec){
			cbirMode = "CBIRS:";
		}
		algorithm = cbirMode+cipherKey.getAlgorithm();
	}
	
	protected CBIRCipherKey(String cbirMode, byte[] key) throws InvalidKeyException {
		try{
			ByteBuffer buffer = ByteBuffer.wrap(key);
			int alg_s = buffer.getInt();
			byte[] alg_bytes = new byte[alg_s];
			buffer.get(alg_bytes);
			String alg = new String(alg_bytes);
			int l = buffer.getInt();
			byte[] b = new byte[l];
			buffer.get(b);
			if(cbirMode.equalsIgnoreCase("CBIRD")){
				cbirKey = new CBIRDKeySpec(b, "RAW");
			}
			else if(cbirMode.equalsIgnoreCase("CBIRS")){
				cbirKey = new CBIRSKeySpec(b, "RAW");
			}
			b = new byte[key.length-8-l-alg_s];
			buffer.get(b);
			cipherKey = new SecretKeySpec(b, alg);
			algorithm = cbirMode+":"+cipherKey.getAlgorithm();
		}
		catch (BufferUnderflowException | InvalidKeyException e){
			throw new InvalidKeyException("Format not recognized: "+e.getMessage());
		}
	}
	
	protected CBIRCipherKey(String cbirMode, byte[] key, int m, int k) throws InvalidKeyException{
		try{
			ByteBuffer buffer = ByteBuffer.wrap(key);
			int alg_s = buffer.getInt();
			byte[] alg_bytes = new byte[alg_s];
			buffer.get(alg_bytes);
			String alg = new String(alg_bytes);
			if(cbirMode.equalsIgnoreCase("CBIRD")){
				cbirKey = new CBIRDKeySpec(key, m, k);
				int offset = m*k*4+m*4;
				cipherKey = new SecretKeySpec(key, offset, key.length-offset, alg);
			}
			else if(cbirMode.equalsIgnoreCase("CBIRS")){
				cbirKey = new CBIRSKeySpec(key, 0, m);
				int offset = m;
				if(key.length-offset < k)
					throw new InvalidKeyException();
				cipherKey = new SecretKeySpec(key, offset, k, alg);
			}
			algorithm = cbirMode+":"+cipherKey.getAlgorithm();
		}
		catch (BufferUnderflowException | IllegalArgumentException | ArrayIndexOutOfBoundsException e){
			throw new InvalidKeyException("Format not recognized");
		}
	}
	
	public CBIRCipherKey(byte[] key, int m, int k, int cipherKeySize) throws InvalidKeyException{
		try{
			ByteBuffer buffer = ByteBuffer.wrap(key);
			int alg_s = buffer.getInt();
			byte[] alg_bytes = new byte[alg_s];
			buffer.get(alg_bytes);
			String alg = new String(alg_bytes);
			cbirKey = new CBIRDKeySpec(key, m, k);
			int offset = m*k*4+m*4;
			if(key.length-offset < cipherKeySize)
				throw new InvalidKeyException();
			cipherKey = new SecretKeySpec(key, offset, cipherKeySize, alg);
			algorithm = "CBIRD:"+cipherKey.getAlgorithm();
		}
		catch (BufferUnderflowException | IllegalArgumentException | ArrayIndexOutOfBoundsException e){
			throw new InvalidKeyException("Format not recognized");
		}
	}
	
	public CBIRCipherKey(byte[] key, int cbirKeyLength) throws InvalidKeyException{
		try{
			ByteBuffer buffer = ByteBuffer.wrap(key);
			int alg_s = buffer.getInt();
			byte[] alg_bytes = new byte[alg_s];
			buffer.get(alg_bytes);
			String alg = new String(alg_bytes);
			cbirKey = new CBIRSKeySpec(key, 0, cbirKeyLength);
			int offset = cbirKeyLength;
			cipherKey = new SecretKeySpec(key, offset, key.length-offset, alg);
			algorithm = "CBIRS:"+cipherKey.getAlgorithm();
		}
		catch (BufferUnderflowException | IllegalArgumentException | ArrayIndexOutOfBoundsException e){
			throw new InvalidKeyException("Format not recognized");
		}
			
	}
	
	@Override
	public String getFormat() {
		return "RAW";
	}

	@Override
	public byte[] getEncoded() {
		byte[] cbir = cbirKey.getEncoded();
		byte[] aes = cipherKey.getEncoded();
		byte[] alg_bytes = cipherKey.getAlgorithm().getBytes();
		ByteBuffer buffer = ByteBuffer.allocate(4+alg_bytes.length+4+cbir.length+aes.length);
		buffer.putInt(alg_bytes.length);
		buffer.put(alg_bytes);
		buffer.putInt(cbir.length);
		buffer.put(cbir);
		buffer.put(aes);
		return buffer.array();
	}
	
	public CBIRKeySpec getCBIRSpec(){
		return cbirKey;
	}
	
	public SecretKey getCipherSpec(){
		return cipherKey;
	}

	@Override
	public String getAlgorithm() {
		return algorithm;
	}

}
