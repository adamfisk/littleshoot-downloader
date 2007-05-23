package org.lastbamboo.common.download;

/**
 * Downloader state types.
 */
public enum DownloaderStateType
    {
    /**
     * Indicates that a given downloader state signals that the downloader is
     * running.
     */
    RUNNING,
    
    /**
     * Indicates that a given downloader state signals that the downloader has
     * failed.
     */
    FAILED,
    
    /**
     * Indicates that a given downloader state signals that the downloader has
     * succeeded.
     */
    SUCCEEDED
    }
