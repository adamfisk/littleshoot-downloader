package org.lastbamboo.common.download;

/**
 * A listener for downloaders.
 * 
 * @param <StateT> The downloader state type.
 */
public interface DownloaderListener<StateT>
    {
    /**
     * Notification that the downloader state has changed.
     * 
     * @param state The new downloader state.
     */
    void stateChanged (StateT state);
    }
