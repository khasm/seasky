package mie.utils;

public class UnrecognizedFormatException extends Exception {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -7716072930406263191L;

	public UnrecognizedFormatException() {
		super("Array format was not recognized");
	}
	
	public UnrecognizedFormatException(String msg){
		super(msg);
	}

}
