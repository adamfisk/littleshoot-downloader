package org.lastbamboo.common.download;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class for keeping track of ranges for a single download.
 */
public class RangeTrackerImpl implements RangeTracker
    {

    private final Log LOG = LogFactory.getLog(RangeTrackerImpl.class);

    private final PriorityBlockingQueue<LongRange> m_ranges;
    
    private final long CHUNK_SIZE = 100000L;
    
    private final Set<LongRange> m_rangeSet = 
        Collections.synchronizedSet(new HashSet<LongRange>());

    private final int m_numChunks;

    /**
     * Creates a new range tracker for a file of the specified size.
     * @param name 
     * 
     * @param fileSize The size of the file we're downloading.
     */
    public RangeTrackerImpl(final String name, final long fileSize)
        {
        LOG.debug("Creating queue for file size: " + fileSize);
        
        // We need enough chunks to handle the full file size.
        m_numChunks = (int) Math.ceil(fileSize/(double) CHUNK_SIZE);
        
        LOG.debug("Creating a queue with " + m_numChunks + " chunks...");
        
        final Comparator<LongRange> rangeComparator = new LongRangeComparator();
        
        m_ranges = new PriorityBlockingQueue<LongRange>(m_numChunks, 
                                                        rangeComparator);
        
        long index = 0;
        while (index < fileSize)
            {
            // If we are at the last chunk, our last chunk ends at the file
            // size.  Since the range is inclusive at both ends, we always
            // subtract 1 to get the maximum byte.
            final long max = Math.min(fileSize - 1, index + CHUNK_SIZE - 1);
            
            final LongRange curRange = new LongRange(index, max);
            
            LOG.debug("Adding range: " + curRange);
            
            m_ranges.add(curRange);
            m_rangeSet.add(curRange);
            
            index = max + 1;
            }
        }

    public LongRange getNextRange()
        {
        try
            {
            return this.m_ranges.take();
            }
        catch (final InterruptedException e)
            {
            LOG.warn("Interrupt waiting for ranges!!", e);
            return null;
            }
        }

    public boolean hasMoreRanges()
        {
        return !this.m_rangeSet.isEmpty();
        }

    
    public void onRangeComplete(final LongRange range)
        {
        LOG.debug("Removing completed range: "+range + " " + 
            this.m_rangeSet.size() + " left...");
        this.m_rangeSet.remove(range);
        
        if (this.m_rangeSet.isEmpty())
            {
            // Add our special range signifier indicating the file is complete.
            this.m_ranges.put(new LongRange(183L,183L));
            }
        }

    public void onRangeFailed(final LongRange range)
        {
        LOG.debug("Range ' " + range + "' failed");
        
        // The specified range was not downloaded successfully, so add it 
        // again.
        this.m_ranges.put(range);
        if (!this.m_rangeSet.contains(range))
            {
            LOG.error("Failed range should still be in range set "+
                this.m_rangeSet);
            }
        }

    public int getNumChunks()
        {
        return this.m_numChunks;
        }

    }
