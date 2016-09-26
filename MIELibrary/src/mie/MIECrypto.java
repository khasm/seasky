package mie;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public interface MIECrypto {
	
	public byte[] encryptImg(byte[] plain_text) throws IllegalBlockSizeException;
	
	public byte[] decryptImg(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException;
	
	public byte[] cbirImg(byte[] img);
	
	public byte[] encryptTxt(byte[] plain_text) throws IllegalBlockSizeException;
	
	public byte[] decryptTxt(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException;
	
	public byte[] cbirTxt(byte[] txt);
	
	public byte[] encryptMime(byte[] plain_text) throws IllegalBlockSizeException;
	
	public byte[] decryptMime(byte[] cipher_text) throws IllegalBlockSizeException, BadPaddingException;
	
	public int getImgFeatureSize();
	
	public int getKeywordSize();

}
