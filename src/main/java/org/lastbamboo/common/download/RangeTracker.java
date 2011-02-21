package org.lastbamboo.common.download;

import org.apache.commons.lang.math.LongRange;
import org.littleshoot.util.Optional;

/**
 * Interface for tracking download ranges.
 */
public interface RangeTracker
    {
    /**
     * Gets the next range to download.  This will be the highest priorty
     * range based on a range ranking that will typically preference the
     * beginning of files for streaming but that will also add randomization
     * to avoid many hosts missing the same pieces, particularly when 
     * sharing partial files.
     * 
     * @return The next range to download.
     */
    Optional<LongRange> getNextRange();

    /**
     * Returns whether or not there are available ranges to download.
     * 
     * @return <code>true</code> if there are more ranges to download, 
     * otherwise <code>false</code>.
     */
    boolean hasMoreRanges();
    
    /**
     * Called when we've completed downloading the specified range.  
     * 
     * @param range The completed range.
     */
    void onRangeComplete(LongRange range);

    /**
     * Called when the attempt to download the specified range has failed.
     * This indicates the range should be added again to the queue of ranges
     * to assign.
     * 
     * @param range The range that could not be downloaded.
     */
    void onRangeFailed(LongRange range);

    /**
     * Accessor for the number of chunks to download.
     * 
     * @return The number of chunks to download.
     */
    int getNumChunks();

    /**
     * Accessor for the number of bytes read.
     * @return The number of bytes read.
     */
    long getBytesRead();
    }
