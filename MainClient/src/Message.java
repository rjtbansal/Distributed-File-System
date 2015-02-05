import java.io.Serializable;
import java.util.HashMap;

import difflib.Patch;

public class Message implements Serializable{
	String action;
	String fileName;
	String fileData;
	HashMap<String,Long> files;
	
	public Message(String action, String fileName, String fileData, HashMap<String,Long> files){
		this.fileData = fileData;
		this.fileName = fileName;
		this.action = action;
		this.files = files;
	}
}