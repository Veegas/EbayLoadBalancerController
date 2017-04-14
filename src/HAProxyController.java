import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

public class HAProxyController {

    private String executablePath;
    private String configuration;
    private int listeningPort = 80;

    public HAProxyController(String executablePath, int listeningPort) {
        this.executablePath = executablePath;
        if (!this.executablePath.endsWith("haproxy")) {
            if (!this.executablePath.endsWith("/")) {
                this.executablePath += "/";
            }
            this.executablePath += "haproxy";
        }

        // Kill all running haproxy processes
        // TODO: this probably isn't good
        try {
            String command = "killall haproxy";
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Set listening port
        this.listeningPort = listeningPort;
    }

    public void generateConfiguration(Vector<Backend> backends)
            throws Exception {

        // Disable logs (Velocity will cause errors if this isn't done)
        Properties properties = new Properties();
        properties.setProperty("log4j.threshold", "WARN");
        org.apache.log4j.PropertyConfigurator.configure(properties);

        // Create the strings that are to be inserted

        String newConfiguration = "";
        String backendsString = "";
        String aclString = "";
        String usageString = "";

        // Make a list to work with
        HashMap<String, Set<Instance>> appInstances = new HashMap<String, Set<Instance>>();
        for (Backend backend : backends) {
            	String appName = backend.getAppName();
        	
                if (!appInstances.containsKey(appName)) {
                    Set<Instance> b = new HashSet<Instance>();
                    b.addAll(backend.getInstances());
                    appInstances.put(appName, b);
                } else {
                    Set<Instance> b = appInstances.get(appName);
                    b.addAll(backend.getInstances());
                }
            }
  
        // Create groups
        Set<Set<Instance>> backendGroups = new HashSet<Set<Instance>>();
        for (Set<Instance> b : appInstances.values()) {
            backendGroups.add(b);
        }
        for (Backend b : backends) {
            String backendName = "\tbackend ";
            String servers = "";
            backendName += b.getAppName();
            for (Instance instance: b.getInstances()) {
                servers += "\t\tserver " + instance.getInstanceId() + "\t" +instance.getHost() + ":"
                        + instance.getPort() + "\n";
            }
            backendsString += backendName + "\n";
            backendsString += servers + "\n";
        }

        // Create acl's
        for (String appName : appInstances.keySet()) {
            aclString += "\tacl " + "acl_" + appName + " path_beg " + "/"
                    + appName + "\n";
        }

        // Create usage strings
        for (String appName : appInstances.keySet()) {
            String instanceName = "";
            usageString += "\tuse_backend " + appName + " if " + "acl_"
                    + appName + "\n";
        }

        // Start the VelocityEngine
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        ve.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
        ve.init();

        // Open the template
        Template t = ve.getTemplate(
                "haproxy.conf_template", "UTF-8");

        // Insert the strings into the template
        VelocityContext context = new VelocityContext();
        context.put("listen_port", listeningPort);
        context.put("backends", backendsString);
        context.put("backenduse", usageString);
        context.put("acl", aclString);

        // Create a new string from the template
        StringWriter stringWriter = new StringWriter();
        t.merge(context, stringWriter);
        configuration = stringWriter.toString();

        // Write configuration to file
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(
                    "/tmp/haproxy.conf"));
            writer.write(configuration);
            writer.close();

            System.out.println("wrote configuration to file");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void reconfigure() throws Exception {

        // Read pid
        String pid = "";
        BufferedReader reader = new BufferedReader(new FileReader(
                "/tmp/haproxy.pid"));
        String line;
        while ((line = reader.readLine()) != null) {
            pid += line + " ";
        }

        // Perform hot reconfiguration of haproxy
        BufferedReader input = null;
        try {
            String command = executablePath
                    + " -f /tmp/haproxy.conf -p /tmp/haproxy.pid -sf " + pid;
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(command);
            InputStream in = p.getInputStream();
           input = new BufferedReader(new InputStreamReader(p
                    .getInputStream()));
            // while ((line = input.readLine()) != null) {
            // System.out.println(line);
            // }
            System.out.println("reconfigured haproxy");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(input);
            // TODO: something
        }

        // TODO: Check if it the reconfiguration actually happened and throw
        // error if necessary
    }

    public void startLoadBalancer() throws Exception {

        // Check for configuration file
        File conf;
        conf = new File("/tmp/haproxy.conf");
        if (!conf.exists()) {
            System.err.println("Could not find haproxy configuration file!");
            throw new Exception();
        }

        // Start haproxy and write pid to /tmp/haproxy.pid
        try {
            String command = executablePath
                    + " -f /tmp/haproxy.conf -p /tmp/haproxy.pid";
            Runtime r = Runtime.getRuntime();
            Process p = r.exec(command);
            System.out.println("started haproxy");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void stopLoadBalancer() throws Exception {

        // Read the PID's
        Vector<String> pids = new Vector<String>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(
                    "/tmp/haproxy.pid"));
            String pid = null;
            while ((pid = reader.readLine()) != null) {
                pids.add(pid);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // Kill all haproxy processes
        for (String pid : pids) {
            try {
                String command = "kill -s 9" + pid;
                Runtime r = Runtime.getRuntime();
                Process p = r.exec(command);
                System.out.println("stopped haproxy");
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }
    
    public static void main (String [] args) {
    	
    	ArrayList<Instance> instances = new ArrayList<Instance>();
    	Instance a1 = new Instance(3200, "192.168.1.18", "instance1");
    	Instance a2 = new Instance(3001, "127.0.0.1", "instance2");
    	Instance a3 = new Instance(3002, "127.0.0.1", "instance3");
    	instances.add(a1);
    	instances.add(a2);
    	instances.add(a3);
    	
    	Backend backend = new Backend("get" , instances);
    	Vector<Backend> backends = new Vector<Backend>();
    	backends.add(backend);
    	
    	HAProxyController controller = new HAProxyController("/usr/sbin/haproxy", 8080);
    	try {
			controller.generateConfiguration(backends);
			controller.reconfigure();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
