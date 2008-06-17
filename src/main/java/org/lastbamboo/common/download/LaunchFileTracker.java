package org.lastbamboo.common.download;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang.math.LongRange;


/**
 * Tracker for the file to launch for immediate viewing.
 */
public interface LaunchFileTracker
    {

    /**
     * Writes the file to the specified stream.
     * 
     * @param os The stream to write to.
     * @throws IOException If there's any read or write error writing to the
     * stream.
     */
    void write(OutputStream os, boolean cancelOnStreamClose) throws IOException;

    /**
     * Called when a specific range is complete.
     * 
     * @param range The completed range.
     */
    void onRangeComplete(LongRange range);
    
    /**
     * Called when the file download has completed.
     */
    void onFileComplete();

    /**
     * Waits until all active launcher have finished their writes, typically
     * to the browser.
     */
    void waitForLaunchersToComplete();

    /**
     * Accessor for the number of active writers.
     * 
     * @return The number of active writers.
     */
    int getActiveWriteCalls();

    /**
     * Called when a download fails for any reason.
     */
    void onFailure();

    }
