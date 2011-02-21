package org.lastbamboo.common.download;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.lang.math.LongRange;
import org.littleshoot.util.NoneImpl;
import org.littleshoot.util.Optional;
import org.littleshoot.util.SomeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for keeping track of ranges for a single download.  The algorithm
 * for choosing a range size is important for making downloads as efficient
 * as possible.  A base
 */
public class RangeTrackerImpl implements RangeTracker
    {
    
    
    /**
     * The logger for this class.
     */
    private final Logger m_log = 
        LoggerFactory.getLogger (RangeTrackerImpl.class);
    
    /**
     * The set of active ranges.  These are ranges that are currently active but
     * may be returned to the inactive queue if they fail.
     */
    private final Set<LongRange> m_active;

    /**
     * The queue of inactive ranges.
     */
    private final Queue<LongRange> m_inactive;

    /**
     * The total number of chunks being tracked.
     */
    private final int m_numChunks;

    /**
     * Keeps track of the number of bytes successfully read.
     */
    private volatile long m_bytesRead;

    private final long m_chunkSize;

    /**
     * Creates a new range tracker for a file of the specified size.
     * @param fileSize The size of the file we're downloading.
     * @param numSources The number of sources for the download.
     */
    public RangeTrackerImpl(final long fileSize, final int numSources)
        {
        this(fileSize, numSources, new DefaultRangeSizeSelector());
        }
    
    /**
     * Creates a new range tracker for a file of the specified size.
     * @param fileSize The size of the file we're downloading.
     * @param numSources The number of sources for the download.
     * @param rangeSizeSelector The class for selecting the size of ranges.
     */
    public RangeTrackerImpl(final long fileSize, final int numSources,
        final RangeSizeSelector rangeSizeSelector)
        {
        m_log.debug("Creating queue for file size: " + fileSize);
        
        this.m_chunkSize = rangeSizeSelector.selectSize(fileSize, numSources);
        m_log.debug("Chunk size is: {}", this.m_chunkSize);
        
        m_numChunks = (int) Math.ceil(fileSize/m_chunkSize);
        
        m_log.debug("Creating a queue with " + m_numChunks + " chunks...");
        
        final Comparator<LongRange> rangeComparator = new LongRangeComparator();
        
        m_inactive = 
            new PriorityQueue<LongRange> (m_numChunks, rangeComparator);
        m_active = new HashSet<LongRange> ();
        
        long index = 0;
        while (index < fileSize)
            {
            // If we are at the last chunk, our last chunk ends at the file
            // size.  Since the range is inclusive at both ends, we always
            // subtract 1 to get the maximum byte.
            final long max = Math.min(fileSize - 1, index + m_chunkSize - 1);
            final LongRange curRange = new LongRange(index, max);
            //m_log.debug("Adding range: " + curRange);
            m_inactive.add (curRange);
            index = max + 1;
            }
        }

    public Optional<LongRange> getNextRange ()
        {
        synchronized (this)
            {
            while (m_inactive.isEmpty () && !m_active.isEmpty ())
                {
                try
                    {
                    wait ();
                    }
                catch (final InterruptedException e)
                    {
                    // This should never happen in normal operation, so we
                    // propagate the exception.
                    m_log.error("Wait interrupted", e);
                    throw new RuntimeException ("Wait interrupted", e);
                    }
                }
            
            if (m_inactive.isEmpty ())
                {
                if (m_active.isEmpty ())
                    {
                    // Both are empty.  We are done.
                    return new NoneImpl<LongRange> ();
                    }
                else
                    {
                    // We should never enter this case, since we were blocking
                    // until we were out of it.
                    m_log.error("Active ranges???");
                    throw new RuntimeException ("Illegal program state");
                    }
                }
            else
                {
                final LongRange nextRange = m_inactive.poll ();
                m_active.add (nextRange);
                return new SomeImpl<LongRange> (nextRange);
                }
            }
        }
    
    public int getNumChunks ()
        {
        return this.m_numChunks;
        }

    /**
     * {@inheritDoc}
     */
    public boolean hasMoreRanges ()
        {
        return !(m_inactive.isEmpty () && m_active.isEmpty ());
        }

    /**
     * {@inheritDoc}
     */
    public void onRangeComplete (final LongRange range)
        {
        m_log.debug ("Range complete: {}", range);
        synchronized (this)
            {
            if (m_active.contains (range))
                {
                this.m_bytesRead += 
                    (range.getMaximumLong() - range.getMinimumLong());
                m_active.remove (range);
                notifyAll ();
                }
            else
                {
                //m_log.error("Nothing known about range: "+range+
                  //  "  Actively downloading: {}", this.m_active);
                m_log.error("Nothing known about range: "+range+
                    "\nActively downloading: " + this.m_active + 
                    "\nWaiting:              " + this.m_inactive);
                throw new RuntimeException("Range '" + range + 
                    "' is unknown to this tracker");
                }
            }
        }

    /**
     * {@inheritDoc}
     */
    public void onRangeFailed (final LongRange range)
        {
        m_log.debug ("Range failed: {}", range);
        
        synchronized (this)
            {
            if (m_active.contains (range))
                {
                m_active.remove (range);
                m_inactive.add (range);
                
                notifyAll ();
                }
            else
                {
                m_log.error("Nothing known about range: "+range+
                    "\nActively downloading: " + this.m_active + 
                    "\nWaiting:              " + this.m_inactive);
                throw new RuntimeException("Range '" + range + 
                    "' is unknown to this tracker");
                }
            }
        }

    public long getBytesRead()
        {
        return this.m_bytesRead;
        }
    }
