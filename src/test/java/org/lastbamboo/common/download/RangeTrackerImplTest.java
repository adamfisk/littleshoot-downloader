package org.lastbamboo.common.download;

import java.io.File;

import junit.framework.TestCase;

import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.common.util.None;
import org.lastbamboo.common.util.Optional;
import org.lastbamboo.common.util.OptionalVisitor;
import org.lastbamboo.common.util.Some;

/**
 * Tests the range tracker class.
 */
public class RangeTrackerImplTest extends TestCase
    {

    private static final Log LOG = 
        LogFactory.getLog(RangeTrackerImplTest.class);
    
    /**
     * Tests the building of the file to launch.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testLaunchFileOrderedReading() throws Exception
        {
        /*
        final File file = new File("cnn.mp4");
        assertTrue(file.isFile());
        final URI expectedHash = Sha1Hasher.createSHA1URN(file);
        final String launchName = "cnn-launch.mp4";
        
        final File launchFile = new File(launchName);
        launchFile.delete();
        launchFile.deleteOnExit();
        assertFalse(launchFile.isFile());
        
        final RangeTracker rt = 
            new RangeTrackerImpl(file.getName(), file.length());
        
        while(rt.hasMoreRanges())
            {
            LOG.debug("Accessing next range...");
            final LongRange range = rt.getNextRange();
            rt.onRangeComplete(range);
            }
        
        assertTrue(launchFile.isFile());
        final URI hash = Sha1Hasher.createSHA1URN(launchFile);
        
        assertEquals(expectedHash, hash);
        */
        }
    
    /**
     * Tests the building of the file to launch.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testLaunchFileUnorderedReading() throws Exception
        {
        /*
        final File file = new File("cnn.mp4");
        assertTrue(file.isFile());
        final URI expectedHash = Sha1Hasher.createSHA1URN(file);
        final String launchName = "cnn-launch.mp4";
        final File launchFile = new File(launchName);
        launchFile.delete();
        launchFile.deleteOnExit();
        assertFalse(launchFile.isFile());
        
        final RangeTracker rt = 
            new RangeTrackerImpl(file.getName(), file.length());
        
        final List reversedRanges = new LinkedList();
        
        // We just create a set of mixed of ranges.  This is a little weird
        // here because of the way getNextRange blocks...
        final Set mixedRanges = new HashSet();
        for (int i = 0; i < 10; i++)
            {
            final LongRange range = rt.getNextRange();
            LOG.debug("Got range: "+range);
            mixedRanges.add(range);
            }
        for (final Iterator iter = mixedRanges.iterator(); iter.hasNext();)
            {
            final LongRange range = (LongRange) iter.next();
            LOG.debug("Completing range: "+range);
            rt.onRangeComplete(range);
            }
        
        // Now finish off the remaining ranges.
        LOG.debug("Finishing off remaining ranges...");
        while(rt.hasMoreRanges())
            {
            LOG.debug("Accessing next range...");
            final LongRange range = rt.getNextRange();
            rt.onRangeComplete(range);
            }
        
        LOG.debug("Looping through reversed...");
        for (final Iterator iter = reversedRanges.iterator(); iter.hasNext();)
            {
            final LongRange range = (LongRange) iter.next();
            rt.onRangeComplete(range);
            }
        
        assertTrue(launchFile.isFile());
        final URI hash = Sha1Hasher.createSHA1URN(launchFile);
        
        assertEquals(expectedHash, hash);
        */
        }
    
    /**
     * Test the tracker.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testTracker() throws Exception
        {
        final long size = 41021L;
        final File testFile = new File("testFile");
        testFile.deleteOnExit();
        final RangeTracker rt = new RangeTrackerImpl(size);
        
        final Optional<LongRange> oRange = rt.getNextRange();
        
        final OptionalVisitor<Void,LongRange> visitor =
                new OptionalVisitor<Void,LongRange> ()
            {
            public Void visitNone
                    (final None<LongRange> none)
                {
                return null;
                }
            
            public Void visitSome
                    (final Some<LongRange> some)
                {
                final LongRange range = some.object ();
                assertEquals(0, range.getMinimumLong());
                assertEquals(size-1, range.getMaximumLong());
                assertTrue (rt.hasMoreRanges());
        
                rt.onRangeComplete(range);
                assertFalse (rt.hasMoreRanges());
                
                return null;
                }
            };
            
        oRange.accept (visitor);
        }
    }
