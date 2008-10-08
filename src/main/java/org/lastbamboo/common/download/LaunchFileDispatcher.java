package org.lastbamboo.common.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for dispatching file download events to all the open file 
 * launchers/streamers.
 */
public class LaunchFileDispatcher implements LaunchFileTracker
    {
    
    private final Logger m_log = 
        LoggerFactory.getLogger(LaunchFileDispatcher.class);
    
    private final Collection<LaunchFileTracker> m_trackers = 
        Collections.synchronizedList(new LinkedList<LaunchFileTracker>());
    private final RandomAccessFile m_randomAccessFile;
    private final PriorityBlockingQueue<LongRange> m_completedRanges;
    private final int m_initialQueueSize;

    private volatile long m_rangeIndex = 0L;
    
    private final File m_incompleteFile;

    private boolean m_complete = false;
    
    private volatile int m_activeWriteCalls = 0;
    
    private final Object DOWNLOAD_STREAM_LOCK = new Object ();

    private final URI m_expectedSha1;
    
    /**
     * Creates a new tracker for streaming the file to the browser.
     * 
     * @param incompleteFile The file on disk.
     * @param raf The random access file to copy the downloaded data from.
     * @param initialQueueSize The initial size of the queue, loosely based
     * on number of chunks we're downloading.
     * @param expectedSha1 The expected SHA-1 for the file.
     */
    public LaunchFileDispatcher(final File incompleteFile, 
        final RandomAccessFile raf, final int initialQueueSize, 
        final URI expectedSha1)
        {
        if (incompleteFile == null)
            {
            throw new NullPointerException("Null RAF");
            }
        if (raf == null)
            {
            throw new NullPointerException("Null ranges");
            }
        this.m_incompleteFile = incompleteFile;
        this.m_randomAccessFile = raf;
        if (initialQueueSize > 0)
            {
            // Cap the queue size to avoid using too much memory.
            if (initialQueueSize > 1000)
                {
                this.m_initialQueueSize = 1000;
                }
            else
                {
                this.m_initialQueueSize = initialQueueSize;
                }
            }
        else
            {
            this.m_initialQueueSize = 1;
            }
        this.m_expectedSha1 = expectedSha1;
        this.m_completedRanges = createQueue();
            //new TreeSet<LongRange>(new IncreasingLongRangeComparator());
        }
    
    private PriorityBlockingQueue<LongRange> createQueue()
        {
        final Comparator<LongRange> increasingRangeComparator = 
            new IncreasingLongRangeComparator();
        return new PriorityBlockingQueue<LongRange>(this.m_initialQueueSize, 
            increasingRangeComparator);
        }

    public void waitForLaunchersToComplete ()
        {
        synchronized (this.DOWNLOAD_STREAM_LOCK)
            {
            if (this.m_activeWriteCalls == 0)
                {
                m_log.debug ("No active launchers for: {}",
                    this.m_incompleteFile.getName());
                return;
                }
            m_log.debug ("Waiting for active streams to finish for: {}",
                this.m_incompleteFile.getName());
            try
                {
                // It shouldn't take forever to stream the already
                // downloaded file to the browser, so cap the wait.
                this.DOWNLOAD_STREAM_LOCK.wait (60*DateUtils.MILLIS_PER_MINUTE);
                if (this.m_activeWriteCalls > 0)
                    {
                    m_log.warn ("Still " + this.m_activeWriteCalls + 
                        " active writes!!");
                    }
                }
            catch (final InterruptedException e)
                {
                m_log.warn ("Unexpected interrupt!!", e);
                }
            
            m_log.debug("Finished waiting...");
            }
        }
    
    public void write(final OutputStream os, final boolean cancelOnStreamClose) 
        throws IOException
        {
        m_log.debug("Writing file...");
        try
            {
            ++m_activeWriteCalls;
            if (this.m_complete)
                {
                m_log.debug("Writing file on disk...");
                writeCompleteFile(os);
                }
            else
                {
                m_log.debug("Writing downloading file...");
                writeDownloadingFile(os, cancelOnStreamClose);
                }
            
            m_log.debug ("Finished launcher write call...");
            
            synchronized (DOWNLOAD_STREAM_LOCK)
                {
                // This indicates the last writer has just completed writing,
                // so we notify the lock that it's OK to close the file stream.
                if (m_activeWriteCalls == 1)
                    {
                    DOWNLOAD_STREAM_LOCK.notify ();
                    }
                }
            }
        finally
            {
            synchronized (DOWNLOAD_STREAM_LOCK)
                {
                --m_activeWriteCalls;
                m_log.debug("Decremented active writes for: " +
                    this.m_incompleteFile.getName() + 
                        " Now: " + m_activeWriteCalls);
                }
            }
        }

    private void writeCompleteFile(final OutputStream os) throws IOException
        {
        try
            {
            final InputStream is = new FileInputStream(this.m_incompleteFile);
            IOUtils.copy(is, os);
            }
        finally
            {
            os.close();
            }
        }
    

    private void writeDownloadingFile(final OutputStream os, 
        final boolean cancelOnStreamClose) throws IOException
        {
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final LaunchFileTracker tracker;
        synchronized (this.m_trackers)
            {
            synchronized (this.m_completedRanges)
                {
                completedRanges.addAll(this.m_completedRanges);
                tracker = new DownloadingFileLauncher(this.m_randomAccessFile, 
                    completedRanges, this.m_expectedSha1, this.m_incompleteFile);
                this.m_trackers.add(tracker);
                }
            }
        
        tracker.write(os, cancelOnStreamClose);
        }

    public void onRangeComplete(final LongRange range)
        {
        //onRangeCompleteVanilla(range);
        onRangeCompleteOptimized(range);
        }
    
    private void onRangeCompleteVanilla(final LongRange range)
        {
        synchronized (this.m_trackers)
            {
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                //m_log.debug("Notifying all trackers of completed range...");
                     
                     // We need to add a range to the global dispatcher range queue
                     // because new writers might come along and need to know 
                     // what has already been written.  Note this is just the ranges
                     // and doesn't take much memory.
                     synchronized (m_completedRanges)
                         {
                         this.m_completedRanges.add(range);
                         }
                tracker.onRangeComplete(range);
                //m_log.debug("Finished range notify...");
                }
            }
        }
    
    private void onRangeCompleteOptimized(final LongRange range)
        {
        // We need to add a range to the global dispatcher range queue
        // because new writers might come along and need to know 
        // what has already been written.  Note this is just the ranges
        // and doesn't take much memory.
        synchronized (m_completedRanges)
            {
            
            // This is an optimization to avoid OutOfMemoryErrors, which 
            // we've seen with very large files.  We basically do a cheap
            // consolidation of contiguous blocks at the beginning of the queue.
            // We don't fully consolidate all of it because it's potentially
            // CPU intensive with large files, but this is a quick and dirty
            // workaround that goes a long way.
            if (range.getMinimumLong() == this.m_rangeIndex)
                {
                // Just remove the old start range because we know we're
                // replacing it.  This range is the one that will continually 
                // grow.  
                
                // We don't do it the first time because then it's a
                // legitimate range, not one of our consolidated ones.
                if (this.m_rangeIndex != 0L)
                    {
                    m_completedRanges.poll();
                    }
                
                this.m_rangeIndex = range.getMaximumLong() + 1L;
                while (!this.m_completedRanges.isEmpty())
                    {
                    final LongRange nextRange = this.m_completedRanges.peek();
                    if (nextRange.getMinimumLong() == this.m_rangeIndex)
                        {
                        this.m_rangeIndex = nextRange.getMaximumLong() + 1L;
                        this.m_completedRanges.remove();
                        }
                    else
                        {
                        break;
                        }
                    }
                // Now add back the larger start range.
                final LongRange startRange = 
                    new LongRange(0L, this.m_rangeIndex - 1L);
                this.m_completedRanges.add(startRange);
                
                //final LongRange newRange = new LongRange(range.getMinimumLong(), 
                  //  startRange.getMaximumLong());
                //notifyTrackers(newRange);
                }
            else
                {
                this.m_completedRanges.add(range);
                }
            }
        notifyTrackers(range);
        }
    
    private void notifyTrackers(final LongRange range)
        {
        synchronized (this.m_trackers)
            {
             
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                //m_log.debug("Notifying all trackers of completed range...");
                tracker.onRangeComplete(range);
                //m_log.debug("Finished range notify...");
                }
            }
        }


    /**
     * Used for testing.
     * 
     * @return The available ranges.
     */
    public Collection<LongRange> getRanges()
        {
        return this.m_completedRanges;
        }
    /**
     * For testing.
     * 
     * @param tracker The tracker.
     */
    public void addTracker(final LaunchFileTracker tracker)
        {
        this.m_trackers.add(tracker);
        }

    public void onFileComplete()
        {
        synchronized (this.m_trackers)
            {
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                tracker.onFileComplete();
                }
            }
        this.m_complete = true;
        }

    public int getActiveWriteCalls()
        {
        return m_activeWriteCalls;
        }

    public void onFailure()
        {
        synchronized (this.m_trackers)
            {
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                tracker.onFailure();
                }
            }
        }

    public void onDownloadStopped()
        {
        synchronized (this.m_trackers)
            {
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                tracker.onDownloadStopped();
                }
            }
        }
    }
