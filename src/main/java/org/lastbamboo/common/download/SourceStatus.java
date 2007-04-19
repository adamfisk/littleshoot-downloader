package org.lastbamboo.common.download;

/**
 * The download status for a single source.
 */
public interface SourceStatus
    {
    /**
     * Returns a name for the source with which this status is associated.  For
     * example, this might return the IP address string of the source.
     *  
     * @return
     *      A name for the source with which this status is assocatied.
     */
    String getName
            ();
    
    /**
     * Returns the speed of the download from the source with which this status
     * is associated in kilobytes per second.
     * 
     * @return
     *      The speed of the download from the source with which this status is
     *      associated in kilobytes per second.
     */
    int getKbs
            ();
    
    /**
     * Returns the progress for the download from the source with which this
     * status is associated.  Values range from 0.0 to 1.0, where 1.0 indicates
     * completion.
     * 
     * @return
     *      The progress for the download from the source with which this status
     *      is associated.  Values range from 0.0 to 1.0, where 1.0 indicates
     *      completion.
     */
    double getProgress
            ();
    }
