package org.lastbamboo.download;


/**
 * Listens for connection events.
 */
public interface RangeDownloadListener
    {

    /**
     * Called when a give downloader has established a connection.
     * 
     * @param downloader The connected downloader.
     */
    void onConnect(RangeDownloader downloader);

    /**
     * Called when the download has begun.
     * 
     * @param downloader The downloader.
     */
    void onDownloadStarted(RangeDownloader downloader);

    }
