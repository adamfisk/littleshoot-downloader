package org.lastbamboo.common.download;

import java.io.File;
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
    void write(OutputStream os) throws IOException;

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
     * Sets the file on disk.
     * 
     * @param file The file on disk.
     */
    void setFile(File file);

    }
