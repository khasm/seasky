package mie.crypto;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.SecretKeySpec;

public final class CBIRSSecretKeyFactory extends SecretKeyFactorySpi {

	@Override
	protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
		if(keySpec instanceof CBIRSKeySpec)
			return (CBIRSKeySpec)keySpec;
		else if(keySpec instanceof SecretKeySpec){
			String algorithm = ((SecretKeySpec) keySpec).getAlgorithm();
			String format = ((SecretKeySpec) keySpec).getFormat();
			if(algorithm.startsWith("CBIRS") && format.equalsIgnoreCase("RAW")){
				byte[] key = ((SecretKeySpec) keySpec).getEncoded();
				CBIRSKeySpec skey;
				try {
					skey = new CBIRSKeySpec(key, format);
				} catch (InvalidKeyException e) {
					throw new InvalidKeySpecException(e.getMessage());
				}
				return skey;
			}
		}
		throw new InvalidKeySpecException();
	}

	@Override
	protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
		if(keySpec.isAssignableFrom(CBIRSKeySpec.class)){
			CBIRSKeySpec spec;
			try {
				spec = new CBIRSKeySpec(key.getEncoded(), key.getFormat());
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
		return new CBIRSKeySpec(key.getEncoded(), key.getFormat());
	}

}
