package org.lastbamboo.common.download;

/**
 * An implementation of the rate segment interface.
 */
public final class RateSegmentImpl implements RateSegment
    {
    /**
     * The start time of this segment.
     */
    private final long m_start;
    
    /**
     * The duration of this segment.
     */
    private final long m_duration;
    
    /**
     * The size of this segment.
     */
    private final long m_size;
    
    /**
     * Constructs a new segment.
     * 
     * @param start
     *      The start time of this segment.
     * @param duration
     *      The duraction of this segment.
     * @param size
     *      The size of this segment.
     */
    public RateSegmentImpl
            (final long start,
             final long duration,
             final long size)
        {
        m_start = start;
        m_duration = duration;
        m_size = size;
        }
    
    /**
     * {@inheritDoc}
     */
    public long getDuration
            ()
        {
        return m_duration;
        }

    /**
     * {@inheritDoc}
     */
    public long getSize
            ()
        {
        return m_size;
        }

    /**
     * {@inheritDoc}
     */
    public long getStart
            ()
        {
        return m_start;
        }
    }
