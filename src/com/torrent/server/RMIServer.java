package com.torrent.server;

import com.torrent.util.ConfigLoader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    private static final int PORT = ConfigLoader.getInt("SERVER_PORT", 1099);
    private static final String SERVICE_NAME = ConfigLoader.get("SERVICE_NAME", "TorrentServer");

    public static void main(String[] args) {
        try {
            Server server = new ServerImpl();
            // Create an RMI registry on the configured port
            Registry registry = LocateRegistry.createRegistry(PORT);
            // Bind the remote object's stub in the registry
            registry.rebind(SERVICE_NAME, server);
            System.err.println("Torrent RMI Server is ready on port " + PORT + " as \"" + SERVICE_NAME + "\"...");
        } catch (Exception e) {
            System.err.println("Torrent Server failed to start: " + e.toString());
            e.printStackTrace();
        }
    }
}
