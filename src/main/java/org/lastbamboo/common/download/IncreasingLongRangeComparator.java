package org.lastbamboo.common.download;

import java.util.Comparator;

import org.apache.commons.lang.math.LongRange;

/**
 * Comparator for ranking ranges.  This comparator simply ranks ranges from
 * start to finish.  So, earlier ranges come before later ranges.
 */
public class IncreasingLongRangeComparator implements Comparator<LongRange>
    {

    public int compare(final LongRange range0, final LongRange range1)
        {
        if (range0.getMinimumLong() > range1.getMinimumLong())
            {
            return 1;
            }
        if (range0.getMinimumLong() < range1.getMinimumLong())
            {
            return -1;
            }
        return 0;
        }

    }
