import java.io.FileReader;
import java.util.Properties;


public class ConfigFile {
   static int portMaster;
   static int portSlave;
   static String ipMaster;
   static String ipSlave;
   static int interval;

	public ConfigFile()
	{
		try
		{
		FileReader reader = new FileReader("config.properties");
		Properties properties=new Properties();
		properties.load(reader);
		String port=properties.getProperty("portMaster");
		String port2=properties.getProperty("portSlave");
		portMaster=Integer.parseInt(port);
		portSlave=Integer.parseInt(port2);
		ipMaster=properties.getProperty("ipMaster");
		ipSlave = properties.getProperty("ipSlave");
		String sInterval = properties.getProperty("interval");
		interval = Integer.parseInt(sInterval);
		
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
