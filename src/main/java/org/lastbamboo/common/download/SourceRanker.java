package org.lastbamboo.common.download;


/**
 * Interface for classes that rank available download sources.
 */
public interface SourceRanker
    {

    /**
     * Returns whether or not this ranker has more sources to download from.
     * 
     * @return <code>true</code> if this ranker has more sources, otherwise
     * <code>false</code>.
     */
    boolean hasMoreSources();

    /**
     * Gets the best available source to download from.  The best source will
     * typically just be the fastest source.
     * 
     * @return The best source to download from.
     */
    RangeDownloader getBestSource();

    /**
     * Notifies the ranker that the given source is available for work.  This
     * will typically be called after a download worker has completed 
     * downloading its assigned range, for example.
     * 
     * @param downloader The available downloader.
     */
    void onAvailable(RangeDownloader downloader);

    void onFailed();

    }
