package org.lastbamboo.common.download;

import java.io.File;

import junit.framework.TestCase;

import org.apache.commons.lang.math.LongRange;
import org.littleshoot.util.None;
import org.littleshoot.util.Optional;
import org.littleshoot.util.OptionalVisitor;
import org.littleshoot.util.Some;

/**
 * Tests the range tracker class.
 */
public class RangeTrackerImplTest extends TestCase
    {

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
        final RangeSizeSelector selector = new DefaultRangeSizeSelector();
        final RangeTracker rt = new RangeTrackerImpl(size, 1, selector);
        
        final Optional<LongRange> oRange = rt.getNextRange();
        
        final OptionalVisitor<Void,LongRange> visitor =
                new OptionalVisitor<Void,LongRange> ()
            {
            public Void visitNone (final None<LongRange> none)
                {
                return null;
                }
            
            public Void visitSome(final Some<LongRange> some)
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
