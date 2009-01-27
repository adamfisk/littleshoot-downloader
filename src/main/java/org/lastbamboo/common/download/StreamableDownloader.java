package org.lastbamboo.common.download;

/**
 * Super-interface for downloaders that may or may not be capable of streaming.
 */
public interface StreamableDownloader
    {

    /**
     * Returns whether or not this downloader can be streamed.
     * 
     * @return <code>true</code> if the downloader can be streamed, otherwise
     * <code>false</code>.
     */
    boolean isStreamable();
    }
