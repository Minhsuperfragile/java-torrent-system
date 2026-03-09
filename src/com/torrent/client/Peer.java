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
import java.util.Map;
import java.util.Scanner;

/**
 * Represents a Peer in the Torrent system.
 * A Peer acts as both a Client (downloading files) and a Server (sharing files).
 * It communicates with the RMIServer for file discovery and uses TCP for file transfers.
 */
public class Peer {
    private final String username;
    private final int port;
    private final String sharedFolderPath;
    private Server server;

    /**
     * Constructs a Peer with a default shared folder path.
     */
    public Peer(String username, int port) {
        this(username, port, "./shared/" + username);
    }

    /**
     * Constructs a Peer with a specific shared folder path.
     */
    public Peer(String username, int port, String sharedFolderPath) {
        this.username = username;
        this.port = port;
        this.sharedFolderPath = sharedFolderPath;
        File sharedFolder = new File(this.sharedFolderPath);
        if (!sharedFolder.exists()) {
            sharedFolder.mkdirs(); // Ensure the shared directory exists
        }
    }

    /**
     * Starts the peer application:
     * 1. Connects to RMIServer.
     * 2. Starts the local TCP FileTransferServer.
     * 3. Registers with the central registry.
     * 4. Enters an interactive CLI loop.
     */
    public void start() {
        try {
            // Load configuration from .env or use defaults
            String serverIp = ConfigLoader.get("SERVER_IP", "localhost");
            int serverPort = ConfigLoader.getInt("SERVER_PORT", 2099);
            String serviceName = ConfigLoader.get("SERVICE_NAME", "TorrentServer");

            // RMI Lookup: Get the remote Server object
            Registry registry = LocateRegistry.getRegistry(serverIp, serverPort);
            server = (Server) registry.lookup(serviceName);

            // Identify local IP for other peers to connect to us
            String localIp = InetAddress.getLocalHost().getHostAddress();
            User user = new User(username, localIp, port);

            // 1. Peer-as-Server: Start TCP listener for file requests
            new Thread(new FileTransferServer(port, sharedFolderPath)).start();

            // 2. Register user with Central RMI Server
            server.registerUser(user);
            System.out.println("Client application started and registered: " + username);

            // 3. Initial scan and publishing of local files
            publishSharedFiles();

            // Shutdown Hook: Cleanly unregister from server when app is closed (Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.unregisterUser(username);
                    System.out.println("\nClient unregistered from server: " + username);
                } catch (Exception e) {
                    System.err.println("Failed to unregister user: " + e.getMessage());
                }
            }));

            // 4. Interactive CLI and Periodic Publisher
            System.out.println("Daemon process is running with shared folder: " + sharedFolderPath);
            System.out.println("Commands: download <filename>, list, exit");
            
            Scanner scanner = new Scanner(System.in);
            
            // Background thread to periodically refresh shared file list on the server
            Thread publisherThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(60000); // Re-scan every 1 minute
                        publishSharedFiles();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            publisherThread.setDaemon(true);
            publisherThread.start();

            // Main UI loop
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("download ")) {
                    String filename = line.substring(9).trim();
                    // Start download in a background thread to keep UI responsive
                    new Thread(() -> downloadFile(filename)).start();
                } else if (line.equals("list")) {
                    // Query server for all available files across the network
                    Map<String, List<String>> locations = server.getFileLocations();
                    System.out.println("Available files:");
                    locations.forEach((fname, users) -> {
                        System.out.println(" - " + fname + " (available from: " + String.join(", ", users) + ")");
                    });
                } else if (line.equals("exit")) {
                    System.exit(0);
                } else {
                    System.out.println("Unknown command. Use: download <filename>, list, exit");
                }
            }

        } catch (Exception e) {
            System.err.println("Error in ClientDaemon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Scans the shared folder, calculates hashes for all files, 
     * and sends the metadata to the RMIServer.
     */
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
                        // Generate hashes for integrity tracking
                        String fileHash = FileUtil.calculateFileHash(file.getAbsolutePath());
                        List<String> pieceHashes = FileUtil.calculatePieceHashes(file.getAbsolutePath());
                        sharedFilesList.add(new SharedFile(filename, fileSize, fileHash, pieceHashes));
                    }
                }
            }

            // Inform the RMI server what files we are currently sharing
            if (!sharedFilesList.isEmpty()) {
                server.publishFiles(username, sharedFilesList);
                System.out.println("Updated server with " + sharedFilesList.size() + " published files.");
            }
        } catch (Exception e) {
            System.err.println("Error publishing shard files: " + e.getMessage());
        }
    }

    /**
     * Peer-as-Client: Initiates a parallel download.
     * 1. Fetches file metadata (piece hashes) from server.
     * 2. Finds all peers that have the file.
     * 3. Orchestrates piece requests using FileDownloader.
     * 
     * @param filename Name of the file to download.
     */
    public void downloadFile(String filename) {
        try {
            System.out.println("Searching for file: " + filename);
            // Get file details (size, hashes) from server
            SharedFile metadata = server.getFileMetadata(filename);
            if (metadata == null) {
                System.err.println("File not found on server.");
                return;
            }

            // Find who owns the file
            Map<String, List<String>> locations = server.getFileLocations();
            List<String> usernames = locations.get(filename);
            if (usernames == null || usernames.isEmpty()) {
                System.err.println("No peers found sharing this file.");
                return;
            }

            // Build a list of User objects (IP/Port) for these peers
            List<User> allUsers = server.getAllUsers();
            List<User> sourcePeers = new ArrayList<>();
            for (String uname : usernames) {
                if (uname.equals(this.username)) continue; // Skip self
                for (User u : allUsers) {
                    if (u.getUsername().equals(uname)) {
                        sourcePeers.add(u);
                        break;
                    }
                }
            }

            if (sourcePeers.isEmpty()) {
                System.err.println("No other peers found sharing this file.");
                return;
            }

            // Start the parallel download orchestrator
            FileDownloader downloader = new FileDownloader(metadata, sourcePeers, sharedFolderPath);
            if (downloader.download()) {
                System.out.println("Download of " + filename + " successful!");
                // Immediately publish the new file so we can share it too!
                publishSharedFiles();
            } else {
                System.err.println("Download of " + filename + " failed.");
            }

        } catch (Exception e) {
            System.err.println("Error initiating download: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java com.torrent.client.Peer <username> <port>");
            System.exit(1);
        }

        String username = args[0];
        int port = Integer.parseInt(args[1]);

        Peer peer = new Peer(username, port);
        peer.start();
    }
}
