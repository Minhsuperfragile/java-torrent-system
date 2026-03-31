package com.distributed.server;

import com.distributed.util.ConfigLoader;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Entry point for the central RMI Server.
 * Initializes the RMI registry and binds the DistributedServer service.
 */
public class RMIServer {
    /** Port for the RMI registry, defaults to 1999 if not in .env */
    private static final int PORT = ConfigLoader.getInt("SERVER_PORT", 1999);
    /** Name used to identify the service in the RMI registry */
    private static final String SERVICE_NAME = ConfigLoader.get("SERVICE_NAME", "DistributedServer");

    public static void main(String[] args) {
        try {
            
            
            String publicIp = ConfigLoader.get("SERVER_PUBLIC_IP", InetAddress.getLocalHost().getHostAddress());
            System.setProperty("java.rmi.server.hostname", publicIp);

            Server server = new ServerImpl(PORT);
            
            
            Registry registry = LocateRegistry.createRegistry(PORT);
            
            
            registry.rebind(SERVICE_NAME, server);

            System.out.println("==============================================");
            System.out.println("Distributed RMI Server is UP");
            System.out.println("Registry Port: " + PORT);
            System.out.println("Service Name: " + SERVICE_NAME);
            System.out.println("Exposed IP (Local): " + publicIp);
            System.out.println("Other machines can connect using: " + publicIp + ":" + PORT);
            System.out.println("==============================================");
            
        } catch (Exception e) {
            System.err.println("Distributed Server failed to start: " + e.toString());
            e.printStackTrace();
        }
    }
}
