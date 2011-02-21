package org.lastbamboo.common.download;

import org.apache.commons.lang.math.LongRange;
import org.littleshoot.util.Optional;

/**
 * Adapter for a range tracker.
 */
public class RangeTrackerAdapter implements RangeTracker
    {

    public long getBytesRead()
        {
        return 0;
        }

    public Optional<LongRange> getNextRange()
        {
        return null;
        }

    public int getNumChunks()
        {
        return 0;
        }

    public boolean hasMoreRanges()
        {
        return false;
        }

    public void onRangeComplete(LongRange range)
        {
        }

    public void onRangeFailed(LongRange range)
        {
        }

    }
