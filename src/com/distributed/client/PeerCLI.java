package com.distributed.client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;

/**
 * CLI application that connects to a local PeerDaemon.
 */
public class PeerCLI {
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        // We assume the daemon is on localhost. 
        // We can optionally pass the RMI port as an argument or use default 1998.
        int rmiPort = com.distributed.util.ConfigLoader.getInt("CLIENT_RMI_PORT", 1998);
        int commandStartIndex = 0;

        // Check if the first argument is a port number (optional)
        try {
            if (args.length > 1 && args[0].matches("\\d+")) {
                rmiPort = Integer.parseInt(args[0]);
                commandStartIndex = 1;
            }
        } catch (NumberFormatException ignored) {}

        if (commandStartIndex >= args.length) {
            printUsage();
            return;
        }

        String command = args[commandStartIndex];

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", rmiPort);
            ClientInterface daemon = (ClientInterface) registry.lookup("PeerDaemon");

            switch (command.toLowerCase()) {
                case "list":
                    Map<String, List<String>> locations = daemon.getFileLocations();
                    System.out.println("Available files in network:");
                    if (locations.isEmpty()) {
                        System.out.println("No files found.");
                    } else {
                        locations.forEach((filename, users) -> {
                            System.out.println(" - " + filename + " (available from: " + String.join(", ", users) + ")");
                        });
                    }
                    break;

                case "download":
                    if (args.length < commandStartIndex + 2) {
                        System.out.println("Usage: download <filename>");
                        return;
                    }
                    String filename = args[commandStartIndex + 1];
                    daemon.downloadFile(filename);
                    System.out.println("Download request sent for: " + filename);
                    System.out.println("Check daemon logs for progress.");
                    break;

                case "info":
                    System.out.println("Connected to Peer Daemon for user: " + daemon.getUsername());
                    break;

                case "stop":
                    daemon.shutdown();
                    System.out.println("Stop command sent to daemon.");
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    printUsage();
            }

        } catch (Exception e) {
            System.err.println("Error connecting to Peer Daemon: " + e.getMessage());
            System.err.println("Is the daemon running on port " + rmiPort + "?");
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java com.distributed.client.PeerCLI [rmi_port] <command> [args]");
        System.out.println("Commands:");
        System.out.println("  list                - List all files available in the network");
        System.out.println("  download <filename> - Request the daemon to download a file");
        System.out.println("  info                - Get information about the connected daemon");
        System.out.println("  stop                - Shutdown the daemon process");
    }
}
