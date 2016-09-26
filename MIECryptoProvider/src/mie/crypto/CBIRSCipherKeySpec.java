package mie.crypto;

import java.security.InvalidKeyException;

import javax.crypto.SecretKey;

public final class CBIRSCipherKeySpec extends CBIRCipherKey {

	private static final long serialVersionUID = 8542902545614856805L;
	private static final String ALGORITHM = "CBIRS";

	public CBIRSCipherKeySpec(CBIRSKeySpec cbir, SecretKey cipher) {
		super(cbir, cipher);
	}
	
	public CBIRSCipherKeySpec(byte[] key) throws InvalidKeyException{
		super(ALGORITHM, key);
	}
	
	public CBIRSCipherKeySpec(byte[] key, int cbirKeyLength) throws InvalidKeyException{
		super(key, cbirKeyLength);
	}
	
	public CBIRSCipherKeySpec(byte[] key, int cbirKeyLength, int cipherKeySize) throws InvalidKeyException{
		super(ALGORITHM, key, cbirKeyLength, cipherKeySize);
	}
}
