package org.lastbamboo.common.download;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class that ranks available download sources.  This will provide callers 
 * with the best available source for downloading the next chunk.
 */
public class SourceRankerImpl implements SourceRanker
    {

    private static final Log LOG = LogFactory.getLog(SourceRankerImpl.class);
    
    private final PriorityBlockingQueue<RangeDownloader> m_sources;
    
    /**
     * Creates a new ranker.
     * @param comparator The comparator to use for judging sources.
     */
    public SourceRankerImpl(final Comparator<RangeDownloader> comparator)
        {
        this.m_sources = 
            new PriorityBlockingQueue<RangeDownloader>(20, comparator);
        }

    public boolean hasMoreSources()
        {
        return !this.m_sources.isEmpty();
        }

    public RangeDownloader getBestSource()
        {
        try
            {
            return this.m_sources.take();
            }
        catch (final InterruptedException e)
            {
            LOG.warn("Interrupt waiting for sources!!", e);
            return null;
            }
        }

    public void onAvailable(final RangeDownloader downloader)
        {
        if (LOG.isDebugEnabled())
            {
            LOG.debug("New source available: " + downloader);
            }
        
        this.m_sources.add(downloader);
        }
    }
