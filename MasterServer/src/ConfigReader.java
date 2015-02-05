import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class ConfigReader{
	private HashMap<String,String> configMap = new HashMap<String,String>();
	
	public String getValue(String fileName, String key){
		buildHash(fileName);
		return (configMap.get(key));
	}
	
	
	private void buildHash(String fileName){
		File f = new File(fileName);
		BufferedReader br;
		String line = null;

		try {
			br = new BufferedReader(new FileReader(f));
			while(null!=(line=br.readLine())){
				String[] split = line.split("=");
				configMap.put(split[0], split[1]);
			}
		}catch (FileNotFoundException e) {
			System.err.println("File "+fileName+" not found:"+e);
		} catch (IOException e) {
			System.err.println("Error while reading form file "+fileName+" :"+e);
		}
	}
}