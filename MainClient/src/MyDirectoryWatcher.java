import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import difflib.DiffUtils;
import difflib.Patch;

public class MyDirectoryWatcher extends DaemonThread{


    private String directory;
    

    public Map<String,Long> newFiles = new HashMap<String,Long>();


    public Map<String,Long> oldFiles = new HashMap<String,Long>();
    
    static String IV = "AAAAAAAAAAAAAAAA";
	static String encryptionKey = "0123456789abcdef";
	

    
    public MyDirectoryWatcher(String directoryPath, int intervalSeconds)
            throws IllegalArgumentException {

        super(intervalSeconds);

        File theDirectory = new File(directoryPath);

        if (theDirectory != null && !theDirectory.isDirectory()) {

            String message = "The path " + directory +
                    " does not represent a valid directory.";
            throw new IllegalArgumentException(message);

        }

        this.directory = directoryPath;

    }
    
    public static void main(String[] args) {
		try {
			synchronize();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        MyDirectoryWatcher dw = new MyDirectoryWatcher("./dir", new ConfigFile().interval);
        dw.start();
    }
    
    public void start() {

    	directorySnapshot();

        super.start();

    }
    
    private void directorySnapshot() {

        oldFiles.clear();
        oldFiles.putAll(newFiles);

        newFiles.clear();

        File dir = new File(directory);
        File[] files = dir.listFiles();

        for (int i = 0; i < files.length; i++) {

            File file = files[i];
            newFiles.put(file.getAbsolutePath(),
                    new Long(file.lastModified()));

        }

    }


    protected void doInterval() {

    	directorySnapshot();

        Iterator currentIt = newFiles.keySet().iterator();

        while (currentIt.hasNext()) {

            String fileName = (String) currentIt.next();
            Long lastModified = (Long) newFiles.get(fileName);
            

            if (!oldFiles.containsKey(fileName)) {
                fileAdded(new File(fileName));
            }
            //If this file did exist before
            else if (oldFiles.containsKey(fileName)) {

                Long prevModified = (Long) oldFiles.get(fileName);

                //If this file existed before and has been modified
                if (prevModified.compareTo(lastModified) != 0) {
                    
                        fileChanged(new File(fileName));
                    
                }
            }
        }

        Iterator prevIt = oldFiles.keySet().iterator();

        while (prevIt.hasNext()) {

            String fileName = (String) prevIt.next();

            //If this file did exist before, but it does not now, then
            //it's been deleted
            if (!newFiles.containsKey(fileName)) {
                fileDeleted(fileName);
            }
        }
    }

   
    protected void fileAdded(File file) {
    	try {
			Files.copy( 
			        file.toPath(), 
			        new File("./backupdir/"+file.getName()).toPath(),
			        StandardCopyOption.REPLACE_EXISTING,
			        StandardCopyOption.COPY_ATTRIBUTES);

			String fileData = readFile(file.getAbsolutePath());
			
			

			Message messageObj = new Message("create", file.getName(), fileData, null);
			
	        new Thread(new SocketThread(messageObj)).start();

		} catch (IOException e) {
			System.out.println("IO Exception in resourceAdded method");
			e.printStackTrace();
		}

    }

    protected void fileChanged(File file) {
    	try { 
    			
    		String backupPath = "./backupdir/"+file.getName();
    		
    		String command = "diff -u ./backupdir ./dir";
    		
    		
            Process proc = Runtime.getRuntime().exec(command); 
            proc.waitFor();
			
			StringBuffer output = new StringBuffer();
			BufferedReader reader = 
                    new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line = "";			
                while ((line = reader.readLine())!= null) {
                	output.append(line + "\n");
                }
            	
    		System.out.println(output);
    		
  		
    		Message messageObj = new Message("modify", file.getName(), output.toString(), null);
    		
    		new Thread(new SocketThread(messageObj)).start();
    		
			Files.copy( 
			        file.toPath(), 
			        new File(backupPath).toPath(),
			        StandardCopyOption.REPLACE_EXISTING,
			        StandardCopyOption.COPY_ATTRIBUTES);
		} catch (IOException e) {
			System.out.println("IO Exception in resourceChanged method");
			e.printStackTrace();
		} 
        
 catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    }

    protected void fileDeleted(String file) {
		try {
			System.out.println(file);
			String[] name = file.split("dir/");
			System.out.println(name[1]);
			Files.deleteIfExists(new File("./backupdir/"+name[1]).toPath());
			
			Message messageObj = new Message("delete", name[1], null, null);
			new Thread(new SocketThread(messageObj)).start();
		} catch (IOException e) {
			System.out.println("IO Exception in resourceDeleted method");
			e.printStackTrace();
		}

    }
    
    private String readFile(String fileName) throws IOException {
	    BufferedReader reader = new BufferedReader(new FileReader(fileName));
	    String line = null;
	    StringBuilder stringBuilder = new StringBuilder();
	    String ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append(line);
	        stringBuilder.append(ls);
	    }
	    reader.close();
	    return stringBuilder.toString();
	}
    
    public static void synchronize() throws UnknownHostException, IOException
	{
		String dir = "./dir";
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
					
					File f = new File("./dir/"+unsealed.fileName);
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
    
    public static void deleteFile(Message to, File f){
		f.delete();
		System.out.println(f.getName());
		String[] name = f.getName().split("dir/");
		System.out.println(name[1]);
		try {
			Files.deleteIfExists(new File("./backupdir/"+name[1]).toPath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void createFile(Message to, File f) throws IOException{
		if (!f.exists()) {
			f.createNewFile();
		}
		PrintWriter pr = new PrintWriter(f);
		pr.println(to.fileData);
		pr.flush();
		pr.close();
		
		Files.copy( 
		        f.toPath(), 
		        new File("./backupdir/"+f.getName()).toPath(),
		        StandardCopyOption.REPLACE_EXISTING,
		        StandardCopyOption.COPY_ATTRIBUTES);

	}
	
	public static Message decrypt(SealedObject unseal, String encryptionKey){
		Cipher cipher;
		Message obj = null;
		try {
			cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
			SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
			cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
			obj=(Message)unseal.getObject(cipher);
			
		} catch (Exception e) {
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