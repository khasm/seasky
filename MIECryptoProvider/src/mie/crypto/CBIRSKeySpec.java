package mie.crypto;

import java.security.InvalidKeyException;

public final class CBIRSKeySpec extends CBIRKeySpec {
	
	private static final long serialVersionUID = 3257240725567212420L;
	private byte[] key;
	
	public CBIRSKeySpec(byte[] key) {
		this(key, 0, key.length);
	}
	
	public CBIRSKeySpec(byte[] key, String format) throws InvalidKeyException{
		if(format.equalsIgnoreCase("RAW")){
			this.key = key.clone();
		}
		else{
			throw new InvalidKeyException("Format not recognized");
		}
	}
	
	public CBIRSKeySpec(byte[] key, int offset, int length) {
		this.key = new byte[length];
		System.arraycopy(key, offset, this.key, 0, length);
	}
	
	public byte[] getKey(){
		return key;
	}

	@Override
	public String getAlgorithm() {
		return "CBIRS";
	}

	@Override
	public byte[] getEncoded() {
		return key.clone();
	}

}
