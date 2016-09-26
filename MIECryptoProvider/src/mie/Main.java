package mie;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.MessagingException;

import mie.crypto.CBIRDKeySpec;
import mie.crypto.CBIRDParameterSpec;
import mie.crypto.CBIRDCipherParameterSpec;

public class Main {

	public static void main(String[] args) throws IOException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidKeySpecException, InvalidAlgorithmParameterException, MessagingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidParameterSpecException, ShortBufferException, NoSuchProviderException {
		//listServices("Cipher");
		testMIEImage();
		testMIEText();
		testCBIR();
		//testCipher();
		testCipherParams("");
		testCipherParams("DES/OFB/PKCS5Padding");
		testCipherParams("TEA/CFB/ISO10126Padding");
		testCipherParams("IDEA/ECB/ISO7816-4Padding");
		testCipherParams("RC5/OpenPGPCFB/X9.23Padding");
		testCipherParams("SEED/CTS/TBCPadding");
		testCipherParams("Skipjack/GOFB/ZeroBytePadding");
		testCipherParams("Serpent/CBC/ZeroBytePadding");
		
		testCipherParams("RC4/ECB/NoPadding");
		//testCipherParams("Salsa20/ECB/NoPadding");
		//testCipherParams("AES/GCFB/PKCS7Padding");
	}
	
	private static void listServices(String service){
		Provider p = Security.getProvider("BC");
		if(p != null){
			Set<Service> services = p.getServices();
			if(services.isEmpty()){
				System.out.println("No services of type "+service+" found");
			}
			else{
				System.out.println("Listing services of type: "+service);
				for(Service s: services){
					if(s.getType().equalsIgnoreCase(service)){
						System.out.println(s.getAlgorithm());
					}
				}
			}
		}
		else{
			System.out.println("Provider not found");
		}
	}
	
