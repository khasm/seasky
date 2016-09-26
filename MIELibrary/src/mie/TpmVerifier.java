package mie;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.dgc.VMID;

public final class TpmVerifier{

	protected native boolean verify(String address);

	static{
		String fileName = "TpmClient.so";
		String dir = System.getProperty("java.io.tmpdir");
		if(dir == null){
			dir = new File(".").getAbsolutePath();
		}
		try{
			long nano = System.nanoTime();
			VMID vmid = new VMID();
			File file = new File(dir+"/mie_"+fileName+nano+vmid.hashCode());
			if(file.exists()){
				System.load(file.getAbsolutePath());
			}
			else{
				InputStream in = TpmVerifier.class.getResourceAsStream("/"+fileName);
				if(in == null){
					System.err.println("TpmClient.so was not found");
				}
				else{
					byte[] in_buffer = new byte[in.available()];
					in.read(in_buffer);
					in.close();
					file = new File(dir+"/mie_"+fileName+nano+vmid.hashCode());
					///might have concurrency problems if deleting a file another jvm is using
					///should not happen with nano+vmid.hashCode concated to the name
					file.deleteOnExit();
					//System.err.println("Writing "+file.getAbsolutePath());
					OutputStream out = new FileOutputStream(file);
					out.write(in_buffer);
					out.close();
					System.load(file.getAbsolutePath());
				}
			} 
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
}