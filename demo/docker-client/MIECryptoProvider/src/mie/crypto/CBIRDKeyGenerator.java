package mie.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

public final class CBIRDKeyGenerator extends KeyGeneratorSpi {

	private SecureRandom random;
	private CBIRDParameterSpec param;
	
	@Override
	protected SecretKey engineGenerateKey() {
		if(null == random){
			SecureRandom r = new SecureRandom();
			engineInit(r);
		}
		int m = param.getM();
		int k = param.getK();
		float[][] a = new float[m][k];
		float[] w = new float[m];
		for(int i = 0; i < m; i++){
			for(int j = 0; j < k; j++)
				a[i][j] = random.nextFloat();
			w[i] = random.nextFloat();
		}
		CBIRDKeySpec spec = new CBIRDKeySpec(a, w);
		return spec;
	}

	@Override
	protected void engineInit(SecureRandom random) {
		try{
			this.engineInit(new CBIRDParameterSpec(), random);
		} catch (InvalidAlgorithmParameterException e){
			e.printStackTrace();
		}
	}

	@Override
	protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidAlgorithmParameterException {
		if(params instanceof CBIRDParameterSpec)
			this.param = (CBIRDParameterSpec)params;
		else
			throw new InvalidAlgorithmParameterException();
		this.random = random;

	}

	@Override
	protected void engineInit(int keySize, SecureRandom random) {
		this.random = random;
		param = new CBIRDParameterSpec(keySize, random);
	}

}
