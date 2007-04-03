package org.lastbamboo.common.download;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class for tracking parts of the downloading file we can view immediately
 * in order.
 */
public class DownloadingFileLauncher implements LaunchFileTracker
    {

    private static final Log LOG = 
        LogFactory.getLog(DownloadingFileLauncher.class);
    
    private final PriorityBlockingQueue<LongRange> m_completedRanges;
    private final RandomAccessFile m_randomAccessFile;
    
    private long m_rangeIndex = 0L;

    private boolean m_complete = false;
    
    /**
     * Creates a new tracker for streaming the file to the browser.
     * 
     * @param file The random access file to copy the downloaded data from.
     * @param completedRanges The ranges that have already completed 
     * downloading.
     */
    public DownloadingFileLauncher(final RandomAccessFile file, 
        final PriorityBlockingQueue<LongRange> completedRanges)
        {
        this.m_randomAccessFile = file;
        this.m_completedRanges = completedRanges;
        }
    
    public void onRangeComplete(final LongRange range)
        {
        synchronized (this.m_completedRanges)
            {   
            if (LOG.isDebugEnabled())
                {
                LOG.debug("Adding completed range!!");
                }
            this.m_completedRanges.add(range);
        
            if (range.getMinimumLong() == this.m_rangeIndex)
                {
                this.m_completedRanges.notify();
                }
            }
        }
    
    public void write(final OutputStream os) throws IOException
        {
        try
            {
            writeAllRanges(os);
            }
        finally
            {
            IOUtils.closeQuietly(os);
            }
        }

    private void writeAllRanges(final OutputStream os) throws IOException
        {
        if (LOG.isDebugEnabled())
            {
            LOG.debug("Writing "+this.m_completedRanges.size()+
                " completed ranges...");
            }
        while (true)
            {
            long startIndex = this.m_rangeIndex;
            // First, determine how much we can write now.
            synchronized (this.m_completedRanges)
                {
                for (final Iterator<LongRange> iter = 
                    this.m_completedRanges.iterator(); 
                    iter.hasNext();)
                    {
                    if (LOG.isDebugEnabled())
                        {
                        LOG.debug("Incrementing index for range...");
                        }
                    final LongRange nextRange = iter.next();
                    if (nextRange.getMinimumLong() == this.m_rangeIndex)
                        {
                        this.m_rangeIndex = nextRange.getMaximumLong() + 1;
                        iter.remove();
                        }
                    else
                        {
                        break;
                        }
                    }
                }
                

            // This will throw an IOException if the user closes the browser 
            // window, for example.
            writeRange(startIndex, this.m_rangeIndex, os);
            
            synchronized (this.m_completedRanges)
                {
                LOG.debug("Locked completed range...");
                final LongRange range = this.m_completedRanges.peek();
                LOG.debug("Got range...");
                if (range == null || 
                    range.getMinimumLong() != this.m_rangeIndex)
                    {
                    try
                        {
                        LOG.debug("Waiting on completed range...");
                        this.m_completedRanges.wait();
                        
                        if (this.m_completedRanges.isEmpty() && this.m_complete)
                            {
                            os.flush();
                            os.close();
                            this.m_completedRanges.notify();
                            return;
                            }
                        }
                    catch (final InterruptedException e)
                        {
                        LOG.warn("Interrupted!!", e);
                        }
                    }
                }
            }
        }

    private void writeRange(final long startIndex, 
        final long endIndex, final OutputStream os) throws IOException
        {
        final int length = (int) (endIndex - startIndex);
        LOG.debug("Copying "+length+" bytes...");
        final byte[] bytesToCopy = new byte[length];
        synchronized (this.m_randomAccessFile)
            {
            LOG.debug("Got lock on file...");
            this.m_randomAccessFile.seek(startIndex);
            final int numBytesRead = this.m_randomAccessFile.read(bytesToCopy);
            if (numBytesRead != length)
                {
                LOG.warn("Unexpected number of bytes read: "+numBytesRead);
                }
            os.write(bytesToCopy);
            }
        LOG.debug("Wrote range...");
        }

    public void onFileComplete()
        {
        LOG.debug("Received notification file is complete");
        this.m_complete = true;
        synchronized (this.m_completedRanges)
            {
            this.m_completedRanges.notify();
            
            // Wait until everything is copied to the output stream.  This
            // should not take too long because we're just copying bytes
            // we already have.  Could see it causing issues in the future
            // though.
            while (!this.m_completedRanges.isEmpty())
                {
                try
                    {
                    this.m_completedRanges.wait();
                    }
                catch (final InterruptedException e)
                    {
                    LOG.error("Interrupted while writing...");
                    }
                }
            }
        LOG.debug("Returning from file complete notification...");
        }

    public void setFile(final File file)
        {
        LOG.warn("Should not be setting the file here.");
        // This has no file reference.
        }

    }
