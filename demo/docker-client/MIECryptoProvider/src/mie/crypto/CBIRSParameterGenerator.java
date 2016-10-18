package mie.crypto;

import java.security.AlgorithmParameterGeneratorSpi;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public final class CBIRSParameterGenerator extends AlgorithmParameterGeneratorSpi {
	
	private CBIRSParameterSpec spec;

	@Override
	protected void engineInit(int size, SecureRandom random) {
		try {
			this.spec = new CBIRSParameterSpec(size, "HMac-SHA1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void engineInit(AlgorithmParameterSpec genParamSpec, SecureRandom random)
			throws InvalidAlgorithmParameterException {
		if(genParamSpec instanceof CBIRSParameterSpec){
			this.spec = (CBIRSParameterSpec)genParamSpec;
		}
		else{
			throw new InvalidAlgorithmParameterException();
		}

	}

	@Override
	protected AlgorithmParameters engineGenerateParameters() {
		try {
			AlgorithmParameterSpec paramSpec;
			if(spec == null){
				paramSpec = new CBIRSParameterSpec();
			}
			else{
				paramSpec = this.spec;
			}
			AlgorithmParameters params = AlgorithmParameters.getInstance("CBIRS");
			params.init(paramSpec);
			return params;
		} catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
			e.printStackTrace();
		}
		return null;
	}

}
