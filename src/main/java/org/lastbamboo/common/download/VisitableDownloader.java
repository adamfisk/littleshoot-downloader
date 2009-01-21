package org.lastbamboo.common.download;

/**
 * Interface for downloaders that are visitable by visitors.
 */
public interface VisitableDownloader
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
