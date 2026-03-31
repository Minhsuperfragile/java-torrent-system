package com.distributed.server;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of the remote Server interface.
 * Manages the global state of the P2P network using thread-safe collections.
 */
public class ServerImpl extends UnicastRemoteObject implements Server {

    /** Maps usernames to User objects containing connection info and status */
    private final Map<String, User> userRegistry;

    /** Maps filenames to a set of usernames who have the file (for discovery) */
    private final Map<String, Set<String>> fileToUsers;

    /**
     * Maps usernames to the list of files they are currently sharing (for metadata
     * management)
     */
    private final Map<String, List<SharedFile>> userToFiles;

    protected ServerImpl(int port) throws RemoteException {
        super(port);
        this.userRegistry = new ConcurrentHashMap<>();
        this.fileToUsers = new ConcurrentHashMap<>();
        this.userToFiles = new ConcurrentHashMap<>();
        startJanitor();
    }

    @Override
    public synchronized void registerUser(User user) throws RemoteException {
        if (user == null || user.getUsername() == null) {
            throw new RemoteException("Invalid user information.");
        }
        userRegistry.put(user.getUsername(), user);
        System.out.println(
                "User registered: " + user.getUsername() + " (" + user.getIpAddress() + ":" + user.getPort() + ")");
    }

    @Override
    public synchronized void unregisterUser(String username) throws RemoteException {
        if (username == null) {
            throw new RemoteException("Username cannot be null.");
        }
        User removedUser = userRegistry.remove(username);
        if (removedUser != null) {

            List<SharedFile> sharedFiles = userToFiles.remove(username);
            if (sharedFiles != null) {
                for (SharedFile file : sharedFiles) {
                    Set<String> users = fileToUsers.get(file.getFilename());
                    if (users != null) {
                        users.remove(username);

                        if (users.isEmpty()) {
                            fileToUsers.remove(file.getFilename());
                        }
                    }
                }
            }
            System.out.println("User unregistered and files removed: " + username);
        } else {
            System.out.println("Unregistering failed: user " + username + " not found.");
        }
    }

    @Override
    public synchronized void publishFiles(String username, List<SharedFile> files) throws RemoteException {
        if (!userRegistry.containsKey(username)) {
            throw new RemoteException("User " + username + " not registered.");
        }

        List<SharedFile> oldFiles = userToFiles.remove(username);
        if (oldFiles != null) {
            for (SharedFile oldFile : oldFiles) {
                Set<String> users = fileToUsers.get(oldFile.getFilename());
                if (users != null) {
                    users.remove(username);
                    if (users.isEmpty()) {
                        fileToUsers.remove(oldFile.getFilename());
                    }
                }
            }
        }

        userToFiles.put(username, new ArrayList<>(files));
        for (SharedFile file : files) {

            fileToUsers.computeIfAbsent(file.getFilename(), k -> ConcurrentHashMap.newKeySet()).add(username);
        }
        System.out.println("User " + username + " published " + files.size() + " files.");
    }

    @Override
    public synchronized Map<String, List<String>> getFileLocations() throws RemoteException {
        Map<String, List<String>> result = new HashMap<>();
        fileToUsers.forEach((filename, users) -> {
            result.put(filename, new ArrayList<>(users));
        });
        return result;
    }

    @Override
    public synchronized List<User> getAllUsers() throws RemoteException {
        return new ArrayList<>(userRegistry.values());
    }

    @Override
    public synchronized SharedFile getFileMetadata(String filename) throws RemoteException {

        for (List<SharedFile> files : userToFiles.values()) {
            for (SharedFile file : files) {
                if (file.getFilename().equals(filename)) {
                    return file;
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void heartbeat(String username) throws RemoteException {
        User user = userRegistry.get(username);
        if (user != null) {
            user.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    @Override
    public synchronized void updateLoad(String username, int load) throws RemoteException {
        User user = userRegistry.get(username);
        if (user != null) {
            user.setLoad(load);
        }
    }

    /**
     * Starts a background maintenance thread (Janitor) that prunes peers
     * who haven't sent a heartbeat within the timeout period (90 seconds).
     */
    private void startJanitor() {
        Thread janitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Check every 30 seconds
                    Thread.sleep(30000);
                    long now = System.currentTimeMillis();
                    List<String> staleUsers = new ArrayList<>();

                    userRegistry.forEach((username, user) -> {
                        long diff = now - user.getLastHeartbeat();
                        // If no heartbeat for > 90s, mark for removal
                        if (diff > 90000) {
                            staleUsers.add(username);
                        }
                    });

                    for (String username : staleUsers) {
                        User user = userRegistry.get(username);
                        if (user != null) {
                            System.out.println("Janitor: Pruning stale user " + username + " (Last heartbeat: "
                                    + (now - user.getLastHeartbeat()) / 1000 + "s ago)");
                            unregisterUser(username); // Cleanly remove their records
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Janitor error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        janitorThread.setDaemon(true); // Don't prevent JVM shutdown
        janitorThread.start();
    }
}
