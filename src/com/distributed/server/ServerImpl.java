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
 * Implementation of the Server interface using RMI.
 * Acts as a central directory to keep track of users and the files they share.
 */
public class ServerImpl extends UnicastRemoteObject implements Server {
    /** Stores mapping from username to User object (IP, Port) */
    private final Map<String, User> userRegistry;
    /** Stores mapping from filename to a set of usernames who currently share it */
    private final Map<String, Set<String>> fileToUsers;
    /** Stores mapping from username to the list of files they are currently sharing */
    private final Map<String, List<SharedFile>> userToFiles;

    protected ServerImpl(int port) throws RemoteException {
        super(port);
        this.userRegistry = new ConcurrentHashMap<>();
        this.fileToUsers = new ConcurrentHashMap<>();
        this.userToFiles = new ConcurrentHashMap<>();
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
            // Cleanup: remove the user from all file-sharing sets
            List<SharedFile> sharedFiles = userToFiles.remove(username);
            if (sharedFiles != null) {
                for (SharedFile file : sharedFiles) {
                    Set<String> users = fileToUsers.get(file.getFilename());
                    if (users != null) {
                        users.remove(username);
                        // If no one else has this file, remove the entry from global list
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

        // 1. Remove previous entries for this user to ensure we have a fresh list
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

        // 2. Add new file listings
        userToFiles.put(username, new ArrayList<>(files));
        for (SharedFile file : files) {
            // Update the global file-to-users map
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

    /**
     * Searches all shared file lists to find the metadata (size, hashes) 
     * for a given filename.
     */
    @Override
    public synchronized SharedFile getFileMetadata(String filename) throws RemoteException {
        // Linear search for metadata. In a larger system, this would be index-optimized.
        for (List<SharedFile> files : userToFiles.values()) {
            for (SharedFile file : files) {
                if (file.getFilename().equals(filename)) {
                    return file;
                }
            }
        }
        return null;
    }
}
