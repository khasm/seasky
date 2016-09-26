package mie.crypto;

import java.security.spec.KeySpec;
import javax.crypto.SecretKey;

public abstract class CBIRKeySpec implements KeySpec, SecretKey {

	private static final long serialVersionUID = 6852086540200916718L;

	@Override
	public String getFormat() {
		return "RAW";
	}
}
