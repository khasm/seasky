package mie.crypto;

import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

public abstract class CBIRCipherParameterSpec implements AlgorithmParameterSpec {
	
	public static final String DEFAULT_CIPHER = "AES";
	public static final String DEFAULT_MODE = "CTR";
	public static final String DEFAULT_PADDING = "PKCS7Padding";
	
	private AlgorithmParameters cbir, cipher;
	private IvParameterSpec iv;
	private String algorithm, mode, padding, cbirMode;
	
	protected CBIRCipherParameterSpec(String cbir){
		try {
			init(cbir, DEFAULT_CIPHER, DEFAULT_MODE, DEFAULT_PADDING);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			///should never happen
			e.printStackTrace();
		}
	}
	
	protected CBIRCipherParameterSpec(String cbirMode, AlgorithmParameters cbirSpec, AlgorithmParameters cipherSpec, String algorithm, String mode, String padding, byte[] iv_bytes){
		this.cbir = cbirSpec;
		this.cipher = cipherSpec;
		this.cbirMode = cbirMode;
		this.algorithm = algorithm;
		this.mode = mode;
		this.padding = padding;
		if(iv_bytes != null){
			iv = new IvParameterSpec(iv_bytes);
		}
		else{
			iv = null;
		}
	}
	
	protected CBIRCipherParameterSpec(String cbir, String transformation) throws NoSuchAlgorithmException, NoSuchPaddingException{
		String[] elem = Utils.parseTransformation(transformation);
		init(cbir, elem[0], elem[1], elem[2]);
	}
	
	protected CBIRCipherParameterSpec(String cbir, String transformation, AlgorithmParameterSpec params) throws InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException{
		String[] elem = Utils.parseTransformation(transformation);
		init(cbir, elem[0], elem[1], elem[2], params);
	}
	
	protected CBIRCipherParameterSpec(String cbir, AlgorithmParameterSpec params) throws InvalidParameterSpecException, InvalidAlgorithmParameterException{
		try {
			init(cbir, DEFAULT_CIPHER, DEFAULT_MODE, DEFAULT_PADDING, params);
		} catch (InvalidParameterSpecException e) {
			throw new InvalidParameterSpecException("The specified parameter spec is not valid for the default cipher (AES)");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			// Should never happen
			e.printStackTrace();
		}
	}
	
	private void init(String cbir, String algorithm, String mode, String padding) throws NoSuchAlgorithmException, NoSuchPaddingException{
		try {
			Cipher.getInstance(algorithm+"/"+mode+"/"+padding, "BC");
		} catch (NoSuchProviderException e) {
			Cipher.getInstance(algorithm+"/"+mode+"/"+padding);
		}
		AlgorithmParameterGenerator apg;
		try{
			try{
				apg = AlgorithmParameterGenerator.getInstance(algorithm, "BC");
			} catch (NoSuchProviderException | NoSuchAlgorithmException e) {
				apg = AlgorithmParameterGenerator.getInstance(algorithm);
			}
			cipher = apg.generateParameters();
		} catch (NoSuchAlgorithmException e){
			cipher = null;
		}
		apg = AlgorithmParameterGenerator.getInstance(cbir);
		this.cbir = apg.generateParameters();
		this.algorithm = algorithm;
		this.mode = mode;
		this.padding = padding;
		this.cbirMode = cbir;
		this.iv = null;
	}
	
	private void init(String cbir, String algorithm, String mode, String padding, AlgorithmParameterSpec params) throws InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException{
		this.iv = null;
		try {
			Cipher.getInstance(algorithm+"/"+mode+"/"+padding, "BC");
		} catch (NoSuchProviderException e) {
			Cipher.getInstance(algorithm+"/"+mode+"/"+padding);
		}
		try {
			try{
				cipher = AlgorithmParameters.getInstance(algorithm, "BC");
			} catch (NoSuchProviderException | NoSuchAlgorithmException e) {
				cipher = AlgorithmParameters.getInstance(algorithm);
			}
			cipher.init(params);
		} catch (NoSuchAlgorithmException e) {
			if(params instanceof IvParameterSpec){
				this.iv = (IvParameterSpec)params;
			}
			else{
				e.printStackTrace();
			}
		}
		AlgorithmParameterGenerator apg = AlgorithmParameterGenerator.getInstance(cbir);
		this.cbir = apg.generateParameters();
		
		this.mode = mode;
		this.padding = padding;
		this.algorithm = algorithm;
		this.cbirMode = cbir;
	}

	public AlgorithmParameters getCBIRParameters(){
		return cbir;
	}
	
	public AlgorithmParameters getCipherParameters(){
		return cipher;
	}
	
	public byte[] getIv(){
		if(iv == null){
			return null;
		}
		else{
			return iv.getIV();
		}
	}
	
	public String getCipherMode(){
		return mode;
	}
	
	public String getCipherPadding(){
		return padding;
	}
	
	public String getCipherAlgorithm(){
		return algorithm;
	}
	
	public String getCBIRMode(){
		return cbirMode;
	}
}
