package mie;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;

import mie.crypto.CBIRCipherKey;
import mie.crypto.CBIRCipherParameterSpec;
import mie.crypto.CBIRDCipherKeySpec;
import mie.crypto.CBIRDCipherParameterSpec;
import mie.crypto.CBIRDParameterSpec;
import mie.crypto.CBIRSCipherKeySpec;
import mie.crypto.CBIRSCipherParameterSpec;
import mie.crypto.CBIRSParameterSpec;

public class MIECryptoModule implements MIECrypto {
	
	private static final String IMG_KEY_FILE = "keys/cbird.key";
	private static final String TXT_KEY_FILE = "keys/cbirs.key";
	private static final String IV_FILE = "keys/iv";
	
	private static final String CBIRD_CIPHER = "CBIRDWithSymmetricCipher";
	private static final String CBIRS_CIPHER = "CBIRSWithSymmetricCipher";
	
	private CBIRCipherKey dkey,skey;
	private CBIRCipherParameterSpec cbirdParams, cbirsParams;
	
	public MIECryptoModule() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException {
		dkey = setup(IMG_KEY_FILE, CBIRD_CIPHER);
		skey = setup(TXT_KEY_FILE, CBIRS_CIPHER);
	}

	@Override
	public byte[] encryptImg(byte[] plain_text) throws IllegalBlockSizeException {
		return encrypt(plain_text, CBIRD_CIPHER, dkey, cbirdParams);
	}

