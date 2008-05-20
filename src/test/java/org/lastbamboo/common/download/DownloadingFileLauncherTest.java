package org.lastbamboo.common.download;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.lang.math.LongRange;
import org.junit.Test;
import org.lastbamboo.common.util.Sha1Hasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Download streaming test.
 */
public class DownloadingFileLauncherTest
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    
    /**
     * Tests to make sure we don't get OutOfMemoryErrors trying to stream
     * files -- typically through copying too many bytes at once.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    @Test public void testOome() throws Exception
        {
        final File file = new File(getClass().getSimpleName());
        file.deleteOnExit();
        final OutputStream os = new FileOutputStream(file);
        final byte[] bytes = new byte[10000000];
        for (int i = 0; i < bytes.length ; i++)
            {
            bytes[i] = (byte) (i % 127);
            }
        os.write(bytes);
        os.write(bytes);
        os.write(bytes);
        os.write(bytes);
        os.write(bytes);
        os.write(bytes);
        os.write(bytes);
        //os.write()
        os.close();

        /*
        for (int i = 0; i < 10000000; i++)
            {
            os.write(i);
            }
        os.close();
        */
        
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        final DownloadingFileLauncher launcher = 
            new DownloadingFileLauncher(raf, completedRanges, sha1, file);
        
        final File fileCopy = new File(file.getName()+"Copy");
        fileCopy.deleteOnExit();
        final OutputStream stream = new FileOutputStream(fileCopy);
        final Runnable runner = new Runnable()
            {
            public void run()
                {
                try
                    {
                    Thread.sleep(400);
                    }
                catch (InterruptedException e)
                    {
                    e.printStackTrace();
                    }
                final Collection<LongRange> ranges = createRanges(file.length());
                //Collections.reverse(ranges);
                m_log.debug("Ranges: "+ranges);
                LongRange minRange = null;
                for (final LongRange lr : ranges)
                    {
                    if (lr.getMinimumLong() != 0L)
                        {
                        launcher.onRangeComplete(lr);
                        }
                    else
                        {
                        minRange = lr;
                        }
                    }
                
                
                launcher.onRangeComplete(minRange);
                launcher.onFileComplete();
                }
            };
        final Thread thread = new Thread(runner, "test-thread");
        thread.setDaemon(true);
        thread.start();
        
        launcher.write(stream, true);
        
        final URI sha1Copy = Sha1Hasher.createSha1Urn(fileCopy);
        assertEquals(sha1, sha1Copy);
        m_log.debug("Copy SHA-1: "+sha1Copy);
        }
    
    @Test public void testLauncher() throws Exception
        {
        final File file = new File(getClass().getSimpleName());
        file.deleteOnExit();
        final OutputStream os = new FileOutputStream(file);
        for (int i = 0; i < 100000; i++)
            {
            os.write(i);
            }
        os.close();
        
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        final DownloadingFileLauncher launcher = 
            new DownloadingFileLauncher(raf, completedRanges, sha1, file);
        
        final File fileCopy = new File(file.getName()+"Copy");
        fileCopy.deleteOnExit();
        final OutputStream stream = new FileOutputStream(fileCopy);
        final Runnable runner = new Runnable()
            {
            public void run()
                {
                try
                    {
                    Thread.sleep(400);
                    }
                catch (final InterruptedException e)
                    {
                    e.printStackTrace();
                    }
                final Collection<LongRange> ranges = createRanges(file.length());
                m_log.debug("Ranges: "+ranges);
                for (final LongRange lr : ranges)
                    {
                    launcher.onRangeComplete(lr);
                    }
                launcher.onFileComplete();
                }
            };
        final Thread thread = new Thread(runner, "test-thread");
        thread.setDaemon(true);
        thread.start();
        
        launcher.write(stream, true);
        
        final URI sha1Copy = Sha1Hasher.createSha1Urn(fileCopy);
        assertEquals(sha1, sha1Copy);
        m_log.debug("Copy SHA-1: "+sha1Copy);
        }
    
    private Collection<LongRange> createRanges(final long length)
        {
        final LinkedList<LongRange> ranges = new LinkedList<LongRange>();
        
        final long rangeSize = length / 1000L;
        long min = 0;
        
        while (true)
            {
            final long max = min + rangeSize - 1;
            final LongRange curRange = new LongRange(min, max);
            ranges.add(curRange);
            
            if (max == (length - 1))
                {
                break;
                }
            min = max + 1;
            }
        Collections.shuffle(ranges);
        return ranges;
        }

    private PriorityBlockingQueue<LongRange> createQueue()
        {
        final Comparator<LongRange> increasingRangeComparator = 
            new IncreasingLongRangeComparator();
        return new PriorityBlockingQueue<LongRange>(10, 
            increasingRangeComparator);
        }
    }
