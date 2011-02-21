package org.lastbamboo.common.download;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.littleshoot.util.CollectionUtils;
import org.littleshoot.util.CollectionUtilsImpl;
import org.littleshoot.util.F1;
import org.littleshoot.util.NoneImpl;
import org.littleshoot.util.Optional;
import org.littleshoot.util.OptionalUtils;
import org.littleshoot.util.OptionalUtilsImpl;
import org.littleshoot.util.SomeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the rate calculator interface.
 */
public class RateCalculatorImpl implements RateCalculator
    {
    
    private final Logger m_log = LoggerFactory.getLogger(getClass());
    
    /**
     * Collection utilities.
     */
    private final CollectionUtils m_collectionUtils;
    
    /**
     * Optional utilities.
     */
    private final OptionalUtils m_optionalUtils;
    
    private final Map<Long, RateSegment> m_rateSegments = 
        Collections.synchronizedMap(new LinkedHashMap<Long, RateSegment>()
            {

            private static final long serialVersionUID = -243416056459048328L;

            @Override
            protected boolean removeEldestEntry(
                final Map.Entry<Long, RateSegment> eldest) 
                {
                // This makes the map automatically purge the least used
                // entry.  
                final boolean remove = size() > 100;
                return remove;
                }
            });
    
    /**
     * Returns the size calculated from a partial rate segment.
     * 
     * @param partialDuration The partial duration.
     * @param duration The full duration.
     * @param size The full size.
     *      
     * @return The partial size.
     */
    private static long getPartialSize (final long partialDuration,
        final long duration, final long size)
        {
        return duration == 0 ?
                size :
                (long) ((partialDuration / (double) duration) * size);
        }
    
    /**
     * Constructs a new rate calculator.
     */
    public RateCalculatorImpl ()
        {
        m_collectionUtils = new CollectionUtilsImpl ();
        m_optionalUtils = new OptionalUtilsImpl ();
        }
    
    /**
     * {@inheritDoc}
     */
    public double getRate ()
        {
        if (m_log.isDebugEnabled())
            {
            //m_log.debug("Accessing the download rate...");
            }
        final long since = System.currentTimeMillis () - 5000;
        final F1<RateSegment,Optional<RateSegment>> f =
                new F1<RateSegment,Optional<RateSegment>> ()
            {
            public Optional<RateSegment> run(final RateSegment segment)
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
        
        final Collection<RateSegment> segments;
        synchronized (this.m_rateSegments)
            {
            segments = 
                new LinkedList<RateSegment>(this.m_rateSegments.values());
            }
        synchronized (segments)
            {
            valid = m_optionalUtils.filterNones
                    (m_collectionUtils.map (segments, f));
            }
        
        final F1<RateSegment,Long> getDuration = new F1<RateSegment,Long> ()
            {
            public Long run (final RateSegment segment)
                {
                return segment.getDuration ();
                }
            };
            
        final Collection<Long> durations =
                m_collectionUtils.map (valid, getDuration);
        
        final F1<RateSegment,Long> getSize = new F1<RateSegment,Long> ()
            {
            public Long run (final RateSegment segment)
                {
                return segment.getSize ();
                }
            };
            
        final Collection<Long> sizes =
                m_collectionUtils.map (valid, getSize);
        
        final long totalDuration = m_collectionUtils.sum (durations);
        final long totalSize = m_collectionUtils.sum (sizes);
        
        final double rate =
            totalDuration > 0 ? totalSize / (double) totalDuration : 0.0;
            
        //m_log.debug("Returning rate: {}", rate);
        return rate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rate;
        }

    public void addData(final RangeDownloader downloader)
        {
        //m_log.debug("Adding data...");
        final long start = downloader.getRangeStartTime();
        final long end = System.currentTimeMillis ();
        final long size = downloader.getNumBytesDownloaded ();
        
        final RateSegment segment = 
            new RateSegmentImpl (start, end - start, size);
        
        final long rangeIndex = downloader.getRangeIndex();
        
        m_rateSegments.put(new Long(rangeIndex), segment);
        }

    public long getBytesRead()
        {
        long bytesRead = 0;
        synchronized (this.m_rateSegments)
            {
            for (final RateSegment segment : this.m_rateSegments.values())
                {
                bytesRead += segment.getSize();
                }
            }
        return bytesRead;
        }
    }
