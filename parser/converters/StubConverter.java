public class StubConverter implements Converter {

	private String name;

	public StubConverter() {
		name = "Stub Converter that returns an unaltered image in the same format as provided";
	}

	public String getDescription() {
		String formats = "";
		for(String format: this.supportedFormats()){
			formats += " "+format;
		}
		return name+": Supported formats:"+formats;
	}

	public String[] supportedFormats() {
		return new String[]{"any"};
	}

	public byte[] convert(byte[] img) {
		return img;
	}

}