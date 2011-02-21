package org.lastbamboo.common.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.security.DigestOutputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.math.LongRange;
import org.junit.Test;
import org.lastbamboo.common.download.stubs.LaunchFileTrackerStub;
import org.littleshoot.util.Sha1;

public class LaunchFileDispatcherTest
    {
    
    @Test public void testOome() throws Exception
        {
        final File file = 
            File.createTempFile(getClass().getSimpleName(), ".tmp");
        file.deleteOnExit();
        final RandomAccessFile raf = new RandomAccessFile (file, "rw");
        final URI sha1 = new URI("urn:sha1:42930jIRU08r0820");
        final int numChunks = 10000000;
        final LaunchFileDispatcher launchFileTracker = 
            new LaunchFileDispatcher (file, raf, numChunks, sha1);

        final long oneMillion = 1000000;
        final long limit = oneMillion * 10;
        
        
        final boolean[] bits = new boolean[(int) limit];
        for (final boolean bit : bits)
            {
            // dummy check.
            assertFalse(bit);
            }
        final AtomicLong ranges = new AtomicLong();
        final LaunchFileTracker tracker = new LaunchFileTrackerStub()
            {

            public void onRangeComplete(final LongRange range)
                {
                ranges.incrementAndGet();
                
                final int bit = (int) (range.getMinimumLong() / 1000);
                if (bits[bit] == true)
                    {
                    throw new IllegalStateException("Bit already set!!!");
                    }
                bits[bit] = true;
                }
            };
            
        launchFileTracker.addTracker(tracker);
        
        LongRange missingLink = null;

        for (long i = 0; i < limit; i++)
            {
            final LongRange lr = new LongRange(i*1000, i*1000 + 999);
            
            if (i % 100 == 0)
                {
                if (missingLink != null)
                    {
                    launchFileTracker.onRangeComplete(missingLink);
                    //break;
                    }
                missingLink = lr;
                }
            else
                {
                launchFileTracker.onRangeComplete(lr);
                }
            }
        launchFileTracker.onRangeComplete(missingLink);
        //assertEquals(limit, ranges.get());
        
        for (final boolean bit : bits)
            {
            //assertTrue(bit);
            }
        
        final Collection<LongRange> completedRanges = launchFileTracker.getRanges();
        
        assertEquals("Should only be 1 range", 1, completedRanges.size());
        }
    }
