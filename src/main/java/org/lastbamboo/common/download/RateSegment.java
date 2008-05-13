package org.lastbamboo.common.download;

/**
 * A segment that can be used to calculate an overall rate.
 */
public interface RateSegment
    {
    /**
     * Returns the start time.
     * 
     * @return The start time.
     */
    long getStart ();
    
    /**
     * Returns the duration.
     * 
     * @return The duration.
     */
    long getDuration ();
    
    /**
     * Returns the size.
     * 
     * @return The size.
     */
    long getSize ();
    }
