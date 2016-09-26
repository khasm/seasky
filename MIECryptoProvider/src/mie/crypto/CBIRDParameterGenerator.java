package mie.crypto;

import java.security.AlgorithmParameterGeneratorSpi;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public final class CBIRDParameterGenerator extends AlgorithmParameterGeneratorSpi {
	
	private CBIRDParameterSpec spec;

	@Override
	protected void engineInit(int size, SecureRandom random) {
		this.spec = new CBIRDParameterSpec(size, random);
	}

	@Override
	protected void engineInit(AlgorithmParameterSpec genParamSpec, SecureRandom random)
			throws InvalidAlgorithmParameterException {
		if(genParamSpec instanceof CBIRDParameterSpec){
			this.spec = (CBIRDParameterSpec)genParamSpec;
		}
		else
			throw new InvalidAlgorithmParameterException();
	}

	@Override
	protected AlgorithmParameters engineGenerateParameters() {
		try {
			AlgorithmParameterSpec paramSpec;
			if(spec == null){
				paramSpec = new CBIRDParameterSpec();
			}
			else{
				paramSpec = this.spec;
			}
			AlgorithmParameters params = AlgorithmParameters.getInstance("CBIRD");
			params.init(paramSpec);
			return params;
		} catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
			e.printStackTrace();
		}
		return null;
	}

}
