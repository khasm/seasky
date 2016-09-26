package mie.crypto;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public final class CBIRDCipherParameters extends CBIRCipherParameters {

	@Override
	protected void engineInit(AlgorithmParameterSpec paramSpec) throws InvalidParameterSpecException {
		if(paramSpec instanceof CBIRDCipherParameterSpec)
			init((CBIRDCipherParameterSpec)paramSpec);
		else
			throw new InvalidParameterSpecException();
	}
}
