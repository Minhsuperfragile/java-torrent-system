package com.torrent.client;

import com.torrent.model.SharedFile;
import com.torrent.model.User;
import com.torrent.server.Server;
import com.torrent.util.ConfigLoader;
import com.torrent.util.FileUtil;
import java.io.File;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

public class Peer {
    private final String username;
    private final int port;
    private final String sharedFolderPath;
    private Server server;

    public Peer(String username, int port) {
        this(username, port, "./shared/" + username);
    }

    public Peer(String username, int port, String sharedFolderPath) {
        this.username = username;
        this.port = port;
        this.sharedFolderPath = sharedFolderPath;
        File sharedFolder = new File(this.sharedFolderPath);
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs();
        }
    }

    public void start() {
        try {
            String serverIp = ConfigLoader.get("SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 2099);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "TorrentServer");

            // Locate the registry on configured IP and port
            Registry registry = LocateRegistry.getRegistry(serverIp, serverPort);
            // Lookup the remote service
            server = (Server) registry.lookup(serviceName);

            // Get local IP address
            String localIp = InetAddress.getLocalHost().getHostAddress();
            User user = new User(username, localIp, port);

            // Register user
            server.registerUser(user);
            System.out.println("Client application started and registered: " + username);

            // Initial publish of files
            publishSharedFiles();

            // Add shutdown hook to unregister when application stops
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.unregisterUser(username);
                    System.out.println("\nClient unregistered from server: " + username);
                } catch (Exception e) {
                    System.err.println("Failed to unregister user: " + e.getMessage());
                }
            }));

            // Keep the process alive and periodically re-scan files (optional)
            System.out.println("Daemon process is running with shared folder: " + sharedFolderPath);
            System.out.println("Press Ctrl+C to stop.");
            while (true) {
                Thread.sleep(30000); // Re-scan every 30 seconds
                publishSharedFiles();
            }

        } catch (Exception e) {
            System.err.println("Error in ClientDaemon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void publishSharedFiles() {
        try {
            File folder = new File(sharedFolderPath);
            File[] files = folder.listFiles();
            List<SharedFile> sharedFilesList = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String filename = file.getName();
                        long fileSize = file.length();
                        String fileHash = FileUtil.calculateFileHash(file.getAbsolutePath());
                        List<String> pieceHashes = FileUtil.calculatePieceHashes(file.getAbsolutePath());
                        sharedFilesList.add(new SharedFile(filename, fileSize, fileHash, pieceHashes));
                    }
                }
            }

            if (!sharedFilesList.isEmpty()) {
                server.publishFiles(username, sharedFilesList);
                System.out.println("Updated server with " + sharedFilesList.size() + " published files.");
            }
        } catch (Exception e) {
            System.err.println("Error publishing shard files: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java com.torrent.client.ClientDaemon <username> <port>");
            System.exit(1);
        }

        String username = args[0];
        int port = Integer.parseInt(args[1]);

        Peer peer = new Peer(username, port);
        peer.start();
    }
}
