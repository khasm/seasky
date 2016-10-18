package mie.crypto;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;

public final class CBIRSCipherSecretKeyFactory extends SecretKeyFactorySpi {

	@Override
	protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
		if(keySpec instanceof CBIRSCipherKeySpec)
			return (SecretKey)keySpec;
		else
			throw new InvalidKeySpecException();
	}

	@Override
	protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
		if(keySpec.isAssignableFrom(CBIRSCipherKeySpec.class)){
			return (CBIRSCipherKeySpec)key;
		}
		else
			throw new InvalidKeySpecException();
	}

	@Override
	protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
		// TODO Auto-generated method stub
		return null;
	}

}
