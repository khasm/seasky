package mie;

import java.io.ByteArrayInputStream;
import java.io.File;
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
import java.security.SecureRandom;
import java.security.Provider.Service;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;
import org.opencv.highgui.Highgui;

import mie.crypto.CBIRDCipherKeySpec;
import mie.crypto.CBIRDKeySpec;
import mie.crypto.CBIRDParameterSpec;
import mie.crypto.CBIRSCipherKeySpec;
import mie.crypto.CBIRSKeySpec;
import mie.utils.CBIRCipherText;
import mie.utils.UnrecognizedFormatException;

public class MainTests {

	//public static void main(String[] args) throws IOException, MessagingException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidParameterSpecException, InvalidKeySpecException, UnrecognizedFormatException {
		/*Provider p = Security.getProvider("MIE");
		if(p == null){
			System.out.println("Not found");
		}
		else
			System.out.println("Found");
		listServices(p);
		testMie();*/
		//testZLib();
		//testClientUnstructured();
		//testClientMime();
		//testClientDMime();
		//testMime();
		//switchkeys();
		//testKeys();
		//imgHeaders();
		//setupMimeDataset();
		//setupDMimeDataset();
		//testCBIRObject();
		//testDMime();
		//checkMimes();
	//}
	
	/*private static void testDMime() throws IOException, MessagingException{
		MIEClient mie = new MIEClient();
		for(int i = 0; i < 1000 ; i++){
			byte[] mime = readDMime(""+i);
			Map<String, byte[]> cbir_features = mie.processMimeDocument(mime);
			System.out.println("id: "+i);
			for(String key: cbir_features.keySet())
				System.out.println(key);
		}
	}*/
	
	@SuppressWarnings("unused")
	private static void checkMimes() throws IOException, MessagingException{
		File dir = new File("/home/johndoe/MIE/Datasets/maildir");
		Map<String, Integer> types = checkDir(dir);
		for(String key: types.keySet()){
			System.out.println(key+":\t\t\t\t"+types.get(key));
		}
	}
	
	private static Map<String,Integer> checkDir(File dir) throws IOException, MessagingException{
		File[] list = dir.listFiles();
		//System.out.println("Checking folder "+dir.getAbsolutePath()+": "+list.length);
		Map<String,Integer> types = new HashMap<String,Integer>();
		Map<String,Integer> ret = null;
		for(File file: list){
			if(file.isDirectory()){
				ret = checkDir(file);
			}
			else if(file.isFile()){
				ret = checkFile(file);
			}
			for(String key: ret.keySet()){
				if(types.containsKey(key)){
					types.put(key, types.get(key)+ret.get(key));
				}
				else{
					types.put(key, ret.get(key));
				}
			}
		}
		return types;
	}
	
