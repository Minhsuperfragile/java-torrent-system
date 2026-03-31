package com.distributed.server;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for the central directory server.
 * Defines methods for peer registration, metadata publishing, and discovery.
 */
public interface Server extends Remote {
     
    /**
     * Registers a new peer in the system.
     */
    void registerUser(User user) throws RemoteException;

    /**
     * Removes a peer and their shared files from the registry.
     */
    void unregisterUser(String username) throws RemoteException;

    /**
     * Updates the list of files being shared by a specific user.
     */
    void publishFiles(String username, List<SharedFile> files) throws RemoteException;

    /**
     * Returns a map of filenames to lists of usernames who possess that file.
     */
    Map<String, List<String>> getFileLocations() throws RemoteException;

    /**
     * Returns a list of all currently registered peers.
     */
    List<User> getAllUsers() throws RemoteException;

    /**
     * Retrieves full metadata (hashes, size) for a specific file.
     */
    SharedFile getFileMetadata(String filename) throws RemoteException;

    /**
     * Updates the liveness timestamp for a user.
     */
    void heartbeat(String username) throws RemoteException;

    /**
     * Updates the current connection load for a user (used for load balancing).
     */
    void updateLoad(String username, int load) throws RemoteException;
}
