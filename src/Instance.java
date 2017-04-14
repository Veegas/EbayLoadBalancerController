
public class Instance {


	private int port;
	private String host;
	private String instanceId;
	
	public Instance(int port, String host, String instanceId) {
		super();
		this.port = port;
		this.host = host;
		this.instanceId = instanceId;
	}

	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	
	public int getPort() {
		return port;
	}

	public String getInstanceId() {
		return instanceId;
	}
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	public void setPort(int port) {
		this.port = port;
	}
	
	
}
