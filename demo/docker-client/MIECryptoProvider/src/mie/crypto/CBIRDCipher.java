package mie.crypto;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.Cipher;

public final class CBIRDCipher extends CBIRCipher {
	
	private static final String ALGORITHM = "CBIRD";
	
	public CBIRDCipher() {
		super(ALGORITHM);
	}

	@Override
	protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			CBIRDCipherParameterSpec params = new CBIRDCipherParameterSpec();
			try {
				engineInit(opmode, key, params, random);
			} catch (InvalidAlgorithmParameterException e) {
				///should never happen
				e.printStackTrace();
			}
			break;
		case(Cipher.DECRYPT_MODE):
			try {
				engineInit(opmode, key, (AlgorithmParameterSpec)null, random);
			} catch (InvalidAlgorithmParameterException e) {
				throw new InvalidKeyException(e.getMessage());
			}
		}
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidKeyException, InvalidAlgorithmParameterException {
		init(opmode, key, params, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
			throws InvalidKeyException, InvalidAlgorithmParameterException {
		try {
			CBIRDCipherParameterSpec dspec = params.getParameterSpec(CBIRDCipherParameterSpec.class);
			engineInit(opmode, key, dspec, random);
		} catch (InvalidParameterSpecException e) {
			throw new InvalidAlgorithmParameterException();
		}

	}
}
