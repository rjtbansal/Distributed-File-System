import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Server {
	static String IV = "AAAAAAAAAAAAAAAA";
	static String encryptionKey = "0123456789abcdef";
	
	public static void main(String args[]) {
		try {
			synchronize();
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			System.out.println("Unknown host from synchronize()"+e1);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			System.out.println("I/O exception from synchronize()"+e1);
		}
		ServerSocket ss = null;
		InputStream is = null;
		try {
			ss = new ServerSocket(new ConfigFile().portMaster);
			System.out.println("Master server Started...");

			while (true) {
				try {
					Socket s = ss.accept();
					is = s.getInputStream();
					System.out.println("------------New Message------------");
					GZIPInputStream gzis = new GZIPInputStream(is);
					ObjectInputStream ois = new ObjectInputStream(gzis);
					SealedObject unseal=(SealedObject)ois.readObject();
					Message unsealed=decrypt(unseal,encryptionKey);
					System.out.println("Decrypted Object:"+unsealed);
					sendToSlave(unsealed);
					if (unsealed != null) {
						File f = new File("./backupdir/"+unsealed.fileName);
						if(unsealed.action.equalsIgnoreCase("delete")){
							deleteFile(unsealed, f);
							}
						else if(unsealed.action.equalsIgnoreCase("create")){
						    createFile(unsealed, f);
						}
						else if(unsealed.action.equalsIgnoreCase("modify")){
														modifyFile(unsealed, f);
						}
						is.close();
						s.close();
					}
				} catch (Exception e) {
					System.out.println("Exception --->" + e.getMessage());
				}
			}

			
		} catch (Exception e) {
			System.out.println(e);
		}
	}
	
	public static void deleteFile(Message to, File f){
		f.delete();
	}
	
	public static void createFile(Message to, File f) throws IOException{
		if (!f.exists()) {
			f.createNewFile();
		}
		PrintWriter pr = new PrintWriter(f);
		pr.println(to.fileData);
		pr.flush();
		pr.close();
	}
	
	public static void modifyFile(Message to, File f) throws IOException{
		File pFile = new File("./backupdir/patchfile");
		if(!pFile.exists()){
			pFile.createNewFile();
		}
		PrintWriter pFilePrinter = new PrintWriter(pFile);
		pFilePrinter.print(to.fileData);
		pFilePrinter.flush();
		pFilePrinter.close();
		
		ProcessBuilder builder = new ProcessBuilder("sh","./my.sh");
		builder.redirectError(new File("out.txt"));

		Process p = builder.start();

	}
	
	public static void sendToSlave(Message message){
		try {
			Socket sock = new Socket(new  ConfigFile().ipSlave,new ConfigFile().portSlave);
			
			GZIPOutputStream gz;
			ObjectOutputStream oos;
			
				gz = new GZIPOutputStream(sock.getOutputStream(), true);
				oos = new ObjectOutputStream(gz);
				SealedObject cipher=encrypt(message, encryptionKey);
				System.out.println("Encrypted Object:"+cipher);
				oos.writeObject(cipher);
				gz.close();
				oos.close();
				sock.close();
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void synchronize() throws UnknownHostException, IOException
	{
		String dir = "./backupdir";
		HashMap<String, Long> snapFiles = new HashMap<String, Long>();
		snapFiles.clear();
		File theDirectory = new File(dir);
		File[] children = theDirectory.listFiles();
		for (int i = 0; i < children.length; i++) {
			File file = children[i];
			snapFiles.put(file.getName(),new Long(file.lastModified()));
		}
		try {
				//send file to server 2 socket 9999 
				Socket sock = new Socket(new ConfigFile().ipSlave,new ConfigFile().portSlave);	
				
				GZIPOutputStream gzos = null;
				ObjectOutputStream oos = null;
				
				GZIPInputStream gzis = null;
				ObjectInputStream ois = null;

				gzos = new GZIPOutputStream(sock.getOutputStream(), true);
				oos = new ObjectOutputStream(gzos);
				SealedObject cipher=encrypt(new Message("sync",null,null,snapFiles), encryptionKey);
				System.out.println("Encrypted Object:"+cipher);
				oos.writeObject(cipher);
				oos.flush();
				//gzos.finish();
				System.out.println("sync object sent");
				
				gzis = new GZIPInputStream(sock.getInputStream());
				ois = new ObjectInputStream(gzis);
				SealedObject unseal = null;

				while(null!=(unseal = (SealedObject)ois.readObject()))
				{   
					Message unsealed=decrypt(unseal,encryptionKey);
					System.out.println("Decrypted Object:"+unsealed);
					
					File f = new File("./backupdir/"+unsealed.fileName);
					if(unsealed.action.equalsIgnoreCase("sync_create"))
					{
						createFile(unsealed, f);
					}
					else if(unsealed.action.equalsIgnoreCase("sync_delete"))
					{
						deleteFile(unsealed, f);						
					}				
				}
		}
		
		catch(Exception e)
		{
			System.out.println("Nothing to synchronize");
		}
	}
	
	public static Message decrypt(SealedObject unseal, String encryptionKey){
		Cipher cipher;
		Message obj = null;
		try {
			cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
			SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
			cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
			obj=(Message)unseal.getObject(cipher);
			
		} catch (NoSuchAlgorithmException e) {
			System.out.println("In decrypt"+e);
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("In decrypt"+e);
		}
		
		return obj;
		 }
	
	public static SealedObject encrypt(Message obj, String encryptionKey) throws Exception {
		 Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
		 SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
		 cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
		 SealedObject sealed = new SealedObject(obj, cipher);
		 return sealed;
		 }
	
}



