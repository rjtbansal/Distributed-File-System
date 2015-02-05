import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class SocketThread implements Runnable{
	
	private Message message;
	static String IV="AAAAAAAAAAAAAAAA";
    static String encryptionKey = "0123456789abcdef";
	public SocketThread(Message message){
		this.message = message;
	}

	
	public void run() {

		try {
			Socket sock = new Socket(new ConfigFile().ipMaster,new ConfigFile().portMaster);
			
			sendMessage(sock);
			
			System.out.println("File replicated");
			
		} catch (Exception e) {
			System.err.println("Master down, trying backup server...");
			try {
				Socket sock = new Socket(new ConfigFile().ipSlave,new ConfigFile().portSlave);
				sendMessage(sock);

			} catch (UnknownHostException e1) {
				System.out.println("Unknown host exception while connecting to slave server "+e);
			} catch (IOException e1) {
				System.out.println("IOException while connecting to slave server "+e);
			}
		}

	}
	
	public void sendMessage(Socket sock){
		GZIPOutputStream gz;
		ObjectOutputStream oos;
		GZIPOutputStream gzfile = null;
		FileOutputStream f = null;
		try {
			f = new FileOutputStream(new File("./test"));
			gzfile = new GZIPOutputStream(f);
			gzfile.finish();
			
			gz = new GZIPOutputStream(sock.getOutputStream(), true);
			oos = new ObjectOutputStream(gz);
			SealedObject cipher=encrypt(message, encryptionKey);
			System.out.println("Message sealed: "+cipher);
			oos.writeObject(cipher);
			gz.close();
			oos.close();
			sock.close();
			
		} catch (IOException e) {
			System.out.println("IOException while sending message "+e);
		} catch (Exception e) {
			System.out.println("Exception while sending message "+e);
		}
		 
		
	}
	
	public SealedObject encrypt(Message obj, String encryptionKey) {
		 Cipher cipher;
		 SealedObject sealed = null;
		try {
			cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
			SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
			 cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
			 sealed = new SealedObject(obj, cipher);
		} catch (Exception e) {
			System.out.println("Exception while encrypting object "+e);
		}
		 
		 return sealed;
		 }
	
}
