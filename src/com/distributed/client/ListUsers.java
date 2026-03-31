package com.distributed.client;

import com.distributed.server.Server;
import com.distributed.util.ConfigLoader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;

/**
 * Standalone utility to list registered users and available files.
 * Directly queries the central RMI registry without needing a daemon.
 */
public class ListUsers {
    public static void main(String[] args) {
        try {
            String serverIp = ConfigLoader.get("CENTRAL_SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 1999);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "DistributedServer");

            Registry registry = LocateRegistry.getRegistry(serverIp, serverPort);
            Server server = (Server) registry.lookup(serviceName);

            System.out.println("Current registered users:");
            server.getAllUsers().forEach(user -> {
                System.out.println("- " + user.getUsername() + " (" + user.getIpAddress() + ":" + user.getPort() + ")");
            });

            System.out.println("\nAvailable Shared Files:");
            Map<String, List<String>> fileLocations = server.getFileLocations();
            if (fileLocations.isEmpty()) {
                System.out.println("No files are currently being shared.");
            } else {
                fileLocations.forEach((filename, users) -> {
                    System.out.println("- " + filename + " (Shared by: " + String.join(", ", users) + ")");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
