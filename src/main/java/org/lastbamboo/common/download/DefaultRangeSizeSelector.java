package org.lastbamboo.common.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for selecting the size of download ranges based on the size of a file
 * and the number of available sources.
 */
public class DefaultRangeSizeSelector implements RangeSizeSelector
    {
    
    private final Logger m_log = LoggerFactory.getLogger(getClass());

    /**
     * The maximum size for chunks.
     */
    public static final long MAX_CHUNK_SIZE = 1024 * 512;
    
    /**
     * The minimum size for chunks.
     */
    public static final long MIN_CHUNK_SIZE = 1024 * 30;
    
    /**
     * This is the factor that takes into account that different sources
     * provide different download speeds.  The extreme of this is a centralized
     * CDN source versus a peer on a modem link, for example.  We need to
     * choose small enough chunks so that slower downloaders don't screw things
     * up for the download as a whole.
     */
    public static final long DIFFERENTIAL_SPEED_FACTOR = 10;
    
    public long selectSize(final long fileSize, final int numSources)
        {
        // The logic here is to give each source a chunk but then to take
        // into account that sources download at difference rates.  We 
        // take a shot in the dark and say this could be a significant
        // differential, and we just take a stab at it.  It's
        // generally better to have too many chunks than too few, however,
        // because a slow downloader can screw up the works more with larger
        // chunks.  The ideal is to always dynamically determine the chunk 
        // size, but we'll save that for later.
        
        final long theoreticalChunkSize = 
            (long) Math.ceil((fileSize/numSources)/DIFFERENTIAL_SPEED_FACTOR);
        
        // Make sure the chunk size isn't way too big...
        final long upperCapChunkSize = 
            Math.min(MAX_CHUNK_SIZE, theoreticalChunkSize);
        // ..and make sure the chunk size isn't way too small.
        final long lowerCapChunkSize = 
            Math.max(upperCapChunkSize, MIN_CHUNK_SIZE);
        // Finally, make sure it's not bigger than the file.
        final long finalSize;
        if (numSources == 1)
            {
            finalSize = fileSize;
            }
        else
            {
            finalSize = Math.min(fileSize, lowerCapChunkSize);
            }
        
        m_log.debug("Size for "+numSources+" source(s) for file of size "+
            fileSize+" is "+finalSize);
        
        m_log.debug("Differential speed factor: ", 
            fileSize/(finalSize*numSources));
        return finalSize;
        }

    }
