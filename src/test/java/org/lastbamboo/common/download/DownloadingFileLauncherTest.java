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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.math.LongRange;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import org.littleshoot.util.DaemonThread;
import org.littleshoot.util.None;
import org.littleshoot.util.Optional;
import org.littleshoot.util.OptionalVisitor;
import org.littleshoot.util.Sha1Hasher;
import org.littleshoot.util.Some;
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
                final Collection<LongRange> ranges = 
                    createRangesBasic(file.length());
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
        final Thread thread = new Thread(runner, "test-thread"+hashCode());
        thread.setDaemon(true);
        thread.start();
        
        launcher.write(stream, true);
        
        final URI sha1Copy = Sha1Hasher.createSha1Urn(fileCopy);
        assertEquals(sha1, sha1Copy);
        m_log.debug("Copy SHA-1: "+sha1Copy);
        }
    
    /**
     * This test attempts to simulate a more realistic download using a
     * RangeTracker.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    @Test public void testLauncherWithRangeTracker() throws Exception
        {
        final File file = new File(getClass().getSimpleName());
        file.deleteOnExit();
        final OutputStream os = new FileOutputStream(file);
        final byte[] bytes = new byte[4000000];
        for (int i = 0; i < bytes.length; i++)
            {
            bytes[i] = (byte)i;
            }
        os.write(bytes);
        os.close();
        //System.out.println("Wrote file");
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        final DownloadingFileLauncher launcher = 
            new DownloadingFileLauncher(raf, completedRanges, sha1, file);
        
        final File fileCopy = new File(file.getName()+"Copy");
        fileCopy.deleteOnExit();
        final OutputStream stream = new FileOutputStream(fileCopy);
        final int numDownloaders = 100;
        final RangeTracker tracker = new RangeTrackerImpl(file.length(), numDownloaders);
    
        startRangeTrackerThreads(launcher, tracker, numDownloaders);
        
        launcher.write(stream, true);
        
        final URI sha1Copy = Sha1Hasher.createSha1Urn(fileCopy);
        assertEquals(sha1, sha1Copy);
        m_log.debug("Copy SHA-1: "+sha1Copy);
        }
    
    private void startRangeTrackerThreads(final DownloadingFileLauncher launcher, 
        final RangeTracker tracker, final int numDownloaders)
        {
        final LinkedList<Thread> threads = new LinkedList<Thread>();
        final AtomicInteger rangesComplete = new AtomicInteger(0);
        
        final AtomicBoolean notifiedOfComplete = new AtomicBoolean(false);
        for (int i = 0; i < numDownloaders; i++)
            {
            final Runnable runner = new Runnable()
                {
                public void run()
                    {
                    while (tracker.hasMoreRanges())
                        {
                        final Optional<LongRange> opt = tracker.getNextRange();
                        final OptionalVisitor<LongRange, LongRange> visitor =
                            new OptionalVisitor<LongRange, LongRange>()
                            {
                            public LongRange visitNone(None<LongRange> none)
                                {
                                return null;
                                }
                            public LongRange visitSome(Some<LongRange> some)
                                {
                                return some.object();
                                }
                            };
                        final LongRange lr = opt.accept(visitor);
                        if (lr == null)
                            {
                            continue;
                            }
                        try
                            {
                            Thread.sleep(RandomUtils.nextInt() % 20);
                            }
                        catch (final InterruptedException e)
                            {
                            e.printStackTrace();
                            }
                        //System.out.println("Got range: "+lr);
                        launcher.onRangeComplete(lr);
                        tracker.onRangeComplete(lr);
                        
                        rangesComplete.incrementAndGet();
                        }
                    
                    if (!notifiedOfComplete.getAndSet(true))
                        {
                        launcher.onFileComplete();
                        }
                    }
                };
            
            // We'll run it on a bunch of threads to really confuse things...
            final Thread thread = 
                new DaemonThread(runner, "test-thread-"+i+" - "+hashCode());
            threads.add(thread);
            }
        
        // As much randomness as possible.
        //Collections.shuffle(threads);
        //System.out.println("Starting num threads: "+threads.size());
        for (final Thread thread : threads)
            {
            //System.out.println("Starting thread: "+thread.getName());
            thread.start();
            }
        }
        
    @Test public void testLauncher() throws Exception
        {
        final File file = new File(getClass().getSimpleName());
        file.deleteOnExit();
        final OutputStream os = new FileOutputStream(file);
        final byte[] bytes = new byte[10000000];
        for (int i = 0; i < bytes.length; i++)
            {
            bytes[i] = (byte)i;
            }
        os.write(bytes);
        os.close();
        
        final RandomAccessFile raf = new RandomAccessFile(file, "rw");
        
        final PriorityBlockingQueue<LongRange> completedRanges = createQueue();
        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        final DownloadingFileLauncher launcher = 
            new DownloadingFileLauncher(raf, completedRanges, sha1, file);
        
        final File fileCopy = new File(file.getName()+"Copy");
        fileCopy.deleteOnExit();
        final OutputStream stream = new FileOutputStream(fileCopy);
        
        final Collection<LinkedList<LongRange>> ranges = createRanges(file.length());

        startThreads(launcher, ranges);
        
        launcher.write(stream, true);
        
        final URI sha1Copy = Sha1Hasher.createSha1Urn(fileCopy);
        assertEquals(sha1, sha1Copy);
        m_log.debug("Copy SHA-1: "+sha1Copy);
        }
    
    private void startThreads(final DownloadingFileLauncher launcher, 
        final Collection<LinkedList<LongRange>> ranges)
        {
        final LinkedList<Thread> threads = new LinkedList<Thread>();
        int index = 0;
        final AtomicInteger rangesComplete = new AtomicInteger(0);
        int tempTotalRanges = 0;
        for (final LinkedList<LongRange> curRanges : ranges)
            {
            tempTotalRanges += curRanges.size();
            }
        final int totalRanges = tempTotalRanges;
        for (final LinkedList<LongRange> curRanges : ranges)
            {
            final Runnable runner = new Runnable()
                {
                public void run()
                    {
                    //m_log.debug("Ranges: "+ranges);
                    for (final LongRange lr : curRanges)
                        {
                        try
                            {
                            Thread.sleep(4);
                            }
                        catch (final InterruptedException e)
                            {
                            e.printStackTrace();
                            }
                        launcher.onRangeComplete(lr);
                        rangesComplete.incrementAndGet();
                        }
                    
                    if (rangesComplete.get() == totalRanges)
                        {
                        launcher.onFileComplete();
                        }
                    }
                };
            
            // We'll run it on a bunch of threads to really confuse things...
            final Thread thread = 
                new DaemonThread(runner, "test-thread-"+index);
            threads.add(thread);
            index++;
            }
        
        // As much randomness as possible.
        Collections.shuffle(threads);
        for (final Thread thread : threads)
            {
            //System.out.println("Starting thread: "+thread.getName());
            thread.start();
            }
        }

    private Collection<LinkedList<LongRange>> createRanges(final long length)
        {
        final LinkedList<LinkedList<LongRange>> ranges = 
            new LinkedList<LinkedList<LongRange>>();
        LinkedList<LongRange> curRanges = new LinkedList<LongRange>();
        
        final long rangeSize = length / 100000L;
        long min = 0;
        
        while (true)
            {
            final long max = min + rangeSize - 1;
            final LongRange curRange = new LongRange(min, max);
            curRanges.add(curRange);
            
            if (curRanges.size() >= 10)
                {
                //Collections.shuffle(curRanges);
                ranges.add(curRanges);
                curRanges = new LinkedList<LongRange>();
                }
            
            if (max == (length - 1))
                {
                break;
                }
            min = max + 1;
            }
        //Collections.shuffle(ranges);
        return ranges;
        }
    
    private Collection<LongRange> createRangesBasic(final long length)
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
