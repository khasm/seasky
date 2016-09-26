package mie.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

public abstract class CBIRCipherKeyGenerator extends KeyGeneratorSpi {

	private KeyGenerator cipher;
	private KeyGenerator cbir;
	private String cbirMode;
	
	protected CBIRCipherKeyGenerator(String cbirMode) {
		this.cbirMode = cbirMode;
	}

	@Override
	protected SecretKey engineGenerateKey() {
		if(cipher == null || cbir == null){
			SecureRandom random = new SecureRandom();
			this.engineInit(random);
		}
		SecretKey cipherKey = cipher.generateKey();
		SecretKey cbirKey = cbir.generateKey();
		CBIRCipherKey retKey = null;
		if(cbirMode.equalsIgnoreCase("CBIRD")){
			retKey = new CBIRDCipherKeySpec((CBIRDKeySpec)cbirKey, cipherKey);
		}
		else if(cbirMode.equalsIgnoreCase("CBIRS")){
			retKey = new CBIRSCipherKeySpec((CBIRSKeySpec)cbirKey, cipherKey);
		}
		return retKey;
	}

	@Override
	protected void engineInit(SecureRandom random) {
		this.engineInit(256, random);
	}

	@Override
	protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidAlgorithmParameterException {
		if(params instanceof CBIRCipherParameterSpec){
			CBIRCipherParameterSpec p = (CBIRCipherParameterSpec)params;
			try {
				try{
					cipher = KeyGenerator.getInstance(p.getCipherAlgorithm(), "BC");
					cbir = KeyGenerator.getInstance(cbirMode, "MIE");
				}  catch (NoSuchProviderException e) {
					cipher = KeyGenerator.getInstance(p.getCipherAlgorithm());
					cbir = KeyGenerator.getInstance(cbirMode);
				}
				cipher.init(random);
				cbir.init(p.getCBIRParameters().getParameterSpec(CBIRParameterSpec.class), random);
			} catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
				e.printStackTrace();
			}
		}
		else{
			throw new InvalidAlgorithmParameterException();
		}
	}

	@Override
	protected void engineInit(int keySize, SecureRandom random) {
		CBIRDCipherParameterSpec spec = new CBIRDCipherParameterSpec();
		try {
			try{
				cipher = KeyGenerator.getInstance(spec.getCipherAlgorithm(), "BC");
				cbir = KeyGenerator.getInstance(cbirMode, "MIE");
			} catch (NoSuchProviderException e) {
				cipher = KeyGenerator.getInstance(spec.getCipherAlgorithm());
				cbir = KeyGenerator.getInstance(cbirMode);
			}
			cipher.init(keySize, random);
			cbir.init(64, random);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
}
