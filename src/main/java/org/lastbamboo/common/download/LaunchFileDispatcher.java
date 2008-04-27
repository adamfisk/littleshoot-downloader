package org.lastbamboo.common.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class for dispatching file download events to all the open file 
 * launchers/streamers.
 */
public class LaunchFileDispatcher implements LaunchFileTracker
    {

    private static final Log LOG = 
        LogFactory.getLog(LaunchFileDispatcher.class);
    
    private final Collection<LaunchFileTracker> m_trackers = 
        Collections.synchronizedList(new LinkedList<LaunchFileTracker>());
    private final RandomAccessFile m_randomAccessFile;
    private final PriorityBlockingQueue<LongRange> m_completedRanges;
    private final int m_numChunks;

    private final File m_file;

    private boolean m_complete = false;
    
    private volatile int m_activeWriteCalls = 0;
    
    private final Object DOWNLOAD_STREAM_LOCK = new Object ();
    
    /**
     * Creates a new tracker for streaming the file to the browser.
     * 
     * @param file The file on disk.
     * @param raf The random access file to copy the downloaded data from.
     * @param numChunks The number of chunks we're downloading.
     */
    public LaunchFileDispatcher(final File file, final RandomAccessFile raf, 
        final int numChunks)
        {
        if (file == null)
            {
            throw new NullPointerException("Null RAF");
            }
        if (raf == null)
            {
            throw new NullPointerException("Null ranges");
            }
        this.m_file = file;
        this.m_randomAccessFile = raf;
        this.m_numChunks = numChunks;
        this.m_completedRanges = createQueue();
        }
    
    private PriorityBlockingQueue<LongRange> createQueue()
        {
        final Comparator<LongRange> increasingRangeComparator = 
            new IncreasingLongRangeComparator();
        return new PriorityBlockingQueue<LongRange>(this.m_numChunks, 
            increasingRangeComparator);
        }

    public void waitForLaunchersToComplete ()
        {
        if (this.m_activeWriteCalls == 0)
            {
            LOG.debug ("No active launchers...");
            return;
            }
        synchronized (this.DOWNLOAD_STREAM_LOCK)
            {
            LOG.debug ("Waiting for active streams to finish streaming");
            try
                {
                // It shouldn't take forever to stream the already
                // downloaded file to the browser, so cap the wait.
                this.DOWNLOAD_STREAM_LOCK.wait (6*60*1000);
                if (this.m_activeWriteCalls > 0)
                    {
                    LOG.warn ("Still " + this.m_activeWriteCalls + 
                        " active writes!!");
                    }
                }
            catch (final InterruptedException e)
                {
                LOG.warn ("Unexpected interrupt!!", e);
                }
            }
        }
    
    public void write(final OutputStream os) throws IOException
        {
        LOG.debug("Writing file...");
        try
            {
            ++m_activeWriteCalls;
            if (this.m_complete)
                {
                LOG.debug("Writing file on disk...");
                writeCompleteFile(os);
                }
            else
                {
                LOG.debug("Writing downloading file...");
                writeDownloadingFile(os);
                }
            
            LOG.debug ("Finished launcher write call...");
            
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
            --m_activeWriteCalls;
            }
        }

    private void writeCompleteFile(final OutputStream os) throws IOException
        {
        try
            {
            final InputStream is = new FileInputStream(this.m_file);
            IOUtils.copy(is, os);
            }
        finally
            {
            os.close();
            }
        }

    private void writeDownloadingFile(final OutputStream os) throws IOException
        {
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final LaunchFileTracker tracker;
        synchronized (this.m_completedRanges)
            {
            completedRanges.addAll(this.m_completedRanges);
            tracker = new DownloadingFileLauncher(this.m_randomAccessFile, 
                completedRanges);
            this.m_trackers.add(tracker);
            }

        tracker.write(os);
        }

    public void onRangeComplete(final LongRange range)
        {
        synchronized (this.m_trackers)
            {
            for (final LaunchFileTracker tracker : this.m_trackers)
                {
                if (LOG.isDebugEnabled())
                    {
                    LOG.debug("Notifying all trackers of completed range...");
                    }
                
                // We need to add a range to the global dispatcher range queue
                // because new writers might come along and need to know 
                // what has already been written.  Note this is just the ranges
                // and doesn't take much memory.
                synchronized (m_completedRanges)
                    {
                    this.m_completedRanges.add(range);
                    }
                tracker.onRangeComplete(range);
                if (LOG.isDebugEnabled())
                    {
                    LOG.debug("Finished range notify...");
                    }
                }
            }
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
    }
