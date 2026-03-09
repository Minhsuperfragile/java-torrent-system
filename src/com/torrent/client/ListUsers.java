package com.torrent.client;

import com.torrent.server.Server;
import com.torrent.util.ConfigLoader;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;

public class ListUsers {
    public static void main(String[] args) {
        try {
            String serverIp = ConfigLoader.get("SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 2099);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "TorrentServer");

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
