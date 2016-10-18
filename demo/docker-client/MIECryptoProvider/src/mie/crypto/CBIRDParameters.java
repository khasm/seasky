package mie.crypto;

import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

public final class CBIRDParameters extends AlgorithmParametersSpi {
	
	private CBIRDParameterSpec spec;
	
	@Override
	protected void engineInit(AlgorithmParameterSpec paramSpec) throws InvalidParameterSpecException {
		if(paramSpec instanceof CBIRDParameterSpec)
			this.spec = (CBIRDParameterSpec)paramSpec;
		else
			throw new InvalidParameterSpecException();
	}

	@Override
	protected void engineInit(byte[] params) throws IOException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void engineInit(byte[] params, String format) throws IOException {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unchecked")
	@Override
	protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(Class<T> paramSpec)
			throws InvalidParameterSpecException {
		if(paramSpec.isInstance(spec))
			return (T)spec;
		else
			throw new InvalidParameterSpecException();
	}

	@Override
	protected byte[] engineGetEncoded() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected byte[] engineGetEncoded(String format) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected String engineToString() {
		// TODO Auto-generated method stub
		return null;
	}

}
