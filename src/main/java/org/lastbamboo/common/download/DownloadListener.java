package org.lastbamboo.common.download;


/**
 * Listener interface for download events, such as the download completing.
 */
public interface DownloadListener
    {

    /**
     * Called when a download is complete.
     * 
     * @param dl The completed downloader.
     */
    void onDownloadComplete(Downloader dl);

    /**
     * Called when a download is cancelled.
     * 
     * @param dl The cancelled download.
     */
    void onDownloadCancelled(Downloader dl);

    }
