package org.lastbamboo.download;

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

    private File m_file;

    private boolean m_complete = false;
    
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

    public void write(final OutputStream os) throws IOException
        {
        LOG.debug("Writing file...");
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
        synchronized (this.m_completedRanges)
            {
            completedRanges.addAll(this.m_completedRanges);
            }
        final LaunchFileTracker tracker = 
            new DownloadingFileLauncher(this.m_randomAccessFile, 
                completedRanges);
        this.m_trackers.add(tracker);
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
                this.m_completedRanges.add(range);
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

    public void setFile(final File file)
        {
        this.m_file = file;
        }

    }
