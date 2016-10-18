package mie.crypto;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.NoSuchPaddingException;

public final class CBIRSCipherParameterSpec extends CBIRCipherParameterSpec {

private static final String ALGORITHM = "CBIRS";
	
	public CBIRSCipherParameterSpec() {
		super(ALGORITHM);
	}
	
	public CBIRSCipherParameterSpec(String transformation) throws NoSuchAlgorithmException, NoSuchPaddingException{
		super(ALGORITHM, transformation);
	}
	
	public CBIRSCipherParameterSpec(String transformation, AlgorithmParameterSpec params) throws InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException{
		super(ALGORITHM, transformation, params);
	}
	
	public CBIRSCipherParameterSpec(AlgorithmParameterSpec params) throws InvalidParameterSpecException, InvalidAlgorithmParameterException{
		super(ALGORITHM, params);
	}

	public CBIRSCipherParameterSpec(AlgorithmParameters cbirSpec, AlgorithmParameters cipherSpec, String cipherAlgorithm,
			String cipherMode, String cipherPadding, byte[] iv) {
		super(ALGORITHM, cbirSpec, cipherSpec, cipherAlgorithm, cipherMode, cipherPadding, iv);
	}
}
