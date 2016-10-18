package mie.utils;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import mie.crypto.Utils;

public class CBIRCipherText{

	private float[][] cbirDFeatures;
	private byte[][] cbirSFeatures; 
	private byte[] cipherText;
	
	public CBIRCipherText(byte[] cipher_text) throws UnrecognizedFormatException{
		cbirDFeatures = null;
		cbirSFeatures = null;
		cipherText = null;
		parseBuffer(cipher_text, 0, cipher_text.length);
	}
	
	private void parseBuffer(byte[] cipher_text, int offset, int length) throws UnrecognizedFormatException{
		switch(cipher_text[offset]){
		case(Utils.CBIRD):
			readCBIRDCipher(cipher_text, offset+1, length-1);
			break;
		case(Utils.CBIRS):
			readCBIRSCipher(cipher_text, offset+1, length-1);
			break;
		case(Utils.FULL_CIPHER):
			readFullCipher(cipher_text, offset+1, length-1);
			break;
		default:
			throw new UnrecognizedFormatException();
		}
	}
	
	private void readCBIRDCipher(byte[] cipher_text, int offset, int length) throws UnrecognizedFormatException{
		ByteBuffer buffer = ByteBuffer.wrap(cipher_text, offset, length);
		try{
			int rows = buffer.getInt();
			int m = buffer.getInt();
			cbirDFeatures = new float[rows][m];
			for(int i = 0; i < rows; i++){
				for(int j = 0; j < m; j++){
					cbirDFeatures[i][j] = buffer.getFloat();
				}
			}
		} catch(BufferUnderflowException e){
			throw new UnrecognizedFormatException("Cipher text is not a CBIR Dense cipher text or is corrupted");
		}
	}
	
	private void readCBIRSCipher(byte[] cipher_text, int offset, int length) throws UnrecognizedFormatException{
		ByteBuffer buffer = ByteBuffer.wrap(cipher_text, offset, length);
		try{
			int n_words = buffer.getInt();
			int word_size = buffer.getInt();
			cbirSFeatures = new byte[n_words][word_size];
			for(int i = 0; i < n_words; i++){
				for(int j = 0; j < word_size; j++){
					cbirSFeatures[i][j] = buffer.get();
				}
			}
		} catch(BufferUnderflowException e){
			throw new UnrecognizedFormatException("Cipher text is not a CBIR Sparse cipher text or is corrupted");
		}
	}
	
	private void readFullCipher(byte[] cipher_text, int offset, int length) throws UnrecognizedFormatException{
		ByteBuffer buffer = ByteBuffer.wrap(cipher_text, offset, length);
		try{
			int cbir_length = buffer.getInt();
			parseBuffer(cipher_text, offset+4, cbir_length);
			int cipher_length = length-4-cbir_length;
			cipherText = new byte[cipher_length];
			System.arraycopy(cipher_text, offset+4+cbir_length, cipherText, 0, cipher_length);
		} catch(BufferUnderflowException e){
			throw new UnrecognizedFormatException("Cipher text is not a CBIR cipher text or is corrupted");
		}
	}
	
	public int getCBIRLength(){
		int length = 0;
		if(cbirDFeatures != null && cbirDFeatures.length > 0){
			length += cbirDFeatures.length*cbirDFeatures[0].length*4;
		}
		if(cbirSFeatures != null && cbirSFeatures.length > 0){
			length += cbirSFeatures.length*cbirSFeatures[0].length;
		}
		return length;
	}
	
	public float[][] getImgFeatures(){
		return cbirDFeatures;
	}
	
	public byte[][] getTextFeatures(){
		return cbirSFeatures;
	}
	
	public byte[] getCipherText(){
		return cipherText;
	}

}
