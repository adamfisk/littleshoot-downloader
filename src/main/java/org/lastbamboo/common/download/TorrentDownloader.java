package org.lastbamboo.common.download;

/**
 * Interface for torrent downloads.
 */
public interface TorrentDownloader
    {

    long getMaxContiguousByte();

    int getNumFiles();

    }