	private static Map<String,Integer> checkFile(File file) throws IOException, MessagingException{
		//System.out.println("\tChecking file "+file.getAbsolutePath());
		Map<String,Integer> types = new HashMap<String,Integer>();
		FileInputStream in = new FileInputStream(file);
		byte[] buffer = new byte[in.available()];
		ByteArrayInputStream din = new ByteArrayInputStream(buffer);
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s, din);
		String contentType = msg.getContentType();
		if(types.containsKey(contentType)){
			types.put(contentType, types.get(contentType)+1);
		}
		else{
			types.put(contentType, 1);
		}
		if(contentType.startsWith("multipart")){
			try {
				Multipart mp = (Multipart) msg.getContent();
				//System.out.println("bodycount: "+mp.getCount());
				Map<String, Integer> ret = processMultipart(mp);
				for(String key: ret.keySet()){
					if(types.containsKey(key)){
						types.put(key, types.get(key)+ret.get(key));
					}
					else{
						types.put(key, ret.get(key));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		in.close();
		return types;
	}
	
	private static Map<String, Integer> processMultipart(Multipart mp) throws MessagingException, IOException{
		Map<String, Integer> types = new HashMap<String, Integer>();
		for(int j = 0; j < mp.getCount(); j++){
			MimeBodyPart body = (MimeBodyPart) mp.getBodyPart(j);
			String type = body.getContentType();
			if(types.containsKey(type)){
				types.put(type, types.get(type)+1);
			}
			else{
				types.put(type, 1);
			}
			if(type.startsWith("multipart")){
				Multipart mp2 = (Multipart)body.getContent();
				Map<String, Integer> ret = processMultipart(mp2);
				for(String key: ret.keySet()){
					if(types.containsKey(key)){
						types.put(key, types.get(key)+ret.get(key));
					}
					else{
						types.put(key, ret.get(key));
					}
				}
			}
		}
		return types;
	}
	
	@SuppressWarnings("unused")
	private static void testCBIRObject() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, InvalidParameterSpecException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, UnrecognizedFormatException, BadPaddingException{
		/**************CIPHER TEXT DOCUMENT*********************/
		MIECrypto crypto = new MIECryptoModule();
		///read text document
		byte[] tb = readTxt("0");
		byte[] enc = crypto.cbirTxt(tb);
		FileOutputStream tout = new FileOutputStream("/home/johndoe/tout-java");
		tout.write(enc);
		tout.close();
		/************END CIPHER TEXT DOCUMENT*******************/
		/*byte[] b = "catness".getBytes();
		PorterStemmer stemmer = new PorterStemmer();
		int j = stemmer.stem(b, 0, b.length);
		System.out.println(j);*/
		/***************CIPHER IMG DOCUMENT*********************/
		for(int t = 0; t < 10; t++){
			String st = ""+t;
			byte[] buffer = readImg(st);
			enc = crypto.encryptImg(buffer);
			CBIRCipherText obj = new CBIRCipherText(enc);
			FileWriter out = new FileWriter("/home/johndoe/encoded-java"+t);
			//System.out.println(enc.length);
			float[][] encoded_float = obj.getImgFeatures();
			for(int i = 0; i < encoded_float.length; i++){
				for(int j = 0; j < encoded_float[i].length; j++){
					out.write((int)encoded_float[i][j]+"");
				}
				out.write("\n");
			}
			out.close();
			byte[] cipher_img = obj.getCipherText();
			byte[] buffer2 = crypto.decryptImg(cipher_img);
			if(Arrays.equals(buffer, buffer2)){
				System.out.println("success");
			}
			else{
				System.out.println("fail");
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void setupMimeDataset() throws IOException, MessagingException{
		for(int i = 0; i < 1000; i++){
			createMimeDocument(i);
		}
	}
	
	@SuppressWarnings("unused")
	private static void setupDMimeDataset() throws IOException, MessagingException{
		for(int i = 0, j = 0; i < 1000; i++, j+=2){
			createDoubleMimeDoc(i, j);
		}
	}
	
	@SuppressWarnings("unused")
	private static void testClientUnstructured() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException{
		MIE mie = new MIEClient(false);
		int last = 999;
		for(int i = 0; i <= last; i++){
			String id = i+"";
			byte[] img = readImg(id);
			byte[] txt = readTxt(id);
			mie.addUnstructredDoc(id, img, txt);
			System.out.println("sent "+id);
		}
		mie.index(true, false);
		for(int i = 0; i <= last; i++){
			search(i, mie);
		}
		/*for(int i = 0; i <= last; i++){
			String id = i+"";
			getFile(mie, id);
		}
		search(0, mie);
		search(333, mie);
		search(758, mie);
		search(298, mie);
		search(634, mie);*/
		//getFile(mie, 0);
		/*getFile(mie, 1);
		getFile(mie, 2);
		getFile(mie, 0);*/
	}
	
	private static void search(int k, MIE mie) throws IOException{
		///search for an image
		System.out.println("searching for: "+k);
		String sk = ""+k;
		byte[] img = readImg(sk);
		byte[] txt = readTxt(sk);
		List<SearchResult> res = mie.searchUnstructuredDocument(img, txt, 0);
		for(SearchResult i: res){
			System.out.printf("Id: %s Score: %.6f\n", i.getId(), i.getScore());
		}
		/*if(res.size() > 0){
			getFile(mie, res.get(0).getId());
		}*/
	}
	
	@SuppressWarnings("unused")
	private static void getFile(MIE mie, String id) throws IOException{
		byte[][] doc = mie.getUnstructuredDoc(id, true);
		File dir = new File(System.getProperty("user.home"));
		FileOutputStream out = new FileOutputStream(new File(dir, "im"+id+".jpg"));
		out.write(doc[0]);
		out.close();
		out = new FileOutputStream(new File(dir, "tags"+id+".txt"));
		out.write(doc[1]);
		out.close();
	}
	
	@SuppressWarnings("unused")
	private static void testClientMime() throws IOException, MessagingException, NoSuchAlgorithmException, NoSuchPaddingException{
		MIE mie = new MIEClient(false);
		int last = 999;
		for(int i = 0; i <= last; i++){
			String id = ""+i;
			byte[] mime = readMime(id);
			mie.addMime(id, mime);
			System.out.println("sent "+id);
		}
		mie.index(true, false);
		for(int i = 0; i <= last; i++){
			searchMime(i, mie);
		}
		/*for(int i = 0; i <= last; i++){
			String id = i+"";
			getMimeFile(mie, id);
		}*/
	}
	
	private static void searchMime(int k, MIE mie) throws IOException, MessagingException{
		///search for an image
		System.out.println("searching for: "+k);
		String sk = ""+k;
		byte[] mime = readMime(sk);
		List<SearchResult> res = mie.searchMime(mime, 0);
		for(SearchResult i: res){
			System.out.printf("Id: %s Score: %.6f\n", i.getId(), i.getScore());
		}
		/*if(res.size() > 0){
			getFile(mie, res.get(0).getId());
		}*/
	}
	
	private static void getMimeFile(MIE mie, String id) throws IOException{
		byte[] doc = mie.getMime(id, true);
		File dir = new File(System.getProperty("user.home"));
		FileOutputStream out = new FileOutputStream(new File(dir, id+".mime"));
		out.write(doc);
		out.close();
	}
	
	@SuppressWarnings("unused")
	private static void testClientDMime() throws IOException, MessagingException, NoSuchAlgorithmException, NoSuchPaddingException{
		MIE mie = new MIEClient(false);
		int last = 999;
		for(int i = 0; i <= last; i++){
			String id = ""+i;
			byte[] mime = readDMime(id);
			mie.addMime(id, mime);
			System.out.println("sent "+id);
		}
		mie.index(true, false);
		for(int i = 0; i <= last; i++){
			searchDMime(i, mie);
		}
		for(int i = 0; i <= last; i++){
			String id = i+"";
			getMimeFile(mie, id);
		}
	}
	
	private static void searchDMime(int k, MIE mie) throws IOException, MessagingException{
		///search for an image
		System.out.println("searching for: "+k);
		String sk = ""+k;
		byte[] mime = readDMime(sk);
		List<SearchResult> res = mie.searchMime(mime, 0);
		for(SearchResult i: res){
			System.out.printf("Id: %s Score: %.6f\n", i.getId(), i.getScore());
		}
		/*if(res.size() > 0){
			getFile(mie, res.get(0).getId());
		}*/
	}
	
	@SuppressWarnings("unused")
	private static void imgHeaders() throws IOException{
		File dir = new File("/home/johndoe/MIE/Datasets/flickr_imgs");
		String[] files = dir.list();
		Map<Container, List<String>> headers = new HashMap<Container, List<String>>();
		for(String file: files){
			FileInputStream in = new FileInputStream(new File(dir, file));
			byte[] array = new byte[13];
			in.read(array);
			in.close();
			String t = new String(array);
			if(t.contains("JFIF")){
				System.out.println("image");
			}
			Container header = new Container(array);
			List<String> l;
			if(headers.containsKey(header)){
				l = headers.get(header);
			}
			else{
				l = new LinkedList<String>();
			}
			l.add(file);
			headers.put(header, l);
		}
	}
	
	private static class Container{
		
		private byte[] array;
		
		private Container(byte[] array){
			this.array = array;
		}

		@Override
		public boolean equals(Object obj){
			if(obj instanceof Container){
				return Arrays.equals(this.array, ((Container)obj).array);
			}
			return false;
		}
		
		@Override
		public int hashCode(){
			return Arrays.hashCode(array);
		}
	}
	
	@SuppressWarnings("unused")
	private static void testKeys() throws NoSuchAlgorithmException, NoSuchPaddingException, IOException, IllegalBlockSizeException{
		MIECrypto crypto = new MIECryptoModule();
		byte[] img = readImg("0");
		byte[] txt = readTxt("0");
		
		byte[] img_cipher = crypto.encryptImg(img);
		ByteBuffer b = ByteBuffer.wrap(img_cipher);
		int cbir_length = b.getInt();
		FileOutputStream out = new FileOutputStream("/home/johndoe/encoded-java2");
		ByteBuffer b2 = ByteBuffer.allocate(cbir_length-4);
		int rows = b.getInt();
		for(int i = 0; i < rows; i++){
			for(int j = 0; j < 64; j++){
				float f = b.getFloat();
				b2.putInt((int)f);
			}
		}
		out.write(b2.array());
		out.close();
		
		byte[] txt_cipher = crypto.encryptTxt(txt);
		b = ByteBuffer.wrap(txt_cipher);
		cbir_length = b.getInt();
		FileOutputStream tout = new FileOutputStream("/home/johndoe/tout-java2");
		tout.write(txt_cipher, 4, cbir_length);
		tout.close();
	}
	
	@SuppressWarnings("unused")
	private static void switchkeys() throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException, InvalidKeyException, NoSuchProviderException, InvalidKeySpecException{
		///*******************img***********************************///
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
		KeyGenerator keyGen = KeyGenerator.getInstance("AES", "BC");
		SecretKey cipher = keyGen.generateKey();
		CBIRDCipherKeySpec k = new CBIRDCipherKeySpec(keySpec, cipher);
		byte[] encoded = k.getEncoded();
		FileOutputStream out = new FileOutputStream("/home/johndoe/MIE/Data/Client/MIE/cbird.key");
		out.write(encoded);
		out.close();
		///*******************txt***********************************///
		FileInputStream tkey = new FileInputStream("/home/johndoe/MIE/Data/Client/MIE/textKey");
		byte[] tkeyb = new byte[tkey.available()];
		tkey.read(tkeyb);
		tkey.close();
		///construct key
		SecretKeySpec ks = new SecretKeySpec(tkeyb, "CBIRS");
		SecretKeyFactory skeyf = SecretKeyFactory.getInstance("CBIRS");
		SecretKey skey = skeyf.generateSecret(ks);
		CBIRSKeySpec sk = (CBIRSKeySpec) skeyf.getKeySpec(skey, CBIRSKeySpec.class);
		CBIRSCipherKeySpec k2 = new CBIRSCipherKeySpec(sk, cipher);
		encoded = k2.getEncoded();
		out = new FileOutputStream("/home/johndoe/MIE/Data/Client/MIE/cbirs.key");
		out.write(encoded);
		out.close();
	}
	
	private static byte[] readImg(String id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im"+id+".jpg");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private static byte[] readTxt(String id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags"+id+".txt");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}

	private static byte[] readMime(String id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_mime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	private static byte[] readDMime(String id) throws IOException{
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_dmime/"+id+".mime");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		return buffer;
	}
	
	/*private static void testZLib() throws IOException{
		byte[] img = readImg();
		ServerConnectorModule zlib = new ServerConnectorModule();
		byte[] compressed = zlib.zipAndSend(img);
		if(compressed != null){
			System.out.println(img.length+" -> "+compressed.length);
			FileOutputStream out = new FileOutputStream("/home/johndoe/zlib-img-java-final");
			out.write(compressed);
			out.close();
		}
		else{
			System.out.println("Compression went wrong");
		}
	}*/
	
	@SuppressWarnings("unused")
	private static void testOpenCV() throws IOException{
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		FeatureDetector detector = FeatureDetector.create(FeatureDetector.PYRAMID_DENSE);
		DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.SURF);
		Mat descriptors;
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] buffer = new byte[in.available()];
		in.read(buffer);
		in.close();
		///create mat from buffer
		Mat img_buffer = new Mat(1, buffer.length, CvType.CV_8UC4);
		try{
		    img_buffer.put(0, 0, buffer);
		}catch(UnsupportedOperationException e){
			///silently ignored
		}
		Mat img = Highgui.imdecode(img_buffer, Highgui.CV_LOAD_IMAGE_COLOR);
		///detect features
		MatOfKeyPoint keypoints = new MatOfKeyPoint();
		detector.detect(img, keypoints);
		///extract features
		descriptors = new Mat();
		extractor.compute(img, keypoints, descriptors);
		System.out.println("OK");
	}
	
	@SuppressWarnings("unused")
	private static void testMie() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, IOException{
		testMIEImage();
		testMIEText();
	}
	

	private static void testMIEText() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException{
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags0.txt");
		byte[] txt_buffer = new byte[in.available()];
		in.read(txt_buffer);
		in.close();
		///generate cipher key
		SecureRandom random = new SecureRandom();
		KeyGenerator keyGen = KeyGenerator.getInstance("CBIRS:AES");
		keyGen.init(random);
		Key img_key = keyGen.generateKey();
		///instatiate cipher
		byte[] img_cipher_buffer;
		Cipher img_cipher = Cipher.getInstance("CBIRS:AES");
		byte[] iv = new byte[img_cipher.getBlockSize()];
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		img_cipher_buffer = testEncryptSingleFinal("CBIRS:AES", txt_buffer, img_key, ivSpec);
		System.out.printf("EncryptSingleFinal: ");
		testDecryptSingleDoFinal("CBIRS:AES",txt_buffer, img_cipher_buffer, img_key, ivSpec);
	}
	
	private static void testMIEImage() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException{
		///read image
		FileInputStream in = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] txt_buffer = new byte[in.available()];
		in.read(txt_buffer);
		in.close();
		///generate cipher key
		SecureRandom random = new SecureRandom();
		KeyGenerator keyGen = KeyGenerator.getInstance("CBIRD:AES");
		keyGen.init(random);
		Key img_key = keyGen.generateKey();
		///instatiate cipher
		byte[] img_cipher_buffer;
		Cipher img_cipher = Cipher.getInstance("CBIRD:AES");
		byte[] iv = new byte[img_cipher.getBlockSize()];
		random.nextBytes(iv);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		img_cipher_buffer = testEncryptSingleFinal("CBIRD:AES", txt_buffer, img_key, ivSpec);
		System.out.printf("EncryptSingleFinal: ");
		testDecryptSingleDoFinal("CBIRD:AES",txt_buffer, img_cipher_buffer, img_key, ivSpec);
	}
	
	private static byte[] testEncryptSingleFinal(String instance, byte[] plain_text, Key key, AlgorithmParameterSpec params) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidAlgorithmParameterException{
		///instatiate cipher
		Cipher img_cipher = Cipher.getInstance(instance);
		img_cipher.init(Cipher.ENCRYPT_MODE, key, params);
		byte[] img_cipher_buffer = img_cipher.doFinal(plain_text);
		return img_cipher_buffer;
	}
	
	private static void testDecryptSingleDoFinal(String instance, byte[] plain_text, byte[] cipher_text, Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
		///get new cipher instance to decrypt
		Cipher img_decipher = Cipher.getInstance(instance);
		img_decipher.init(Cipher.DECRYPT_MODE, key, params);
		byte[] new_img = img_decipher.doFinal(cipher_text);
		///compare buffers
		if(Arrays.equals(plain_text, new_img)){
			System.out.println("Sucess");
		}
		else{
			System.out.println("Buffers are not equal");
		}
	}
	
	@SuppressWarnings("unused")
	private static void testMime() throws IOException, MessagingException, NoSuchAlgorithmException, NoSuchPaddingException{
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s);
		MimeMultipart content = new MimeMultipart();
		///add image
		FileInputStream imgin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im0.jpg");
		byte[] imgb = new byte[imgin.available()];
		imgin.read(imgb);
		imgin.close();
		MimeBodyPart mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		content.addBodyPart(mp);
		///add text
		FileInputStream txtin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags0.txt");
		byte[] txtb = new byte[txtin.available()];
		txtin.read(txtb);
		txtin.close();
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		content.addBodyPart(mp);
		///nested multipart
		/*MimeMultipart c = new MimeMultipart();
		mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(c);
		content.addBodyPart(mp);*/
		///finalize
		msg.setContent(content);
		FileOutputStream out = new FileOutputStream("/home/johndoe/mime-test");
		msg.writeTo(out);
		out.close();
		///read file
		FileInputStream min = new FileInputStream("/home/johndoe/mime-test");
		byte [] mb = new byte[min.available()];
		min.read(mb);
		min.close();
		///test mime processing
		/*MIEClient mie = new MIEClient();
		Map<String, byte[]> map = mie.addMime(0, mb);
		for(String map_key: map.keySet()){
			System.out.println(map_key);
		}*/
	}
	
	private static void createMimeDocument(int id) throws IOException, MessagingException{
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s);
		MimeMultipart content = new MimeMultipart();
		///add image
		FileInputStream imgin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im"+id+".jpg");
		byte[] imgb = new byte[imgin.available()];
		imgin.read(imgb);
		imgin.close();
		MimeBodyPart mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		content.addBodyPart(mp);
		///add text
		FileInputStream txtin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags"+id+".txt");
		byte[] txtb = new byte[txtin.available()];
		txtin.read(txtb);
		txtin.close();
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		content.addBodyPart(mp);
		///nested multipart
		/*MimeMultipart c = new MimeMultipart();
		mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(c);
		content.addBodyPart(mp);*/
		///finalize
		msg.setContent(content);
		FileOutputStream out = new FileOutputStream("/home/johndoe/MIE/Datasets/flickr_mime/"+id+".mime");
		msg.writeTo(out);
		out.close();
	}
	
	private static void createDoubleMimeDoc(int id, int img) throws IOException, MessagingException{
		Properties p = new Properties();
		Session s = Session.getInstance(p);
		Message msg = new MimeMessage(s);
		MimeMultipart content = new MimeMultipart();
		///add first image
		FileInputStream imgin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im"+img+".jpg");
		byte[] imgb = new byte[imgin.available()];
		imgin.read(imgb);
		imgin.close();
		MimeBodyPart mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		content.addBodyPart(mp);
		///add first text
		FileInputStream txtin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags"+img+".txt");
		byte[] txtb = new byte[txtin.available()];
		txtin.read(txtb);
		txtin.close();
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		content.addBodyPart(mp);
		///add second image
		int nid;
		if(img == 1999)
			nid = 0;
		else
			nid = img+1;
		imgin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_imgs/im"+nid+".jpg");
		imgb = new byte[imgin.available()];
		imgin.read(imgb);
		imgin.close();
		MimeBodyPart mp2 = new MimeBodyPart();
		mp2.setContent(imgb, "image/jpeg");
		content.addBodyPart(mp2);
		///add first text
		txtin = new FileInputStream("/home/johndoe/MIE/Datasets/flickr_tags/tags"+nid+".txt");
		txtb = new byte[txtin.available()];
		txtin.read(txtb);
		txtin.close();
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		content.addBodyPart(mp);
		///nested multipart
		/*MimeMultipart c = new MimeMultipart();
		mp = new MimeBodyPart();
		mp.setContent(imgb, "image/jpeg");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(new String(txtb), "text/plain");
		c.addBodyPart(mp);
		mp = new MimeBodyPart();
		mp.setContent(c);
		content.addBodyPart(mp);*/
		///finalize
		System.out.println("base id: "+img+" extra: "+nid+" final id: "+id);
		msg.setContent(content);
		FileOutputStream out = new FileOutputStream("/home/johndoe/MIE/Datasets/flickr_dmime/"+id+".mime");
		msg.writeTo(out);
		out.close();
	}
	
	@SuppressWarnings("unused")
	private static void listServices(Provider p){
		Set<Service> services = p.getServices();
		Map<String, List<String> > algorithms = new HashMap<String, List<String> >();
		for(Service s: services){
			String type = s.getType();
			List<String> alg = algorithms.get(type);
			if(alg == null){
				alg = new LinkedList<String>();
				
			}
			alg.add(s.getAlgorithm());
			algorithms.put(type, alg);
		}
		for(String s: algorithms.keySet()){
			List<String> alg = algorithms.get(s);
			for(String s2: alg){
				System.out.println(s+": "+s2);
			}
		}
	}

}
