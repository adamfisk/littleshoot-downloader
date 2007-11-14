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
    /**
     * The maximum size of each range.
     */
    private static final long CHUNK_SIZE = 100000L;
    
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
    private final Logger m_logger;

    /**
     * The total number of chunks being tracked.
     */
    private final int m_numChunks;

    /**
     * Creates a new range tracker for a file of the specified size.
     * @param name 
     * 
     * @param fileSize The size of the file we're downloading.
     */
    public RangeTrackerImpl(final String name, final long fileSize)
        {
        m_logger = LoggerFactory.getLogger (RangeTrackerImpl.class);
        m_logger.debug("Creating queue for file size: " + fileSize);
        
        // We need enough chunks to handle the full file size.
        m_numChunks = (int) Math.ceil(fileSize/(double) CHUNK_SIZE);
        
        m_logger.debug("Creating a queue with " + m_numChunks + " chunks...");
        
        final Comparator<LongRange> rangeComparator = new LongRangeComparator();
        
        m_inactive = new PriorityQueue<LongRange> (m_numChunks, rangeComparator);
        m_active = new HashSet<LongRange> ();
        
        long index = 0;
        while (index < fileSize)
            {
            // If we are at the last chunk, our last chunk ends at the file
            // size.  Since the range is inclusive at both ends, we always
            // subtract 1 to get the maximum byte.
            final long max = Math.min(fileSize - 1, index + CHUNK_SIZE - 1);
            
            final LongRange curRange = new LongRange(index, max);
            
            m_logger.debug("Adding range: " + curRange);
            
            m_inactive.add (curRange);
            
            index = max + 1;
            }
        }
    
    /**
     * {@inheritDoc}
     */
    public Optional<LongRange> getNextRange
            ()
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
                	throw new RuntimeException (e);
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
    
    /**
     * {@inheritDoc}
     */
    public int getNumChunks
            ()
        {
        return this.m_numChunks;
        }

    /**
     * {@inheritDoc}
     */
    public boolean hasMoreRanges
            ()
        {
        return !(m_inactive.isEmpty () && m_active.isEmpty ());
        }

    /**
     * {@inheritDoc}
     */
    public void onRangeComplete
            (final LongRange range)
        {
        synchronized (this)
            {
            if (m_active.contains (range))
                {
                m_active.remove (range);
                
                notifyAll ();
                }
            else
                {
                throw new RuntimeException
                        ("Range '" + range + "' is unknown to this tracker");
                }
        	}
        }

    /**
     * {@inheritDoc}
     */
    public void onRangeFailed
            (final LongRange range)
        {
        m_logger.debug ("Range ' " + range + "' failed");
        
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
                throw new RuntimeException
                        ("Range '" + range + "' is unknown to this tracker");
                }
        	}
        }
    }
