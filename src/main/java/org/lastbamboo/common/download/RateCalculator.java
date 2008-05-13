package org.lastbamboo.common.download;

import java.util.Collection;

/**
 * An interface to an object that helps calculate rates.
 */
public interface RateCalculator
    {
    /**
     * Returns the rate since a given time.
     * 
     * @param segments The rate segments that abstractly describe the activity.
     * @param since The time.
     *      
     * @return The rate since the given time.
     */
    double getRate (Collection<RateSegment> segments, long since);
    }
