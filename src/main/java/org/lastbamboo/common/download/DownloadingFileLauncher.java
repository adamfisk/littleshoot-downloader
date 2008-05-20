package org.lastbamboo.common.download;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.util.Base32;
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
    
    private long m_rangeIndex = 0L;

    private boolean m_complete = false;

    private final URI m_expectedSha1;

    private DigestOutputStream m_digestOutputStream;

    private volatile boolean m_writtenAll = false;

    private final File m_incompleteFile;

    /**
     * Flag for whether or not the stream we're writing to is closed.
     */
    private volatile boolean m_streamClosed = false;
    
    
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
                //m_log.debug("Found range we need -- notifying...");
                this.m_completedRanges.notify();
                }
            }
        }
    
    public void write(final OutputStream os, final boolean cancelOnStreamClose) 
        throws IOException
        {
        MessageDigest digest = null;
        try
            {
            digest = MessageDigest.getInstance("SHA-1");
            }
        catch (final NoSuchAlgorithmException e)
            {
            m_log.error("No SHA-1??", e);
            throw new IllegalStateException("Need a message digest");
            }
        this.m_digestOutputStream = new DigestOutputStream(os, digest);
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
            m_log.debug("Throwable streaming file: " + 
                this.m_incompleteFile.getName(), t);
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
            long startIndex = this.m_rangeIndex;
            // First, determine how much we can write now.
            synchronized (this.m_completedRanges)
                {
                for (final Iterator<LongRange> iter = 
                    this.m_completedRanges.iterator(); 
                    iter.hasNext();)
                    {
                    //LOG.debug("Incrementing index for range...");
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
                    this.m_completedRanges.notify();
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
                        //m_log.debug("Next range min is: {}", this.m_rangeIndex);
                        //m_log.debug("Ranges: {}", this.m_completedRanges);
                        this.m_completedRanges.wait();
                        
                        //m_log.debug("Finished waiting...");
                        if (done())
                            {
                            m_log.debug("We're done.  Writing any remaining...");
                            os.flush();
                            os.close();
                            this.m_writtenAll = true;
                            this.m_completedRanges.notify();
                            return;
                            }
                        }
                    catch (final InterruptedException e)
                        {
                        m_log.warn("Interrupted!!", e);
                        }
                    }
                }
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
        return this.m_completedRanges.isEmpty() && this.m_complete;
        }

    private void writeRange(final long startIndex, 
        final long endIndex, final OutputStream os) throws IOException
        {
        int index = 0;
        final long length = endIndex - startIndex;
        //m_log.debug("Copying total bytes: {}", length);
        final long maxChunkSize = 1024 * 500;
        synchronized (this.m_randomAccessFile)
            {
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
                this.m_randomAccessFile.seek(baseIndex);
                
                final int numBytesRead = 
                    this.m_randomAccessFile.read(bytesToCopy);
                
                // The bytes read should equal the expected length because the
                // file's already on disk.  The API doesn't require this, but
                // in practice it should always happen.
                if (numBytesRead != curChunkSize)
                    {
                    m_log.warn("Unexpected number of bytes read: "+numBytesRead);
                    }
                os.write(bytesToCopy);
                index += numBytesRead;
                }
            }
        //m_log.debug("Wrote range...");
        }
    
    public void onFileComplete()
        {
        m_log.debug("Received notification file is complete");
        this.m_complete = true;
        synchronized (this.m_completedRanges)
            {
            //m_log.debug("Got lock with remaining ranges: {}",m_completedRanges);
            this.m_completedRanges.notify();
            
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
                m_log.debug("Waiting to write remaining bytes...");
                try
                    {
                    this.m_completedRanges.wait();
                    }
                catch (final InterruptedException e)
                    {
                    m_log.error("Interrupted while writing...");
                    }
                }
            }
        m_log.debug("Returning from file complete notification...");
        
        verifySha1();
        }

    private void verifySha1()
        {
        synchronized (m_completedRanges)
            {
            while (!m_writtenAll)
                {
                try
                    {
                    this.m_completedRanges.wait();
                    }
                catch (final InterruptedException e)
                    {
                    m_log.error("Interrupted?", e);
                    }
                }
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
                m_log.warn("Null expected SHA-1.  Testing?");
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

    }
