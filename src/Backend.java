import java.util.ArrayList;

public class Backend {

	public Backend(String appName, ArrayList<Instance> instances) {
		super();
		this.appName = appName;
		this.instances = instances;
	}

	private String appName;
	private ArrayList<Instance> instances; 

	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public ArrayList<Instance> getInstances() {
		return instances;
	}

	public void setInstances(ArrayList<Instance> instances) {
		this.instances = instances;
	}
		
	
	
	
}
