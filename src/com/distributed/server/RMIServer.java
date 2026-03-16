package com.distributed.server;

import com.distributed.util.ConfigLoader;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    private static final int PORT = ConfigLoader.getInt("SERVER_PORT", 1999);
    private static final String SERVICE_NAME = ConfigLoader.get("SERVICE_NAME", "DistributedServer");

    public static void main(String[] args) {
        try {
            // Important for RMI: Tell the system WHICH IP to broadcast to clients.
            // If you are on a LAN, this should be your 192.168.x.x address.
            String publicIp = ConfigLoader.get("SERVER_PUBLIC_IP", InetAddress.getLocalHost().getHostAddress());
            System.setProperty("java.rmi.server.hostname", publicIp);

            Server server = new ServerImpl(PORT);
            
            // Create an RMI registry on the configured port (listens on 0.0.0.0 by default)
            Registry registry = LocateRegistry.createRegistry(PORT);
            
            // Bind the remote object's stub in the registry
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
