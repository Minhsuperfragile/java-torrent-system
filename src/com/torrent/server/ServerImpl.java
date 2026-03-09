package com.torrent.server;

import com.torrent.model.SharedFile;
import com.torrent.model.User;
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

public class ServerImpl extends UnicastRemoteObject implements Server {
    private final Map<String, User> userRegistry;
    // Map of filename -> set of usernames who have this file
    private final Map<String, Set<String>> fileToUsers;
    // Map of username -> list of files they are sharing
    private final Map<String, List<SharedFile>> userToFiles;

    protected ServerImpl() throws RemoteException {
        super();
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
            // Remove user from file listings
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

        // Remove old entries for this user
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

        // Add new entries
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
}
