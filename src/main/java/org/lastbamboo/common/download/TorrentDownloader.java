package org.lastbamboo.common.download;

import java.io.File;

/**
 * Interface for torrent downloads.
 */
public interface TorrentDownloader extends StreamableDownloader
    {

    long getMaxContiguousByte();

    int getNumFiles();

    int getTorrentState();

    File getTorrentFile();

    File getIncompleteDir();

    String getUri();

    void setSeeding(boolean seeding);

    }
