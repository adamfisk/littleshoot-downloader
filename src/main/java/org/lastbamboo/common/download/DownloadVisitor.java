package org.lastbamboo.common.download;


/**
 * Top level visitor for download types.
 * 
 * @param <T> The result of visitations.
 */
public interface DownloadVisitor<T>
    {

    T visitTorrentDownloader(TorrentDownloader libTorrentDownloader);

    T visitGnutellaDownloader(GnutellaDownloader gnutellaDownloader);

    T visitLittleShootDownloader(LittleShootDownloader littleShootDownloader);

    }
