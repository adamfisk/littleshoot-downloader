package org.lastbamboo.common.download;

import java.util.Comparator;

import org.apache.commons.lang.math.LongRange;

/**
 * Comparator for ranking ranges.  We preference the beginning of files, but
 * this should also add some randomization so that not everyone ends up 
 * needing the same bytes at the end of the file.
 */
public class LongRangeComparator implements Comparator<LongRange>
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
