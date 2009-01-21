package org.lastbamboo.common.download;


/**
 * Interface for downloaders that are visitable by visitors.
 * 
 * @param <StateT> The type of object that maintains the state.
 */
public interface VisitableDownloader<StateT> extends Downloader<StateT>
    {

    /**
     * Accepts the specified visitor class.
     * 
     * @param <T> The type the visitor will return.
     * @param visitor The visitor to accept.
     * @return The return value of the visitor. 
     */
    <T> T accept(DownloadVisitor<T> visitor);

    }
