package org.lastbamboo.common.download;

import java.io.File;
import java.io.OutputStream;

/**
 * The interface to an object that manages the download of a single resource.
 * 
 * @param <StateT> The type of object that maintains the state.
 */
public interface Downloader<StateT>
    {
    /**
     * Starts downloading the resource.
     */
    void start ();
    
    /**
     * Returns the current state of this downloader.
     * 
     * @return The current state of this downloader. 
     */
    StateT getState ();
    
    /**
     * Returns the file to which this downloader downloads the resource.
     * 
     * @return The file to which this downloader downloads the resource.
     */
    File getIncompleteFile ();
    
    /**
     * Returns the final path for the file.
     * 
     * @return The final path for the file.
     */
    File getCompleteFile ();
    
    /**
     * Returns the size of the resource that is downloaded by this downloader.
     * 
     * @return The size of the resource that is downloaded by this downloader.
     */
    long getSize ();
    
    /**
     * Writes the resource that this downloader downloads to a given stream.
     * The download does not have to be complete for writing to occur.  This can
     * be used to stream the content of this downloader while it is still
     * downloading.
     * 
     * @param os The output stream to which to write the resource.
     * @param cancelOnStreamClose Whether or not the cancel the download 
     * when the stream closes.
     */
    void write (OutputStream os, boolean cancelOnStreamClose);
    
    /**
     * Adds a listener to be notified of events of this downloader.
     * @param listener The listener to be notified.
     */
    void addListener (DownloaderListener<StateT> listener);

    /**
     * Removes a listener that was being notified of events of this downloader.
     * 
     * @param listener The listener to remove.
     */
    void removeListener (DownloaderListener<StateT> listener);

    /**
     * Returns whether or not this downloader has already started.
     * 
     * @return <code>true</code> if this downloader has already started, 
     * otherwise <code>false</code>.
     */
    boolean isStarted();

    /**
     * The final name for the file.
     * 
     * @return The final name for the file.
     */
    String getFinalName();

    /**
     * Stops the download.
     * 
     * @param removeFiles Whether or not to also remove all downloaded files.
     */
    void stop(boolean removeFiles);

    /**
     * Pauses the download.
     */
    void pause();
    

    /**
     * Resumes the download.
     */
    void resume();

    /**
     * Accessor the time the download started.
     * 
     * @return The time the download started.
     */
    long getStartTime();
    
    }
