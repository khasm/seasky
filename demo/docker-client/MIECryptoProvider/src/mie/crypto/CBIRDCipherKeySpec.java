package mie.crypto;

import java.security.InvalidKeyException;
import javax.crypto.SecretKey;

public final class CBIRDCipherKeySpec extends CBIRCipherKey {

	private static final long serialVersionUID = -1121581187889599061L;
	private static final String ALGORITHM = "CBIRD";
	
	public CBIRDCipherKeySpec(CBIRDKeySpec cbir, SecretKey cipher) {
		super(cbir, cipher);
	}
	
	public CBIRDCipherKeySpec(byte[] key) throws InvalidKeyException{
		super(ALGORITHM, key);
	}
	
	public CBIRDCipherKeySpec(byte[] key, int m, int k) throws InvalidKeyException{
		super(ALGORITHM, key, m, k);
	}
	
	public CBIRDCipherKeySpec(byte[] key, int m, int k, int cipherKeySize) throws InvalidKeyException{
		super(key, m, k, cipherKeySize);
	}
}
