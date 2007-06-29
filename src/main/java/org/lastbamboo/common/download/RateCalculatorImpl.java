package org.lastbamboo.common.download;

import java.util.Collection;

import org.lastbamboo.common.util.CollectionUtils;
import org.lastbamboo.common.util.CollectionUtilsImpl;
import org.lastbamboo.common.util.F1;
import org.lastbamboo.common.util.NoneImpl;
import org.lastbamboo.common.util.Optional;
import org.lastbamboo.common.util.OptionalUtils;
import org.lastbamboo.common.util.OptionalUtilsImpl;
import org.lastbamboo.common.util.SomeImpl;

/**
 * An implementation of the rate calculator interface.
 */
public class RateCalculatorImpl implements RateCalculator
    {
    /**
     * Collection utilities.
     */
    private final CollectionUtils m_collectionUtils;
    
    /**
     * Optional utilities.
     */
    private final OptionalUtils m_optionalUtils;
    
    /**
     * Returns the size calculated from a partial rate segment.
     * 
     * @param partialDuration
     *      The partial duration.
     * @param duration
     *      The full duration.
     * @param size
     *      The full size.
     *      
     * @return
     *      The partial size.
     */
    private static long getPartialSize
            (final long partialDuration,
             final long duration,
             final long size)
        {
        return duration == 0 ?
                size :
                (long) ((partialDuration / (double) duration) * size);
        }
    
    /**
     * Constructs a new rate calculator.
     */
    public RateCalculatorImpl
            ()
        {
        m_collectionUtils = new CollectionUtilsImpl ();
        m_optionalUtils = new OptionalUtilsImpl ();
        }
    
    /**
     * {@inheritDoc}
     */
    public double getRate
            (final Collection<RateSegment> segments,
             final long since)
        {
        final F1<RateSegment,Optional<RateSegment>> f =
                new F1<RateSegment,Optional<RateSegment>> ()
            {
            public Optional<RateSegment> run
                    (final RateSegment segment)
                {
                final long end = segment.getStart () + segment.getDuration ();
                
                if (end <= since)
                    {
                    return new NoneImpl<RateSegment> ();
                    }
                else
                    {
                    if (since <= segment.getStart ())
                        {
                        return new SomeImpl<RateSegment> (segment);
                        }
                    else
                        {
                        final long partialDuration = end - since;
                        
                        final long partialSize =
                                getPartialSize (partialDuration,
                                                segment.getDuration (),
                                                segment.getSize ());
                        
                        final RateSegment partial =
                                new RateSegmentImpl (since,
                                                     partialDuration,
                                                     partialSize);
                        
                        return new SomeImpl<RateSegment> (partial);
                        }
                    }
                }
            };
            
        final Collection<RateSegment> valid;
        synchronized (segments)
            {
            valid = m_optionalUtils.filterNones
                    (m_collectionUtils.map (segments, f));
            }
        
        final F1<RateSegment,Long> getDuration = new F1<RateSegment,Long> ()
            {
            public Long run
                    (final RateSegment segment)
                {
                return segment.getDuration ();
                }
            };
            
        final Collection<Long> durations =
                m_collectionUtils.map (valid, getDuration);
        
        final F1<RateSegment,Long> getSize = new F1<RateSegment,Long> ()
            {
            public Long run
                    (final RateSegment segment)
                {
                return segment.getSize ();
                }
            };
            
        final Collection<Long> sizes =
                m_collectionUtils.map (valid, getSize);
        
        final long totalDuration = m_collectionUtils.sum (durations);
        final long totalSize = m_collectionUtils.sum (sizes);
        
        return totalDuration > 0 ? totalSize / (double) totalDuration : 0.0;
        }
    }
