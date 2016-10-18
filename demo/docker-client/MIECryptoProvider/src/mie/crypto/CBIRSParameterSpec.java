package mie.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.Mac;

public final class CBIRSParameterSpec implements CBIRParameterSpec {
	
	private int key_size, keyword_size;
	private String hmac_algorithm, provider;
	
	public CBIRSParameterSpec(){
		key_size = 20;
		Mac mac = null;
		try {
			mac = Mac.getInstance("HMac-SHA1", "BC");
			this.provider = "BC";
		} catch (NoSuchProviderException e) {
			try {
				mac = Mac.getInstance("HMAC_SHA1_ALGORITHM");
			} catch (NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
			this.provider = mac.getProvider().getName();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		if(mac != null){
			keyword_size = mac.getMacLength();
			hmac_algorithm = mac.getAlgorithm();
		}
	}
	
	protected CBIRSParameterSpec(int keysize, String hmac, String provider) throws NoSuchAlgorithmException{
		key_size = keysize;
		Mac mac;
		try {
			mac = Mac.getInstance(hmac, provider);
			this.provider = provider;
		} catch (NoSuchProviderException e) {
			try {
				mac = Mac.getInstance(hmac, "BC");
				this.provider = "BC";
			} catch (NoSuchProviderException e1) {
				mac = Mac.getInstance(hmac);
				this.provider = mac.getProvider().getName();
			}
		}
		keyword_size = mac.getMacLength();
		hmac_algorithm = hmac;
	}
	
	protected CBIRSParameterSpec(int keysize, String hmac) throws NoSuchAlgorithmException{
		this(keysize, hmac, "BC");
	}
	
	public int getKeySize(){
		return key_size;
	}
	
	public int getKeywordSize(){
		return keyword_size;
	}
	
	public String getHmac(){
		return hmac_algorithm;
	}
	
	public String getProvider(){
		return provider;
	}

}
