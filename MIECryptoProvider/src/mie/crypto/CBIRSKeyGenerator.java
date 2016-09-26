package mie.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

public final class CBIRSKeyGenerator extends KeyGeneratorSpi {

	private SecureRandom random;
	private int key_size;
	
	@Override
	protected SecretKey engineGenerateKey() {
		if(null == random){
			SecureRandom r = new SecureRandom();
			engineInit(r);
		}
		byte[] buffer = new byte[key_size];
		random.nextBytes(buffer);
		CBIRSKeySpec spec = new CBIRSKeySpec(buffer);
		return spec;
	}

	@Override
	protected void engineInit(SecureRandom random) {
		CBIRSParameterSpec params = new CBIRSParameterSpec();
		this.random = random;
		key_size = params.getKeySize();
	}

	@Override
	protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidAlgorithmParameterException {
		if(!(params instanceof CBIRSParameterSpec))
			throw new InvalidAlgorithmParameterException();
		this.random = random;
		key_size = ((CBIRSParameterSpec)params).getKeySize();

	}

	@Override
	protected void engineInit(int keysize, SecureRandom random) {
		this.random = random;
		key_size = keysize;
	}
}
