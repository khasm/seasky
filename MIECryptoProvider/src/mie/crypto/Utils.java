package mie.crypto;

import java.security.NoSuchAlgorithmException;

public final class Utils {
	
	public static final byte CBIRD = 0x0;
	public static final byte CBIRS = 0x1;
	public static final byte FULL_CIPHER = 0x2;
	
	protected static String[] parseTransformation(String transformation) throws NoSuchAlgorithmException{
		String[] ret = new String[3];
		int index = transformation.indexOf("/");
		if(index == -1){
			ret[0] = transformation;
			ret[1] = CBIRCipherParameterSpec.DEFAULT_MODE;
			ret[2] = CBIRCipherParameterSpec.DEFAULT_PADDING;
		}
		else{
			ret[0] = transformation.substring(0, index);
			int index2 = transformation.indexOf("/", index+1);
			if(index2 == -1)
				throw new NoSuchAlgorithmException("Invalid transformation");
			else if(index2 == index+1){
				ret[1] = CBIRCipherParameterSpec.DEFAULT_MODE;
			}
			else{
				ret[1] = transformation.substring(index+1, index2);
			}
			ret[2] = transformation.substring(index2+1);
		}
		return ret;
	}
}
