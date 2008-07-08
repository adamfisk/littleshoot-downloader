package org.lastbamboo.common.download;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.util.NoneImpl;
import org.lastbamboo.common.util.Optional;
import org.lastbamboo.common.util.SomeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for keeping track of ranges for a single download.
 */
public class RangeTrackerImpl implements RangeTracker
    {
    
    private static final int MAX_CHUNK_SIZE = 1024 * 512;
    
    private static final int MIN_CHUNK_SIZE = 1024 * 50;

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
     * The logger for this class.
     */
    private final Logger m_log = 
        LoggerFactory.getLogger (RangeTrackerImpl.class);

    /**
     * The total number of chunks being tracked.
     */
    private final int m_numChunks;

    /**
     * Keeps track of the number of bytes successfully read.
     */
    private volatile long m_bytesRead;

    private final int m_chunkSize;

    /**
     * Creates a new range tracker for a file of the specified size.
     * @param fileSize The size of the file we're downloading.
     * @param numSources The number of sources for the download.
     */
    public RangeTrackerImpl(final long fileSize, final int numSources)
        {
        m_log.debug("Creating queue for file size: " + fileSize);
        
        // The logic here is to give each source a chunk but then to take
        // into account that sources download at difference rates.  We 
        // take a shot in the dark and say this could be a significant
        // differential, and we just take a stab at it.  It's
        // generally better to have too many chunks than too few, however,
        // because a slow downloader can screw up the works more with larger
        // chunks.  The ideal is to always dynamically determine the chunk 
        // size, but we'll save that for later.
        final int theoreticalChunkSize = 
            (int) Math.ceil((fileSize/numSources)/10);
        
        // Make sure the chunk size isn't way too big...
        final int upperCapChunkSize = 
            Math.min(MAX_CHUNK_SIZE, theoreticalChunkSize);
        // ..and make sure the chunk size isn't way too small.
        final int lowerCapChunkSize = 
            Math.max(upperCapChunkSize, MIN_CHUNK_SIZE);
        
        // Finally, make sure it's not bigger than the file.
        this.m_chunkSize = (int) Math.min(fileSize, lowerCapChunkSize);
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
                m_log.error("Nothing known about range!!");
                throw new RuntimeException
                        ("Range '" + range + "' is unknown to this tracker");
                }
            }
        }

    /**
     * {@inheritDoc}
     */
    public void onRangeFailed (final LongRange range)
        {
        m_log.debug ("Range ' " + range + "' failed");
        
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
                m_log.error("Nothing known about range!!");
                throw new RuntimeException
                        ("Range '" + range + "' is unknown to this tracker");
                }
            }
        }

    public long getBytesRead()
        {
        return this.m_bytesRead;
        }
    }
