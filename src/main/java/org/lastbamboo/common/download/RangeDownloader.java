package org.lastbamboo.common.download;

import org.apache.commons.lang.math.LongRange;

/**
 * Interface for classes that can initiate downloads.
 */
public interface RangeDownloader
    {

    /**
     * Gets the speed of this downloader in kilobytes per second.
     * @return The speed of this downloader in kilobytes per second.
     */
    int getKbs();

    /**
     * Downloads the specified range from the assigned file.
     * 
     * @param range The byte range to download.
     */
    void download(LongRange range);
    
    /**
     * Sends a head request to the server this downloader is downloading from.
     */
    void issueHeadRequest();

    /**
     * Accessor for the content type returned from the server.
     * 
     * @return The content type HTTP header returned from the server for this
     * range.
     */
    String getContentType();

    }
