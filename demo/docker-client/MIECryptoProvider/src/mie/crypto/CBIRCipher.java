package mie.crypto;

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;

public abstract class CBIRCipher extends CipherSpi {
	
	private Mac cbir;
	private Cipher cipher;
	private byte[] buffer;
	private int bufferCount, opmode;
	private CBIRCipherParameterSpec defaultSpec;
	private CBIRCipherKey cryptoKey;
	private CBIRCipherParameterSpec cryptoParams;
	private String cbirMode;
	
	protected CBIRCipher(String cbirMode) {
		this.cbirMode = cbirMode;
		cryptoParams = null;
		defaultSpec = new CBIRDCipherParameterSpec();
		String mode = defaultSpec.getCipherMode();
		String padding = defaultSpec.getCipherPadding();
		String alg = defaultSpec.getCipherAlgorithm();
		try {
			try {
				cipher = Cipher.getInstance(alg+"/"+mode+"/"+padding, "BC");
			} catch (NoSuchProviderException e) {
				cipher = Cipher.getInstance(alg+"/"+mode+"/"+padding);
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
			throws IllegalBlockSizeException, BadPaddingException {
		byte[] cipherOutput;
		ByteBuffer bb;
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			int total;
			int cbir_length;
			if(input != null){
				///tested
				cbir.update(input, inputOffset, inputLen);
				cbir_length = cbir.getMacLength();
				total = 5 + cbir_length + cipher.getOutputSize(inputLen);
				bb = ByteBuffer.allocate(total);
				long start = System.nanoTime();///start crypto benchmark
				try{
					cipher.doFinal(input, inputOffset, inputLen, bb.array(), 5 +
						cbir_length);
				}
				catch(ShortBufferException e){
					System.err.println("CBIRCipher.java:71::"+e.getMessage());
				}
				long time = System.nanoTime()-start;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time);
			}
			else{
				///tested
				cbir_length = cbir.getMacLength();
				total = 5 + cbir_length + cipher.getOutputSize(0);
				bb = ByteBuffer.allocate(total);
				long start = System.nanoTime();///start crypto benchmark
				try{
					cipher.doFinal(bb.array(), 5 + cbir_length);
				}
				catch(ShortBufferException e){
					System.err.println("CBIRCipher.java:87::"+e.getMessage());
				}
				long time = System.nanoTime()-start;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time);
			}
			try{
				cbir.doFinal(bb.array(), 5);
			}
			catch(ShortBufferException e){
				System.err.println("CBIRCipher.java:96::"+e.getMessage());
			}
			long start = System.nanoTime();///start misc benchmark
			bb.position(0);
			bb.put(Utils.FULL_CIPHER);
			bb.putInt(cbir_length);
			long time = System.nanoTime()-start;///end misc benchmark
			TimeSpec.addMiscEncryptionTime(time);
			this.reset();
			return bb.array();
		case(Cipher.DECRYPT_MODE):
			if(input != null){
				///tested
				long start2 = System.nanoTime();///start crypto benchmark
				cipherOutput = cipher.doFinal(input, inputOffset, inputLen);
				long time2 = System.nanoTime()-start2;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time2);
			}
			else{
				///tested
				long start2 = System.nanoTime();///start crypto benchmark
				cipherOutput = cipher.doFinal();
				long time2 = System.nanoTime()-start2;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time2);
			}
			this.reset();
			return cipherOutput;
		default:
			return null;
		}
	}

	@Override
	protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
			throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		int r;
		ByteBuffer bb;
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			cbir.update(input, inputOffset, inputLen);
			int cbir_length = cbir.getMacLength();
			int total = 5 + cbir_length + cipher.getOutputSize(inputLen);
			if(total > output.length-outputOffset){
				/*
				 * Known bugs: cbir sparse will provide an incorrect feature extraction time
				 * if this happens 
				 */
				cbir.reset();
				cbir.update(buffer, 0, bufferCount);
				throw new ShortBufferException();
			}
			cbir.doFinal(output, outputOffset + 5);
			long start = System.nanoTime();///start crypto benchmark
			cipher.doFinal(input, inputOffset, inputLen, output, outputOffset + 
				5 + cbir_length);
			long time = System.nanoTime()-start;///end crypto benchmark
			TimeSpec.addSymmetricEncryptionTime(time);
			start = System.nanoTime();///start misc benchmark
			bb = ByteBuffer.wrap(output, outputOffset, output.length - outputOffset);
			bb.put(Utils.FULL_CIPHER);
			bb.putInt(cbir_length);
			time = System.nanoTime()-start;///end misc benchmark
			TimeSpec.addMiscEncryptionTime(time);
			this.reset();
			return total;
		case(Cipher.DECRYPT_MODE):
			if(input != null){
				///tested
				long start2 = System.nanoTime();///start crypto benchmark
				r = cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
				long time2 = System.nanoTime()-start2;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time2);
			}
			else{
				///tested
				long start2 = System.nanoTime();///start crypto benchmark
				r = cipher.doFinal(output, outputOffset);
				long time2 = System.nanoTime()-start2;///end crypto benchmark
				TimeSpec.addSymmetricEncryptionTime(time2);
			}
			this.reset();
			return r;
		default:
			return 0;
		}
	}

	@Override
	protected int engineGetBlockSize() {
		return cipher.getBlockSize();
	}

	@Override
	protected byte[] engineGetIV() {
		return cipher.getIV();
	}

	@Override
	protected int engineGetOutputSize(int inputLen) {
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			return cbir.getMacLength()+cipher.getOutputSize(inputLen);
		case(Cipher.DECRYPT_MODE):
			return cipher.getOutputSize(inputLen);
		default:
			return 0;
		}
	}

	@Override
	protected AlgorithmParameters engineGetParameters() {
		String instance = cbirMode+"WithSymmetricCipher";
		try {
			AlgorithmParameters p = AlgorithmParameters.getInstance(instance);
			p.init(cryptoParams);
			return p;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidParameterSpecException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected void init(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
			throws InvalidKeyException, InvalidAlgorithmParameterException {
		if(key == null || random == null)
			throw new NullPointerException();
		if(!(key instanceof CBIRCipherKey))
			throw new InvalidKeyException();
		this.opmode = opmode;
		if(params != null){
			if(params instanceof CBIRCipherParameterSpec){
				cryptoParams = (CBIRCipherParameterSpec)params;
			}
			else{
				try {
					if(cbirMode.equalsIgnoreCase("CBIRD")){
						cryptoParams = new CBIRDCipherParameterSpec(params);
					}
					else if (cbirMode.equalsIgnoreCase("CBIRS")){
						cryptoParams = new CBIRSCipherParameterSpec(params);
					}
				} catch (InvalidParameterSpecException e) {
					throw new InvalidAlgorithmParameterException(e.getMessage());
				}
			}
		}
	    cryptoKey = (CBIRCipherKey)key;
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			if(params == null){
				throw new InvalidAlgorithmParameterException();
			}
			AlgorithmParameters cbirParams = cryptoParams.getCBIRParameters();
			try {
				///init cbir
				cbir = Mac.getInstance(cbirParams.getAlgorithm(), cbirParams.getProvider());
				try {
					if(cbirMode.equalsIgnoreCase("CBIRD")){
						cbir.init(cryptoKey.getCBIRSpec(), cbirParams.getParameterSpec(CBIRDParameterSpec.class));
					}
					else if (cbirMode.equalsIgnoreCase("CBIRS")){
						cbir.init(cryptoKey.getCBIRSpec(), cbirParams.getParameterSpec(CBIRSParameterSpec.class));
					}
				} catch (InvalidParameterSpecException e) {
					throw new InvalidAlgorithmParameterException();
				}
				///init aes
				initCipher();
				AlgorithmParameters tmp = cryptoParams.getCipherParameters();
				if(tmp == null){
					tmp = cipher.getParameters();
					if(cbirMode.equalsIgnoreCase("CBIRD")){
						cryptoParams = new CBIRDCipherParameterSpec(cryptoParams.getCBIRParameters(), tmp, 
								cryptoParams.getCipherAlgorithm(), cryptoParams.getCipherMode(), cryptoParams.getCipherPadding(),
								cipher.getIV());
					}
					else if (cbirMode.equalsIgnoreCase("CBIRS")){
						cryptoParams = new CBIRSCipherParameterSpec(cryptoParams.getCBIRParameters(), tmp, 
								cryptoParams.getCipherAlgorithm(), cryptoParams.getCipherMode(), cryptoParams.getCipherPadding(),
								cipher.getIV());
					}
				}
			} catch (NoSuchAlgorithmException e) {
				///should never happen
				e.printStackTrace();
			}
			break;
		case(Cipher.DECRYPT_MODE):
			initCipher();
			break;
		case(Cipher.WRAP_MODE):
			break;
		case(Cipher.UNWRAP_MODE):
			break;
		default:
			throw new UnsupportedOperationException();
		}
		buffer = new byte[50];
		bufferCount = 0;
	}

	@Override
	protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
		String alg, padding;
		alg = defaultSpec.getCipherAlgorithm();
		padding = defaultSpec.getCipherPadding();
		String transformation = alg+"/"+mode+"/"+padding;
		try {
			try{
				Cipher.getInstance(transformation, "BC");
			} catch (NoSuchProviderException e) {
				Cipher.getInstance(transformation);
			}
			defaultSpec = new CBIRDCipherParameterSpec(transformation);
		} catch (NoSuchPaddingException e) {
			///Silently ignore as it might be changed afterwards
		}
	}

	@Override
	protected void engineSetPadding(String padding) throws NoSuchPaddingException {
		String alg, mode;
		alg = defaultSpec.getCipherAlgorithm();
		mode = defaultSpec.getCipherMode();
		String transformation = alg+"/"+mode+"/"+padding;
		try {
			try{
				Cipher.getInstance(transformation, "BC");
			} catch (NoSuchProviderException e) {
				Cipher.getInstance(transformation);
			}
			defaultSpec = new CBIRDCipherParameterSpec(transformation);
		} catch (NoSuchAlgorithmException e) {
			///Silently ignore as it might be changed afterwards
		}
	}

	@Override
	protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			///tested
			updateBuffer(input, inputOffset, inputLen);
			cbir.update(input, inputOffset, inputLen);
			long start = System.nanoTime();///start crypto benchmark
			byte[] buffer = cipher.update(input, inputOffset, inputLen); 
			long time = System.nanoTime()-start;///end crypto benchmark
			TimeSpec.addSymmetricEncryptionTime(time);
			return buffer;
		case(Cipher.DECRYPT_MODE):
			long start2 = System.nanoTime();///start crypto benchmark
			byte[] buffer2 = cipher.update(input, inputOffset, inputLen);
			long time2 = System.nanoTime()-start2;///end crypto benchmark
			TimeSpec.addSymmetricEncryptionTime(time2);
			return buffer2;
		default:
			return null;
		}
	}

	@Override
	protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
		int r;
		switch(opmode){
		case(Cipher.ENCRYPT_MODE):
			long start = System.nanoTime();///start crypto benchmark
			r = cipher.update(input, inputOffset, inputLen, output, outputOffset);
			long time = System.nanoTime()-start;///end crypto benchmark
			TimeSpec.addSymmetricEncryptionTime(time);
			updateBuffer(input, inputOffset, inputLen);
			cbir.update(input, inputOffset, inputLen);
			return r;
		case(Cipher.DECRYPT_MODE):
			long start2 = System.nanoTime();///start crypto benchmark
			r = cipher.update(input, inputOffset, inputLen, output, outputOffset);
			long time2 = System.nanoTime()-start2;///end crypto benchmark
			TimeSpec.addSymmetricEncryptionTime(time2);
			return r;
		default:
			return 0;
		}
	}
	
	private void updateBuffer(byte[] input, int inputOffset, int inputLen){
		while(inputLen > buffer.length-bufferCount){
			byte[] tmp = new byte[buffer.length*2];
			System.arraycopy(buffer, 0, tmp, 0, bufferCount);
			buffer = tmp;
		}
		System.arraycopy(input, inputOffset, buffer, bufferCount, inputLen);
		bufferCount+=inputLen;
	}
	
	private void reset(){
		buffer = new byte[50];
		bufferCount = 0;
		try {
			initCipher();
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			// should not be happen
			e.printStackTrace();
		}
		if(opmode == Cipher.ENCRYPT_MODE){
			cbir.reset();
		}
	}
	
	private void initCipher() throws InvalidKeyException, InvalidAlgorithmParameterException{
		String alg, mode, padding;
		AlgorithmParameters param = null;
		byte[] iv_bytes = null;
		if(cryptoParams != null){
			mode = cryptoParams.getCipherMode();
			padding = cryptoParams.getCipherPadding();
			param = cryptoParams.getCipherParameters();
			alg = cryptoParams.getCipherAlgorithm();
			iv_bytes = cryptoParams.getIv();
		}
		else{
			mode = defaultSpec.getCipherMode();
			padding = defaultSpec.getCipherPadding();
			alg = defaultSpec.getCipherAlgorithm();
		}
		String transformation = alg+"/"+mode+"/"+padding;
		try {
			try {
				cipher = Cipher.getInstance(transformation, "BC");
			} catch (NoSuchProviderException e) {
				cipher = Cipher.getInstance(transformation);
			}
			if(cryptoParams != null && !mode.equalsIgnoreCase("ECB")){
				if(param != null){
					cipher.init(opmode, cryptoKey.getCipherSpec(), param);
				}
				else{
					if(iv_bytes != null){
						IvParameterSpec iv = new IvParameterSpec(iv_bytes);
						cipher.init(opmode, cryptoKey.getCipherSpec(), iv);
					}
					else{
						cipher.init(opmode, cryptoKey.getCipherSpec());
					}
				}
			}
			else{
				try{
					cipher.init(opmode, cryptoKey.getCipherSpec());
				}catch (InvalidKeyException e){
					if(param != null){
						cipher.init(opmode, cryptoKey.getCipherSpec(), param);
					}
					else if(iv_bytes != null){
						IvParameterSpec iv = new IvParameterSpec(iv_bytes);
						cipher.init(opmode, cryptoKey.getCipherSpec(), iv);
					}
				}
			}
		}catch (NoSuchPaddingException e) {
			e.printStackTrace();
			cipher = null;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			cipher = null;
		}
	}
}
