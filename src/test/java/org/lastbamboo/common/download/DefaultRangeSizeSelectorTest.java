package org.lastbamboo.common.download;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test for the class that chooses the size of download chunks
 */
public class DefaultRangeSizeSelectorTest
    {

    @Test public void testDefaultRangeSizeSelector() throws Exception
        {
        final DefaultRangeSizeSelector selector = 
            new DefaultRangeSizeSelector();
        long fileSize = 1000000L;
        
        // We do a special check for single source.
        long rangeSize = selector.selectSize(fileSize, 1);
        assertEquals(fileSize, rangeSize);

        for (int numRuns = 1; numRuns < 10; numRuns++)
            {
            final long curFileSize = (long) Math.pow(1000, numRuns); 
            for (int numSources = 2; numSources < 100; numSources++)
                {
                runCheck(curFileSize, numSources);
                }
            }
        }

    private void runCheck(long fileSize, int numSources)
        {
        final DefaultRangeSizeSelector selector = 
            new DefaultRangeSizeSelector();
        final long rangeSize = selector.selectSize(fileSize, numSources);
        
        final String chunkSizeMessage =
            "Unexpected chunk size: "+rangeSize+" for file size "+fileSize+" and num sources "+numSources;
        assertTrue(chunkSizeMessage, 
            rangeSize <= DefaultRangeSizeSelector.MAX_CHUNK_SIZE);
        
        if (fileSize < DefaultRangeSizeSelector.MIN_CHUNK_SIZE)
            {
            assertEquals(chunkSizeMessage, fileSize, rangeSize);
            }
        else
            {
            assertTrue(chunkSizeMessage,
                rangeSize >= DefaultRangeSizeSelector.MIN_CHUNK_SIZE);
            final long expectedSize = fileSize/(numSources * 
                DefaultRangeSizeSelector.DIFFERENTIAL_SPEED_FACTOR);
            if (expectedSize >= DefaultRangeSizeSelector.MAX_CHUNK_SIZE)
                {
                assertEquals(chunkSizeMessage, DefaultRangeSizeSelector.MAX_CHUNK_SIZE, rangeSize);
                }
            else if (expectedSize <= DefaultRangeSizeSelector.MIN_CHUNK_SIZE)
                {
                assertEquals(chunkSizeMessage, DefaultRangeSizeSelector.MIN_CHUNK_SIZE, rangeSize);
                }
            else
                {
                assertEquals(chunkSizeMessage, expectedSize, rangeSize);
                }
            }
        
        }
    }
