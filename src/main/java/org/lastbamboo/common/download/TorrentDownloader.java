package org.lastbamboo.common.download;

/**
 * Interface for torrent downloads.
 */
public interface TorrentDownloader extends StreamableDownloader
    {

    long getMaxContiguousByte();

    int getNumFiles();

    }