	@Override
	public byte[] decryptImg(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException {
		return decrypt(cipher_text, CBIRD_CIPHER, dkey, cbirdParams);
	}

	@Override
	public byte[] cbirImg(byte[] img) {
		try {
			Mac cbird = Mac.getInstance("CBIRD");
			cbird.init(dkey.getCBIRSpec());
			cbird.update(img);
			return cbird.doFinal();
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			///should not happen at this point
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public byte[] encryptTxt(byte[] plain_text) throws IllegalBlockSizeException {
		return encrypt(plain_text, CBIRS_CIPHER, skey, cbirsParams);
	}

	@Override
	public byte[] decryptTxt(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException {
		return decrypt(cipher_text, CBIRS_CIPHER, skey, cbirsParams);
	}

	@Override
	public byte[] cbirTxt(byte[] txt) {
		try {
			Mac cbirs = Mac.getInstance("CBIRS");
			cbirs.init(skey.getCBIRSpec());
			cbirs.update(txt);
			return cbirs.doFinal();
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			///should not happen at this point
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public byte[] encryptMime(byte[] plain_text) throws IllegalBlockSizeException {
		try {
			String transformation = cbirdParams.getCipherAlgorithm()+"/"+cbirdParams.getCipherMode()+
			"/"+cbirdParams.getCipherPadding();
			Cipher cipher = Cipher.getInstance(transformation);
			cipher.init(Cipher.ENCRYPT_MODE, dkey.getCipherSpec(), cbirdParams.getCipherParameters());
			return cipher.doFinal(plain_text);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
			InvalidAlgorithmParameterException | BadPaddingException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public byte[] decryptMime(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException {
		try {
			String transformation = cbirdParams.getCipherAlgorithm()+"/"+cbirdParams.getCipherMode()+"/"
			+cbirdParams.getCipherPadding();
			Cipher cipher = Cipher.getInstance(transformation);
			cipher.init(Cipher.DECRYPT_MODE, dkey.getCipherSpec(), cbirdParams.getCipherParameters());
			return cipher.doFinal(cipher_text);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
			InvalidAlgorithmParameterException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Necessario para reconstruir as palavras do resultado do cbir para indexacao
	 * Nao incluido no resultado porque e um parametro da cifra
	 */
	@Override
	public int getKeywordSize() {
		AlgorithmParameters t = cbirsParams.getCBIRParameters();
		try {
			CBIRSParameterSpec t2 = t.getParameterSpec(CBIRSParameterSpec.class);
			return t2.getKeywordSize();
		} catch (InvalidParameterSpecException e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	/**
	 * Necessario para reconstruir a matrix de floats resultante do cbir para a indexacao
	 * Nao incluido no resultado porque e um parametro da cifra
	 */
	@Override
	public int getImgFeatureSize() {
		AlgorithmParameters t = cbirdParams.getCBIRParameters();
		try {
			CBIRDParameterSpec t2 = t.getParameterSpec(CBIRDParameterSpec.class);
			return t2.getM();
		} catch (InvalidParameterSpecException e) {
			///should never happen
			e.printStackTrace();
			return -1;
		}
	}
	
	private byte[] encrypt(byte[] plain_text, String transformation, SecretKey key, 
		CBIRCipherParameterSpec params) throws IllegalBlockSizeException{
		try {
			Cipher cipher = Cipher.getInstance(transformation);
			cipher.init(Cipher.ENCRYPT_MODE, key, params);
			return cipher.doFinal(plain_text);
		} catch (InvalidKeyException | InvalidAlgorithmParameterException | BadPaddingException
		| NoSuchAlgorithmException | NoSuchPaddingException e) {
			///should not happen at this point
			e.printStackTrace();
			return null;
		}
	}
	
	private byte[] decrypt(byte[] cipher_text, String transformation, SecretKey key, 
		CBIRCipherParameterSpec params) throws IllegalBlockSizeException, BadPaddingException{
		try {
			Cipher cipher = Cipher.getInstance(transformation);
			cipher.init(Cipher.DECRYPT_MODE, key, params);
			return cipher.doFinal(cipher_text);
		} catch (InvalidKeyException | InvalidAlgorithmParameterException | 
			NoSuchAlgorithmException | NoSuchPaddingException e) {
			///should not happen at this point
			e.printStackTrace();
			return null;
		}
	}
	
	private CBIRCipherKey setup(String fileName, String transformation) throws IOException, 
		NoSuchAlgorithmException, NoSuchPaddingException{
		File file = new File(fileName);
		CBIRCipherKey key = null;
		if(file.exists()){
			try {
				FileInputStream in = new FileInputStream(file);
				byte[] key_bytes = new byte[in.available()];
				in.read(key_bytes);
				in.close();
				try {
					if(transformation.equalsIgnoreCase(CBIRD_CIPHER)){
						key = new CBIRDCipherKeySpec(key_bytes);
					}
					else if(transformation.equalsIgnoreCase(CBIRS_CIPHER)){
						key = new CBIRSCipherKeySpec(key_bytes);
					}
				} catch (InvalidKeyException e) {
					key = null;
					System.err.println("Couldn't read img cbir_cipher key from file");
				}
			} catch (FileNotFoundException e) {
				///should never happen
				e.printStackTrace();
			}
		}
		if(key == null){
			KeyGenerator keyGen = KeyGenerator.getInstance(transformation);
			SecretKey skey = keyGen.generateKey();
			SecretKeyFactory keyFact = SecretKeyFactory.getInstance(transformation);
			try {
				if(transformation.equalsIgnoreCase(CBIRD_CIPHER)){
					key = (CBIRDCipherKeySpec) keyFact.getKeySpec(skey, CBIRDCipherKeySpec.class);
					
				}
				else if(transformation.equalsIgnoreCase(CBIRS_CIPHER)){
					key = (CBIRSCipherKeySpec) keyFact.getKeySpec(skey, CBIRSCipherKeySpec.class);
				}
				FileOutputStream out = new FileOutputStream(fileName);
				out.write(key.getEncoded());
				out.close();
			} catch (InvalidKeySpecException e) {
				/// should never happen
				e.printStackTrace();
			}
		}
		file = new File(IV_FILE);
		byte[] iv_bytes;
		if(file.exists()){
			///try to read iv from file
			FileInputStream in = new FileInputStream(file);
			iv_bytes = new byte[in.available()];
			in.read(iv_bytes);
			in.close();
		}
		else{
			///generate new iv, getting a cipher instance isn't really necessary
			///since iv size can be known ahead of time, but solves most problems 
			//if the default cipher used changes
			Cipher imgCipher = Cipher.getInstance(transformation);
			int iv_size = imgCipher.getBlockSize();
			iv_bytes = new byte[iv_size];
			SecureRandom random = new SecureRandom();
			random.nextBytes(iv_bytes);
			///store iv on a file or decryption won't be possible even with the correct key
			FileOutputStream out = new FileOutputStream(file);
			out.write(iv_bytes);;
			out.close();
		}
		IvParameterSpec iv = new IvParameterSpec(iv_bytes);
		try {
			if(transformation.equalsIgnoreCase(CBIRD_CIPHER)){
				cbirdParams = new CBIRDCipherParameterSpec(iv);
			}
			else if(transformation.equalsIgnoreCase(CBIRS_CIPHER)){
				cbirsParams = new CBIRSCipherParameterSpec(iv);
			}			
		} catch (InvalidParameterSpecException e) {
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}
		return key;
	}
}
