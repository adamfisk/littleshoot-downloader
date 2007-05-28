package org.lastbamboo.common.download;

import java.net.URI;

import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.util.Optional;

/**
 * Interface for classes that can initiate downloads.
 */
public interface RangeDownloader
    {
    /**
     * Gets the speed of this downloader in kilobytes per second.
     * @return The speed of this downloader in kilobytes per second.
     */
    Optional<Integer> getKbs();
    
    /**
     * Returns the number of bytes downloaded by this downloader.
     * 
     * @return
     *      The number of bytes downloaded by this downloader.
     */
    long getNumBytesDownloaded
            ();

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
     * Returns the source URI for this downloader.
     * 
     * @return
     *      The source URI for this downloader.
     */
    URI getSourceUri
            ();
    }
