package mie.crypto;

import java.security.Provider;

public final class MIEProvider extends Provider {

	private static final long serialVersionUID = -4096593686295266703L;

	public MIEProvider() {
		super("MIE", 1.0, "Provider implementing CBIR");
		///image features encryption
		put("Mac.CBIRD", CBIRDense.class.getName());
		put("SecretKeyFactory.CBIRD", CBIRDSecretKeyFactory.class.getName());
		put("KeyGenerator.CBIRD", CBIRDKeyGenerator.class.getName());
		put("AlgorithmParameters.CBIRD", CBIRDParameters.class.getName());
		put("AlgorithmParameterGenerator.CBIRD", CBIRDParameterGenerator.class.getName());
		///text features encryption
		put("Mac.CBIRS", CBIRSparse.class.getName());
		put("SecretKeyFactory.CBIRS", CBIRSSecretKeyFactory.class.getName());
		put("KeyGenerator.CBIRS", CBIRSKeyGenerator.class.getName());
		put("AlgorithmParameters.CBIRS", CBIRSParameters.class.getName());
		put("AlgorithmParameterGenerator.CBIRS", CBIRSParameterGenerator.class.getName());
		///image encryption
		put("Cipher.CBIRDWithSymmetricCipher", CBIRDCipher.class.getName());
		put("SecretKeyFactory.CBIRDWithSymmetricCipher", CBIRDCipherSecretKeyFactory.class.getName());
		put("KeyGenerator.CBIRDWithSymmetricCipher", CBIRDCipherKeyGenerator.class.getName());
		put("AlgorithmParameters.CBIRDWithSymmetricCipher", CBIRDCipherParameters.class.getName());
		///text encryption
		put("Cipher.CBIRSWithSymmetricCipher", CBIRSCipher.class.getName());
		put("SecretKeyFactory.CBIRSWithSymmetricCipher", CBIRSCipherSecretKeyFactory.class.getName());
		put("KeyGenerator.CBIRSWithSymmetricCipher", CBIRSCipherKeyGenerator.class.getName());
		put("AlgorithmParameters.CBIRSWithSymmetricCipher", CBIRSCipherParameters.class.getName());
	}

}
