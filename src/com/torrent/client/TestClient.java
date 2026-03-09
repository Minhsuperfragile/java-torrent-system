package com.torrent.client;

import com.torrent.model.User;
import com.torrent.server.Server;
import com.torrent.util.ConfigLoader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestClient {
    public static void main(String[] args) {
        try {
            String serverIp = ConfigLoader.get("SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 1099);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "TorrentServer");

            // Locate the registry on configured IP and port
            Registry registry = LocateRegistry.getRegistry(serverIp, serverPort);
            // Lookup the remote service
            Server server = (Server) registry.lookup(serviceName);

            // Create a test user
            User testUser = new User("Alice", "127.0.0.1", 8080);
            // Call the remote method to register the user
            server.registerUser(testUser);

            System.out.println("User registered via RMI!");
            System.out.println("Registered users:");
            server.getAllUsers().forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("Test client failed: " + e.toString());
            e.printStackTrace();
        }
    }
}
