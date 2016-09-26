package mie.crypto;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public final class CBIRSCipherParameters extends CBIRCipherParameters {

	@Override
	protected void engineInit(AlgorithmParameterSpec paramSpec) throws InvalidParameterSpecException {
		if(paramSpec instanceof CBIRSCipherParameterSpec)
			init((CBIRCipherParameterSpec)paramSpec);
		else
			throw new InvalidParameterSpecException();
	}
}
