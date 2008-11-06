package org.lastbamboo.common.download;


/**
 * An interface to an object that helps calculate rates.
 */
public interface RateCalculator
    {
    /**
     * Returns the rate since a given time.
     *      
     * @return The rate since the given time.
     */
    double getRate ();

    /**
     * Adds data for the given downloader.
     * 
     * @param downloader The downloader to add data for.
     */
    void addData(RangeDownloader downloader);

    /**
     * Accessor for the total number of bytes read.
     * 
     * @return The total number of bytes read.
     */
    long getBytesRead();
    }
