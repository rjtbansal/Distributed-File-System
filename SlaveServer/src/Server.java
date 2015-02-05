import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;

import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Server 
{
	static String IV="AAAAAAAAAAAAAAAA";
    static String encryptionKey = "0123456789abcdef";
    
	public static void main(String args[]) 
	{
		ServerSocket ss = null; 
		try 
		{
			ss = new ServerSocket(new ConfigFile().portSlave); // listen to client
			System.out.println("Slave server Started...");
			while (true) 
			{
				try 
				{
					Socket s = ss.accept(); // accept from client
					System.out.println("------------New Message------------");
					GZIPInputStream gzis = new GZIPInputStream(s.getInputStream());
					ObjectInputStream ois = new ObjectInputStream(gzis);
					
					GZIPOutputStream gzos = new GZIPOutputStream(s.getOutputStream(), true);
					ObjectOutputStream oos = new ObjectOutputStream(gzos);
					
					SealedObject unseal=(SealedObject)ois.readObject();
					Message unsealed=decrypt(unseal,encryptionKey);
					System.out.println("Decrypted Object:"+unsealed);

					if (unsealed != null) 
					{
						if(unsealed.action.equalsIgnoreCase("delete"))
						{
							File f = new File("./backupdir/"+unsealed.fileName);
							deleteFile(unsealed, f);
						}
						else if(unsealed.action.equalsIgnoreCase("create"))
						{
							File f = new File("./backupdir/"+unsealed.fileName);
							createFile(unsealed, f);
						}
						else if(unsealed.action.equalsIgnoreCase("modify"))
						{
							File f = new File("./backupdir/"+unsealed.fileName);
							modifyFile(unsealed, f);
						}
						else if (unsealed.action.equalsIgnoreCase("sync"))
						{
							System.out.println("in sync");
							synchronize(unsealed,oos);
						}
						s.close();
					}
				} 
				catch (Exception e) 
				{
					System.out.println("Exception --->" + e);
				}
			}

			
		} 
		catch (Exception e) 
		{
			System.out.println(e);
		}
	}
	
	public static void deleteFile(Message to, File f)
	{
		f.delete();
	}	
	public static void createFile(Message to, File f) throws IOException
	{
		if (!f.exists()) 
		{
			f.createNewFile();
		}
		PrintWriter pr = new PrintWriter(f);
		pr.println(to.fileData);
		pr.flush();
		pr.close();
	}	
	public static void modifyFile(Message to, File f) throws IOException
	{
		File pFile = new File("./backupdir/patchfile");
		if(!pFile.exists())
		{
			pFile.createNewFile();
		}
		PrintWriter pFilePrinter = new PrintWriter(pFile);
		pFilePrinter.print(to.fileData);
		pFilePrinter.flush();
		pFilePrinter.close();
		
		ProcessBuilder builder = new ProcessBuilder("sh","./my.sh");
		Process p = builder.start();
	}	
	public static void synchronize(Message to, ObjectOutputStream oos)
	{
				
		 String directory = "./backupdir";
		 Map<String,Long> currentFiles = new HashMap<String,Long>();
		 Map<String,Long> oneFiles = new HashMap<String,Long>();    
		 //take snapshot of existing directory
		currentFiles.clear();
        File theDirectory = new File(directory);
        File[] children = theDirectory.listFiles();
        for (int i = 0; i < children.length; i++) 
        {
            File file = children[i];
            currentFiles.put(file.getName(),new Long(file.lastModified()));
        }
        
		//compare file with current snapshot
        oneFiles = to.files;
        Iterator currentIt = currentFiles.keySet().iterator();
        while (currentIt.hasNext()) 
        {   
        	String fileName = (String) currentIt.next();
            Long lastModified = (Long) currentFiles.get(fileName);
            if (!oneFiles.containsKey(fileName)) 
            {
            	resourceAdded(fileName,oos);
            }
            else if (oneFiles.containsKey(fileName)) 
            {
                Long oneModified = (Long) oneFiles.get(fileName);
                //If this file existed before and has been modified
                if (oneModified.compareTo(lastModified) != 0)
                {    
                	resourceAdded(fileName,oos);   
                }
            }

        }
        Iterator oneIt = oneFiles.keySet().iterator();
        while (oneIt.hasNext()) 
        {
            String fileName = (String) oneIt.next();
           if (!currentFiles.containsKey(fileName)) 
            {
            	resourceDeleted(fileName,oos);
            }
        }
        try {
			oos.close();
		} catch (IOException e) {
			System.out.println("io exception after closing in synchronize");
			e.printStackTrace();
		}
	}
	protected static void resourceAdded(String fileName, ObjectOutputStream oos) 
    {
		System.out.println("Added: "+fileName);
    	try {
				String fileData = readFile(fileName);
				System.out.println(fileData);
				
				Message messageObj = new Message("sync_create", fileName, fileData, null);	
				SealedObject cipher=encrypt(messageObj, encryptionKey);
				System.out.println("Encrypted Object:"+cipher);
	    		
				oos.writeObject(cipher);
				oos.flush();
	    	}
	    	catch (IOException e) 
	    	{
	    			System.out.println("IO Exception in resourceAdded method");
	    			e.printStackTrace();
	    	} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    }
	protected static void resourceDeleted(String fileName, ObjectOutputStream oos) 
    {	
		System.out.println("Deleted: "+fileName);
		try {
				Message messageObj = new Message("sync_delete", fileName, null,null);
				SealedObject cipher=encrypt(messageObj, encryptionKey);
				System.out.println("Encrypted Object:"+cipher);
	    		
				oos.writeObject(cipher);
				oos.flush();
			}
			catch (Exception e) 
			{
				System.out.println("IO Exception in resourceDeleted method");
				e.printStackTrace();
			}
	}
	private static String readFile(String fileName) throws IOException 
    {
	    BufferedReader reader = new BufferedReader(new FileReader(".//backupdir//"+fileName));
	    String line = null;
	    StringBuilder stringBuilder = new StringBuilder();
	    String ls = System.getProperty("line.separator");
	    while( ( line = reader.readLine() ) != null ) 
	    {
	        stringBuilder.append(line);
	        stringBuilder.append(ls);
	    }
	    reader.close();
	    return stringBuilder.toString();
	}
	
	public static SealedObject encrypt(Message obj, String encryptionKey) throws Exception {
		 Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
		 SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
		 cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
		 SealedObject sealed = new SealedObject(obj, cipher);
		 return sealed;
		 }
	
	public static Message decrypt(SealedObject unseal, String encryptionKey) throws Exception{
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "SunJCE");
		SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES");
		cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(IV.getBytes("UTF-8")));
		Message obj=(Message)unseal.getObject(cipher);
		
		return obj;
		 }
	
	
}
	


