package org.lastbamboo.common.download;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestOutputStream;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.lang.time.DateUtils;
import org.littleshoot.util.Base32;
import org.littleshoot.util.Sha1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for tracking parts of the downloading file we can view immediately
 * in order.
 */
public class DownloadingFileLauncher implements LaunchFileTracker
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass()); 
    
    private final PriorityBlockingQueue<LongRange> m_completedRanges;
    private final RandomAccessFile m_randomAccessFile;
    
    private volatile long m_rangeIndex = 0L;

    private volatile boolean m_completeFlag = false;

    private final URI m_expectedSha1;

    private DigestOutputStream m_digestOutputStream;

    private volatile boolean m_writtenAll = false;

    private final File m_incompleteFile;

    /**
     * Flag for whether or not the stream we're writing to is closed.
     */
    private volatile boolean m_streamClosed = false;

    private volatile boolean m_failed = false;

    /**
     * Flag for whether or not the not download is stopped.
     */
    private volatile boolean m_stopped;
    
    
    /**
     * Creates a new tracker for streaming the file to the browser.
     * 
     * @param raf The random access file to copy the downloaded data from.
     * @param completedRanges The ranges that have already completed 
     * downloading.
     * @param expectedSha1 The expected SHA-1 for the file.
     * @param incompleteFile The incomplete file on disk.
     */
    public DownloadingFileLauncher(final RandomAccessFile raf, 
        final PriorityBlockingQueue<LongRange> completedRanges, 
        final URI expectedSha1, final File incompleteFile)
        {
        if (raf == null)
            {
            throw new NullPointerException("Null RAF");
            }
        if (completedRanges == null)
            {
            throw new NullPointerException("Null ranges");
            }
        this.m_randomAccessFile = raf;
        this.m_completedRanges = completedRanges;
        this.m_expectedSha1 = expectedSha1;
        this.m_incompleteFile = incompleteFile;
        }
    
    public void onRangeComplete(final LongRange range)
        {
        synchronized (this.m_completedRanges)
            {   
            //m_log.debug("Adding completed range: "+range);
            this.m_completedRanges.add(range);
        
            // Notify the completed ranges if the range we just got is the
            // one we're waiting for.
            if (range.getMinimumLong() == this.m_rangeIndex)
                {
                //m_log.debug("Found range we need -- notifying: {}",
                //   this.m_rangeIndex);
                this.m_completedRanges.notifyAll();
                }
            }
        }
    
    public void onFailure()
        {
        this.m_failed = true;
        IOUtils.closeQuietly(this.m_digestOutputStream);
        this.m_streamClosed = true;
        synchronized (this.m_completedRanges)
            {
            //m_log.debug("Got lock with remaining ranges: {}",m_completedRanges);
            this.m_completedRanges.notifyAll();
            }
        }
    
    public void write(final OutputStream os, final boolean cancelOnStreamClose) 
        throws IOException 
        {
        // Our SHA-1 implementation is much faster than Sun's.
        this.m_digestOutputStream = new DigestOutputStream(os, new Sha1());
        try
            {
            writeAllRanges(this.m_digestOutputStream);
            m_log.debug("Wrote all ranges...");
            }
        catch (final IOException e)
            {
            m_log.debug("IO error writing file: " + 
                this.m_incompleteFile.getName(), e);
            this.m_streamClosed = true;
            throw e;
            }
        catch (final Throwable t)
            {
            m_log.warn("Throwable streaming file: " + 
                this.m_incompleteFile.getName(), t);
            }
        finally
            {
            IOUtils.closeQuietly(this.m_digestOutputStream);
            }
        }

    private void writeAllRanges(final OutputStream os) throws IOException
        {
        if (m_log.isDebugEnabled())
            {
            m_log.debug("Writing "+this.m_completedRanges.size()+
                " completed ranges...");
            }
        while (true)
            {
            // First, determine how much we can write now.
            final long startIndex;
            synchronized (this.m_completedRanges)
                {
                startIndex = this.m_rangeIndex;
                while (!this.m_completedRanges.isEmpty())
                    {
                    //LOG.debug("Incrementing index for range...");
                    final LongRange nextRange = this.m_completedRanges.peek();
                    if (nextRange.getMinimumLong() == this.m_rangeIndex)
                        {
                        this.m_rangeIndex = nextRange.getMaximumLong() + 1;
                        this.m_completedRanges.remove();
                        }
                    else
                        {
                        break;
                        }
                    }
                }
                

            // This will throw an IOException if the user closes the browser 
            // window, for example.
            // This just writes all the ranges we already have.
            writeRange(startIndex, this.m_rangeIndex, os);
            
            synchronized (this.m_completedRanges)
                {
                //m_log.debug("Locked completed range...");
                if (done())
                    {
                    m_log.debug("We're done.  Flushing and notifying for: {}", 
                        m_incompleteFile.getName());
                    os.flush();
                    os.close();
                    this.m_writtenAll = true;
                    this.m_completedRanges.notifyAll();
                    return;
                    }
                final LongRange range = this.m_completedRanges.peek();
                //m_log.debug("Got range...");
                
                // If there is no new range or it's not the next range we
                // need, wait until we get it.  
                if (range == null || 
                    range.getMinimumLong() != this.m_rangeIndex)
                    {
                    try
                        {
                        //m_log.debug("Waiting on completed range. Complete: {}",
                          //  this.m_complete);
                        //m_log.debug("Waiting on next range min: {}", 
                        //    this.m_rangeIndex);
                        //m_log.debug("Ranges: {}", this.m_completedRanges);
                        timedWait();
                        
                        if (this.m_failed || this.m_stopped)
                            {
                            m_log.debug("Download failed...");
                            os.flush();
                            os.close();
                            this.m_completedRanges.notifyAll();
                            return;
                            }
                        
                        //m_log.debug("Finished waiting...");
                        if (done())
                            {
                            m_log.debug("We're done.  Writing any remaining...");
                            os.flush();
                            os.close();
                            this.m_writtenAll = true;
                            this.m_completedRanges.notifyAll();
                            return;
                            }
                        }
                    catch (final InterruptedException e)
                        {
                        m_log.warn("Interrupted!!", e);
                        break;
                        }
                    }
                }
            }
        }

    /**
     * We want to make sure we never wait forever. 
     * 
     * @throws InterruptedException If we're interrupted for any reason.
     */
    private void timedWait() throws InterruptedException
        {
        final long waitStart = System.currentTimeMillis();
        this.m_completedRanges.wait(DateUtils.MILLIS_PER_DAY);
        final long waitTime = System.currentTimeMillis() - waitStart;
        if (waitTime > DateUtils.MILLIS_PER_HOUR)
            {
            m_log.warn("Waited for download for more " +
                "than an hour: "+waitTime/1000/60);
            }
        }

    /**
     * Checks if we're done.  Note that this relies on the downloader notifying
     * this class of all completed ranges prior to the uber-downloader 
     * thinking we're done.  Otherwise, we'd get the onFileComplete notification
     * prior to receiving all the ranges in the file, and we potentially
     * would never write the last few ranges (depending on the thread
     * scheduling, I believe).
     * 
     * @return <code>true</code> if we've received the file complete 
     * notification and we've written all the ranges in the file.
     */
    private boolean done()
        {
        return this.m_completedRanges.isEmpty() && this.m_completeFlag;
        }

    private void writeRange(final long startIndex, 
        final long endIndex, final OutputStream os) throws IOException
        {
        int index = 0;
        final long length = endIndex - startIndex;
        //m_log.debug("Copying total bytes: {}", length);
        final long maxChunkSize = 1024 * 500;

        //m_log.debug("Got lock on file...");
        while (index < length)
            {
            final long curChunkSize;
            if (length - index < maxChunkSize)
                {
                curChunkSize = (length - index);
                }
            else
                {
                curChunkSize = maxChunkSize;
                }
            final byte[] bytesToCopy = new byte[(int) curChunkSize];
            final long baseIndex = startIndex + index;
            
            final int numBytesRead;
            synchronized (this.m_randomAccessFile)
                {
                this.m_randomAccessFile.seek(baseIndex);
                numBytesRead = this.m_randomAccessFile.read(bytesToCopy);
                }
            
            // The bytes read should equal the expected length because the
            // file's already on disk.  The API doesn't require this, but
            // in practice it should always happen.
            if (numBytesRead != curChunkSize)
                {
                m_log.warn("Unexpected number of bytes read.  Expected "+
                    curChunkSize+" but was "+numBytesRead);
                }
            //m_log.debug("Writing bytes...");
            os.write(bytesToCopy);
            index += numBytesRead;
            }
        //m_log.debug("Wrote range...");
        }
    
    public void onFileComplete()
        {
        m_log.debug("Received notification file is complete");
        
        synchronized (this.m_completedRanges)
            {
            this.m_completeFlag = true;
            //m_log.debug("Got lock with remaining ranges: {}",m_completedRanges);
            this.m_completedRanges.notifyAll();
            
            if (this.m_streamClosed)
                {
                m_log.debug("Stream is closed...returning");
                return;
                }
            // Wait until everything is copied to the output stream.  This
            // should not take too long because we're just copying bytes
            // we already have.  Could see it causing issues in the future
            // though.
            while (!this.m_completedRanges.isEmpty())
                {
                m_log.debug("Waiting to write remaining bytes...next range: {}", 
                    this.m_rangeIndex);
                
                try
                    {
                    timedWait();
                    }
                catch (final InterruptedException e)
                    {
                    m_log.error("Interrupted while writing...");
                    }
                }
            }
        
        verifySha1();
        m_log.debug("Returning from file complete notification...");
        }

    private void verifySha1()
        {
        synchronized (m_completedRanges)
            {
            while (!m_writtenAll && !m_stopped)
                {
                try
                    {
                    timedWait();
                    }
                catch (final InterruptedException e)
                    {
                    m_log.error("Interrupted?", e);
                    }
                }
            }
        
        if (m_stopped)
            {
            m_log.debug("Download stopped!");
            return;
            }
        final byte[] sha1Bytes = 
            this.m_digestOutputStream.getMessageDigest().digest();

        try
            {
            // preferred casing: lowercase "urn:sha1:", uppercase encoded value
            // note that all URNs are case-insensitive for the "urn:<type>:" 
            // part, but some MAY be case-sensitive thereafter (SHA1/Base32 is 
            // case insensitive)
            final URI sha1 = new URI("urn:sha1:"+Base32.encode(sha1Bytes));
            if (this.m_expectedSha1 == null)
                {
                // This can happen during tests.
                m_log.debug("Null expected SHA-1.  Testing?");
                }
            else if (!this.m_expectedSha1.equals(sha1))
                {
                m_log.error("Did not get expected SHA-1!!!  Expected "+
                    this.m_expectedSha1+" but was "+sha1);
                throw new IllegalStateException("SHA-1 mismatch!!");
                }
            else
                {
                m_log.debug("SHA-1s match!!");
                }
            }
        catch (final URISyntaxException e)
            {
            // This should never happen.
            m_log.error("Could not encode SHA-1", e);
            }  
        }

    public int getActiveWriteCalls()
        {
        // Effectively a NO-OP in this case.
        return 0;
        }

    public void waitForLaunchersToComplete()
        {
        // Effectively a NO-OP in this case.
        }

    public void onDownloadStopped()
        {
        if (done())
            {
            m_log.debug("Already finished.");
            return;
            }
        this.m_streamClosed = true;
        synchronized (this.m_completedRanges)
            {
            this.m_completedRanges.notifyAll();
            }
        
        this.m_stopped = true;
        if (this.m_digestOutputStream != null)
            {
            IOUtils.closeQuietly(this.m_digestOutputStream);
            }
        }

    }
