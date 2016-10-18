package mie.crypto;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;

public final class CBIRDSecretKeyFactory extends SecretKeyFactorySpi {

	@Override
	protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
		if(keySpec instanceof CBIRDKeySpec)
			return (CBIRDKeySpec)keySpec;
		else
			throw new InvalidKeySpecException();
	}

	@Override
	protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
		if(keySpec.isAssignableFrom(CBIRDKeySpec.class)){
			CBIRDKeySpec spec;
			try {
				spec = new CBIRDKeySpec(key.getEncoded(), key.getFormat());
			} catch (InvalidKeyException e) {
				throw new InvalidKeySpecException(e.getMessage());
			}
			return spec;
		}
		else
			throw new InvalidKeySpecException();
	}

	@Override
	protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
		//return new CBIRDSecretKey(key.getEncoded(), key.getFormat());
		return null;
	}

}
