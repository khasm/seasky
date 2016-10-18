package mie.crypto;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.NoSuchPaddingException;

public final class CBIRDCipherParameterSpec extends CBIRCipherParameterSpec {
	
	private static final String ALGORITHM = "CBIRD";
	
	public CBIRDCipherParameterSpec() {
		super(ALGORITHM);
	}
	
	public CBIRDCipherParameterSpec(String transformation) throws NoSuchAlgorithmException, NoSuchPaddingException{
		super(ALGORITHM, transformation);
	}
	
	public CBIRDCipherParameterSpec(String transformation, AlgorithmParameterSpec params) throws InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException{
		super(ALGORITHM, transformation, params);
	}
	
	public CBIRDCipherParameterSpec(AlgorithmParameterSpec params) throws InvalidParameterSpecException, InvalidAlgorithmParameterException{
		super(ALGORITHM, params);
	}
	
	protected CBIRDCipherParameterSpec(AlgorithmParameters cbirSpec, AlgorithmParameters cipherSpec, String algorithm, String mode, String padding, byte[] iv){
		super(ALGORITHM, cbirSpec, cipherSpec, algorithm, mode, padding, iv);
	}
}
