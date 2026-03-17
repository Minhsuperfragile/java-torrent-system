package com.distributed.server;

import com.distributed.model.SharedFile;
import com.distributed.model.User;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface Server extends Remote {
    /**
     * Registers a new user to the system.
     * 
     * @param user the User to register
     * @throws RemoteException if there is a RMI related error
     */
    void registerUser(User user) throws RemoteException;

    /**
     * Unregisters a user from the system based on their username.
     * 
     * @param username the username of the user to unregister
     * @throws RemoteException if there is a RMI related error
     */
    void unregisterUser(String username) throws RemoteException;

    /**
     * Publishes a list of files that a peer is sharing.
     * 
     * @param username the username of the peer
     * @param files    the list of files being shared
     * @throws RemoteException if there is a RMI related error
     */
    void publishFiles(String username, List<SharedFile> files) throws RemoteException;

    /**
     * Returns a map of all shared files and the users who possess them.
     * 
     * @return a map from filename to its list of users
     * @throws RemoteException if there is a RMI related error
     */
    Map<String, List<String>> getFileLocations() throws RemoteException;

    /**
     * Returns a list of all registered users.
     * 
     * @return a list of Users
     * @throws RemoteException if there is a RMI related error
     */
    List<User> getAllUsers() throws RemoteException;

    /**
     * Returns the metadata of a shared file.
     * 
     * @param filename the name of the file
     * @return the SharedFile metadata
     * @throws RemoteException if there is a RMI related error
     */
    SharedFile getFileMetadata(String filename) throws RemoteException;
    /**
     * Updates the heartbeat timestamp for a peer to keep them active in the directory.
     * 
     * @param username the username of the peer
     * @throws RemoteException if there is a RMI related error
     */
    void heartbeat(String username) throws RemoteException;

    /**
     * Updates the current load (active transfers) for a peer.
     * 
     * @param username the username of the peer
     * @param load     the number of active incoming transfers
     * @throws RemoteException if there is a RMI related error
     */
    void updateLoad(String username, int load) throws RemoteException;
}