	private static void testCipher() throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, InvalidParameterSpecException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
		String cipherAlgorithm = "AES";
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] img_buffer = new byte[in.available()];
		in.read(img_buffer);
		in.close();
		///generate parameters
		/*AlgorithmParameterGenerator apg = AlgorithmParameterGenerator.getInstance(cipherAlgorithm, "BC");
		AlgorithmParameters cipherParams = apg.generateParameters();*/
		///generate key
		KeyGenerator keyGen = KeyGenerator.getInstance(cipherAlgorithm, "BC");
		keyGen.init(128);
		Key key = keyGen.generateKey();
		Cipher cipher = Cipher.getInstance(cipherAlgorithm+"/GCFB/PKCS7Padding");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] cipher_buffer = cipher.update(img_buffer);
		byte[] f = cipher.doFinal();
		AlgorithmParameters p = cipher.getParameters();
		IvParameterSpec iv = new IvParameterSpec(cipher.getIV());
		cipher = Cipher.getInstance(cipherAlgorithm+"/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, p);
		byte[] end_buffer = cipher.doFinal(cipher_buffer);
		
	}
	
	private static void testCipherParams(String transformation) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException{
		String toTest;
		if(transformation.equals("")){
			toTest = "AES/CTR/PKCS7Padding";
		}
		else{
			toTest = transformation;
		}
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] img_buffer = new byte[in.available()];
		in.read(img_buffer);
		in.close();
		///read text
		in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags0.txt");
		byte[] txt_buffer = new byte[in.available()];
		in.read(txt_buffer);
		in.close();
		///test image cipher
		///choose symmetric cipher
		System.out.println(toTest);
		AlgorithmParameterSpec cipherSpec = new CBIRDCipherParameterSpec(toTest);
		///generate cipher key for image
		SecureRandom random = new SecureRandom();
		KeyGenerator keyGen = KeyGenerator.getInstance("CBIRDWithSymmetricCipher");
		keyGen.init(cipherSpec, random);
		Key key = keyGen.generateKey();
		///instatiate cipher
		Cipher cipher = Cipher.getInstance("CBIRDWithSymmetricCipher");
		cipher.init(Cipher.ENCRYPT_MODE, key, cipherSpec);
		///get cipher text
		byte[] cipher_buffer = cipher.doFinal(img_buffer);
		///split cbir from cipher text
		ByteBuffer bb = ByteBuffer.wrap(cipher_buffer);
		bb.position(1);
		int l = bb.getInt();
		bb.position(l+5);
		byte[] cipher_text = new byte[bb.remaining()];
		bb.get(cipher_text);
		///get new cipher instance to decrypt
		AlgorithmParameters p = cipher.getParameters();
		cipher = Cipher.getInstance("CBIRDWithSymmetricCipher");
		cipher.init(Cipher.DECRYPT_MODE, key, p);
		byte[] new_img = cipher.doFinal(cipher_text);
		///compare buffers
		if(compareArrays(img_buffer, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static boolean compareArrays(byte[] array1, byte[] array2){
		if(array1 != null && array2 != null){
			if(array1.length != array2.length){
				return false;
			}
			else{
				for(int i  = 0; i < array1.length; i++){
					if(array1[i] != array2[i]){
						return false;
					}
				}
			}
			return true;
		}
		else if(array1 == null && array2 == null){
			return true;
		}
		return false;
	}
	
	private static void testMIEText() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException{
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags0.txt");
		byte[] txt_buffer = new byte[in.available()];
		in.read(txt_buffer);
		in.close();
		///generate cipher key
		SecureRandom random = new SecureRandom();
		KeyGenerator keyGen = KeyGenerator.getInstance("CBIRSWithSymmetricCipher");
		keyGen.init(random);
		Key img_key = keyGen.generateKey();
		///Instantiate cipher
		byte[] img_cipher_buffer;
		Cipher img_cipher = Cipher.getInstance("CBIRSWithSymmetricCipher");
		byte[] iv = new byte[img_cipher.getBlockSize()];
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		img_cipher_buffer = testEncryptSingleFinal("CBIRSWithSymmetricCipher",txt_buffer, img_key, ivSpec);
		System.out.printf("EncryptSingleFinal: ");
		testDecryptSingleDoFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		
		img_cipher_buffer = testEncryptUpdateFinal("CBIRSWithSymmetricCipher",txt_buffer, img_key, ivSpec);
		System.out.printf("EncryptUpdateFinal: ");
		testDecryptSingleDoFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		
		img_cipher_buffer = testEncryptSelectedMultiUpdateFinal("CBIRSWithSymmetricCipher",txt_buffer, img_key, ivSpec);
		System.out.printf("EncryptSelectedMultiUpdateFinal: ");
		testDecryptSingleDoFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		///test decrypt
		System.out.printf("SingleDoFinal: ");
		testDecryptSingleDoFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SingleUpdateFinal: ");
		testDecryptSingleUpdateFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("MultiUpdate: ");
		testDecryptMultiUpdate("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal: ");
		testDecryptSelectedUpdateFinal("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal2: ");
		testDecryptSelectedUpdateFinal2("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal3: ");
		testDecryptSelectedUpdateFinal3("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		
		System.out.printf("SingleDoFinalOutput: ");
		testDecryptSingleDoFinalOutput("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput1: ");
		testDecryptSelectedUpdateFinalOutput1("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput2: ");
		testDecryptSelectedUpdateFinalOutput2("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput3: ");
		testDecryptSelectedUpdateFinalOutput3("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("UpdateFinalOutput: ");
		testDecryptUpdateFinalOutput("CBIRSWithSymmetricCipher",txt_buffer, img_cipher_buffer, img_key, ivSpec);
	}
	
	private static void testMIEImage() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException{
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] img_buffer = new byte[in.available()];
		in.read(img_buffer);
		in.close();
		///generate cipher key
		SecureRandom random = new SecureRandom();
		KeyGenerator keyGen = KeyGenerator.getInstance("CBIRDWithSymmetricCipher");
		keyGen.init(random);
		Key img_key = keyGen.generateKey();
		///instatiate cipher
		byte[] img_cipher_buffer;
		Cipher img_cipher = Cipher.getInstance("CBIRDWithSymmetricCipher");
		byte[] iv = new byte[img_cipher.getBlockSize()];
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		img_cipher_buffer = testEncryptSingleFinal("CBIRDWithSymmetricCipher",img_buffer, img_key, ivSpec);
		System.out.printf("EncryptSingleFinal: ");
		testDecryptSingleDoFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		
		img_cipher_buffer = testEncryptUpdateFinal("CBIRDWithSymmetricCipher",img_buffer, img_key, ivSpec);
		System.out.printf("EncryptUpdateFinal: ");
		testDecryptSingleDoFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		
		img_cipher_buffer = testEncryptSelectedMultiUpdateFinal("CBIRDWithSymmetricCipher",img_buffer, img_key, ivSpec);
		System.out.printf("EncryptSelectedMultiUpdateFinal: ");
		testDecryptSingleDoFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		///test decrypt
		System.out.printf("SingleDoFinal: ");
		testDecryptSingleDoFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SingleUpdateFinal: ");
		testDecryptSingleUpdateFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("MultiUpdate: ");
		testDecryptMultiUpdate("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal: ");
		testDecryptSelectedUpdateFinal("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal2: ");
		testDecryptSelectedUpdateFinal2("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinal3: ");
		testDecryptSelectedUpdateFinal3("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		
		System.out.printf("SingleDoFinalOutput: ");
		testDecryptSingleDoFinalOutput("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput1: ");
		testDecryptSelectedUpdateFinalOutput1("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput2: ");
		testDecryptSelectedUpdateFinalOutput2("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("SelectedUpdateFinalOutput3: ");
		testDecryptSelectedUpdateFinalOutput3("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
		System.out.printf("UpdateFinalOutput: ");
		testDecryptUpdateFinalOutput("CBIRDWithSymmetricCipher",img_buffer, img_cipher_buffer, img_key, ivSpec);
	}
	
	private static byte[] testEncryptSingleFinal(String instance, byte[] plain_text, Key key, AlgorithmParameterSpec params) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		///instatiate cipher
		Cipher img_cipher = Cipher.getInstance(instance);
		img_cipher.init(Cipher.ENCRYPT_MODE, key, params);
		byte[] img_cipher_buffer = img_cipher.doFinal(plain_text);
		ByteBuffer bb = ByteBuffer.wrap(img_cipher_buffer, 0, 5);
		bb.position(1);
		int t = bb.getInt();
		byte[] fin = new byte[img_cipher_buffer.length-5-t];
		System.arraycopy(img_cipher_buffer, t+5, fin, 0, img_cipher_buffer.length-t-5);
		return fin;
	}
	
	private static byte[] testEncryptUpdateFinal(String instance, byte[] plain_text, Key key, AlgorithmParameterSpec params) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		///instatiate cipher
		Cipher img_cipher = Cipher.getInstance(instance);
		img_cipher.init(Cipher.ENCRYPT_MODE, key, params);
		byte[] tmp1 = img_cipher.update(plain_text);
		byte[] tmp2 = img_cipher.doFinal();
		ByteBuffer bb = ByteBuffer.wrap(tmp2, 0, 5);
		bb.position(1);
		int t = bb.getInt()+5;
		byte[] img_cipher_buffer = new byte[tmp1.length+tmp2.length];
		System.arraycopy(tmp2, 0, img_cipher_buffer, 0, t);
		System.arraycopy(tmp1, 0, img_cipher_buffer, t, tmp1.length);
		System.arraycopy(tmp2, t, img_cipher_buffer, tmp1.length+t, tmp2.length-t);
		byte[] fin = new byte[img_cipher_buffer.length-t];
		System.arraycopy(img_cipher_buffer, t, fin, 0, img_cipher_buffer.length-t);
		return fin;
	}
	
	private static byte[] testEncryptSelectedMultiUpdateFinal(String instance, byte[] plain_text, Key key, AlgorithmParameterSpec params) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		///instatiate cipher
		Cipher img_cipher = Cipher.getInstance(instance);
		img_cipher.init(Cipher.ENCRYPT_MODE, key, params);
		int select = plain_text.length/3;
		byte[] tmp1 = img_cipher.update(plain_text, 0, select);
		byte[] tmp2 = img_cipher.update(plain_text, select, select);
		byte[] tmp3 = img_cipher.doFinal(plain_text, select*2, plain_text.length-select*2);
		ByteBuffer bb = ByteBuffer.wrap(tmp3, 0, 5);
		bb.position(1);
		int t = bb.getInt()+5;
		byte[] img_cipher_buffer = new byte[tmp1.length+tmp2.length+tmp3.length];
		System.arraycopy(tmp3, 0, img_cipher_buffer, 0, t);
		System.arraycopy(tmp1, 0, img_cipher_buffer, t, tmp1.length);
		System.arraycopy(tmp2, 0, img_cipher_buffer, t+tmp1.length, tmp2.length);
		System.arraycopy(tmp3, t, img_cipher_buffer, t+tmp1.length+tmp2.length, tmp3.length-t);
		byte[] fin = new byte[img_cipher_buffer.length-t];
		System.arraycopy(img_cipher_buffer, t, fin, 0, img_cipher_buffer.length-t);
		return fin;
	}
	
	private static void testDecryptSingleDoFinalOutput(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ShortBufferException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] end_buffer = new byte[4000000];
		int n = img_decipher.doFinal(cipher_text, 0, cipher_text.length, end_buffer, 0);
		byte[] new_img = new byte[n];
		System.arraycopy(end_buffer, 0, new_img, 0, n);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinalOutput1(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ShortBufferException{
		/*ByteBuffer bb = ByteBuffer.wrap(cipher_text, 0, 4);
		int threshold = bb.getInt()+4;*/
		int threshold = cipher_text.length/3;
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		int block = img_decipher.getBlockSize();
		
		int select = 2;
		byte[] end_buffer = new byte[4000000];
		int n = 0;
		int t = img_decipher.update(cipher_text, 0, select, end_buffer);
		n = t;
		
		int select2 = 5;
		int offset = select;
		t = img_decipher.update(cipher_text, offset, select2, end_buffer, n);
		n+=t;
		
		offset+=select2;
		t = img_decipher.update(cipher_text, offset, threshold, end_buffer, n);
		n+=t;
		
		offset+=threshold;
		t = img_decipher.update(cipher_text, offset, block, end_buffer, n);
		n+=t;
		
		offset+=block;
		t = img_decipher.doFinal(cipher_text, offset, cipher_text.length-offset, end_buffer, n);
		n+=t;
		byte[] new_img = new byte[n];
		System.arraycopy(end_buffer, 0, new_img, 0, n);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinalOutput2(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ShortBufferException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		int select = 2;
		byte[] end_buffer = new byte[4000000];
		int n = 0;
		int t = img_decipher.update(cipher_text, 0, select, end_buffer);
		n = t;
		int select2 = 5;
		t = img_decipher.update(cipher_text, select, select2, end_buffer, n);
		n+=t;
		t = img_decipher.doFinal(cipher_text, select+select2, cipher_text.length-select-select2, end_buffer, n);
		n+=t;
		byte[] new_img = new byte[n];
		System.arraycopy(end_buffer, 0, new_img, 0, n);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinalOutput3(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ShortBufferException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		int select = 2;
		byte[] end_buffer = new byte[4000000];
		int n = 0;
		int t = img_decipher.update(cipher_text, 0, select, end_buffer);
		n = t;
		t = img_decipher.doFinal(cipher_text, select, cipher_text.length-select, end_buffer, n);
		n+=t;
		byte[] new_img = new byte[n];
		System.arraycopy(end_buffer, 0, new_img, 0, n);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptUpdateFinalOutput(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ShortBufferException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] end_buffer = new byte[4000000];
		int n = img_decipher.update(cipher_text, 0, cipher_text.length, end_buffer);
		int t = img_decipher.doFinal(end_buffer, n);
		n+=t;
		byte[] new_img = new byte[n];
		System.arraycopy(end_buffer, 0, new_img, 0, n);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSingleDoFinal(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] new_img = img_decipher.doFinal(cipher_text);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinal(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		/*ByteBuffer bb = ByteBuffer.wrap(cipher_text, 0, 4);
		int select = bb.getInt()+4;*/
		int select = cipher_text.length/2;
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] buffer = new byte[img_decipher.getOutputSize(cipher_text.length)];
		int offset = 0;
		for(int i = 0; i < select; i++){
			byte[] tmp = new byte[1];
			tmp[0] = cipher_text[i];
			byte[] o = img_decipher.update(tmp);
			if(o != null){
				System.arraycopy(o, 0, buffer, offset, o.length);
				offset += o.length;
			}
		}
		byte[] bb = img_decipher.doFinal(cipher_text, select, cipher_text.length-select);
		byte[] new_img = new byte[bb.length + offset];
		System.arraycopy(buffer, 0, new_img, 0, offset);
		System.arraycopy(bb, 0, new_img, offset, bb.length);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinal2(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		/*ByteBuffer bb = ByteBuffer.wrap(cipher_text, 0, 4);
		int select = bb.getInt()+4;*/
		int select = 0;
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		select+=img_decipher.getBlockSize()+1;
		byte[] tmp1 = img_decipher.update(cipher_text, 0, select);
		byte[] tmp2 = img_decipher.doFinal(cipher_text, select, cipher_text.length-select);
		byte[] new_img = new byte[tmp1.length+tmp2.length];
		System.arraycopy(tmp1, 0, new_img, 0, tmp1.length);
		System.arraycopy(tmp2, 0, new_img, tmp1.length, tmp2.length);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSelectedUpdateFinal3(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		int select = 2;
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		img_decipher.update(cipher_text, 0, select);
		byte[] new_img = img_decipher.doFinal(cipher_text, select, cipher_text.length-select);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptMultiUpdate(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] tmp1 = new byte[1000];
		int tmp1_count = 0;
		for(int i = 0; i < cipher_text.length; i++){
			byte[] tmp2 = new byte[1];
			tmp2[0] = cipher_text[i];
			byte[] tmp3 = img_decipher.update(tmp2);
			if(tmp3 != null){
				while(tmp3.length > tmp1.length-tmp1_count){
					byte[] tmp4 = new byte[tmp1.length*2];
					System.arraycopy(tmp1, 0, tmp4, 0, tmp1_count);
					tmp1 = tmp4;
				}
				System.arraycopy(tmp3, 0, tmp1, tmp1_count, tmp3.length);
				tmp1_count+=tmp3.length;
			}
		}
		byte[] tmp5 = img_decipher.doFinal();
		byte[] new_img = new byte[tmp1_count+tmp5.length];
		System.arraycopy(tmp1, 0, new_img, 0, tmp1_count);
		System.arraycopy(tmp5, 0, new_img, tmp1_count, tmp5.length);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testDecryptSingleUpdateFinal(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] tmp1 = img_decipher.update(cipher_text, 0, cipher_text.length);
		byte[] tmp2 = img_decipher.doFinal();
		byte[] new_img = new byte[tmp1.length+tmp2.length];
		System.arraycopy(tmp1, 0, new_img, 0, tmp1.length);
		System.arraycopy(tmp2, 0, new_img, tmp1.length, tmp2.length);
		///compare buffers
		if(compareArrays(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	private static void testCBIR() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidParameterSpecException, InvalidAlgorithmParameterException{
		/**************CIPHER TEXT DOCUMENT*********************/
		///read text document
		FileInputStream tin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags0.txt");
		byte[] tb = new byte[tin.available()];
		tin.read(tb);
		tin.close();
		///read text key
		FileInputStream tkey = new FileInputStream("/home/johndoe/MIE/Data/Client/MIE/textKey");
		byte[] tkeyb = new byte[tkey.available()];
		tkey.read(tkeyb);
		tkey.close();
		///get cipher instance
		Mac sparse = Mac.getInstance("CBIRS");
		///construct key
		SecretKeySpec ks = new SecretKeySpec(tkeyb, "CBIRS");
		SecretKeyFactory skeyf = SecretKeyFactory.getInstance("CBIRS");
		SecretKey skey = skeyf.generateSecret(ks);
		///init cipher
		sparse.init(skey);
		sparse.update(tb, 0, tb.length);
		byte[] enc = sparse.doFinal();
		FileOutputStream tout = new FileOutputStream("/home/johndoe/tout-java");
		tout.write(enc);
		tout.close();
		/************END CIPHER TEXT DOCUMENT*******************/
		/*byte[] b = "catness".getBytes();
		PorterStemmer stemmer = new PorterStemmer();
		int j = stemmer.stem(b, 0, b.length);
		System.out.println(j);*/
		/***************CIPHER IMG DOCUMENT*********************/
		///read key
		byte[] key = null;
		FileInputStream sin = new FileInputStream("/home/johndoe/MIE/Data/Client/MIE/sbeKey");
		byte[] bb = new byte[sin.available()];
		sin.read(bb);
		ByteBuffer bt = ByteBuffer.wrap(bb).order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer btout = ByteBuffer.allocate(bt.capacity());
		while(bt.hasRemaining()){
			btout.putFloat(bt.getFloat());
		}
		key = btout.array();
		sin.close();
		/*for(int i = 0; i < 4; i++)
			System.out.printf("%x ", key[i]);
		System.out.println("");*/
		AlgorithmParameterGenerator apg = AlgorithmParameterGenerator.getInstance("CBIRD");
		AlgorithmParameters ap = apg.generateParameters();
		CBIRDParameterSpec paramSpec = ap.getParameterSpec(CBIRDParameterSpec.class);
		CBIRDKeySpec keySpec = new CBIRDKeySpec(key, paramSpec.getM(), paramSpec.getK());
		SecretKeyFactory fact = SecretKeyFactory.getInstance("CBIRD");
		SecretKey dkey = fact.generateSecret(keySpec);
		///init cipher
		Mac mac = Mac.getInstance("CBIRD");
		mac.init(dkey, paramSpec);
		///read file
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		mac.update(buffer, 0, buffer.length);
		FileWriter out = new FileWriter("/home/johndoe/encoded-java");
		enc = mac.doFinal();
		//System.out.println(enc.length);
		ByteBuffer bb2 = ByteBuffer.wrap(enc);
		bb2.position(1);
		int rows = bb2.getInt();
		int m = bb2.getInt();
		float[][] encoded_float = new float[rows][m];
		for(int i = 0; i < rows; i++){
			for(int j = 0; j < m; j++){
				encoded_float[i][j] =  bb2.getFloat();
				out.write((int)encoded_float[i][j]+" ");
			}
			out.write("\n");
		}
		out.close();
		in.close();
		/************END CIPHER TEXT DOCUMENT*******************/
	}

}
