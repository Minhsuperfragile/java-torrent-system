package com.distributed.client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for the Peer Daemon.
 * Allows a CLI client to communicate with the background daemon process.
 */
public interface ClientInterface extends Remote {
    /**
     * Instructs the daemon to start downloading a file.
     */
    void downloadFile(String filename) throws RemoteException;

    /**
     * Queries the daemon for available files in the network.
     * @return A map of filename to list of usernames sharing it.
     */
    Map<String, List<String>> getFileLocations() throws RemoteException;

    /**
     * Returns the username of this peer.
     */
    String getUsername() throws RemoteException;

    /**
     * Shuts down the daemon process.
     */
    void shutdown() throws RemoteException;
}
