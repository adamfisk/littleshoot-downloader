package org.lastbamboo.common.download;

/**
 * Interface for algorithms for selecting the size of ranges based on the 
 * number of available download sources and the size of files.
 */
public interface RangeSizeSelector
    {

    /**
     * Selects the size of ranges based on the file size and the number of
     * sources for the download.
     * 
     * @param fileSize The size of the file.
     * @param numSources The number of sources for the download.
     * @return The size to use for each chunk.
     */
    long selectSize(long fileSize, int numSources);

    }
