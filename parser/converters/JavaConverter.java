import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import java.util.Iterator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;

public class JavaConverter implements Converter {

	private String name;

	public JavaConverter() {
		name = "Java Converter using Java Advanced Imaging";
	}

	public String getDescription() {
		String formats = "";
		for(String format: this.supportedFormats()){
			formats += " "+format;
		}
		return name+". Supported formats:"+formats;
	}

	public String[] supportedFormats() {
		return ImageIO.getReaderFormatNames();
	}

	public byte[] convert(byte[] img) {
		InputStream inImg = new ByteArrayInputStream(img);
		byte[] jpgImg = null;
		try{
			BufferedImage buffer = ImageIO.read(inImg);
			ImageWriteParam param = new JPEGImageWriteParam(null);
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(1.0f);
			Iterator writers = ImageIO.getImageWritersByFormatName("jpg");
			ImageWriter writer = (ImageWriter)writers.next();
			IIOImage imgJpg = new IIOImage(buffer, null, null);
			ByteArrayOutputStream outImg = new ByteArrayOutputStream();
			ImageOutputStream out = ImageIO.createImageOutputStream(outImg);
			writer.setOutput(out);
			writer.write(null, imgJpg, param);
			if(ImageIO.write(buffer, "jpeg", outImg)){
				jpgImg = outImg.toByteArray();
			}
		}
		catch (IOException e){
			e.printStackTrace();
		}
		return jpgImg;
	}
}