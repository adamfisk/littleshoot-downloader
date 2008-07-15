package org.lastbamboo.common.download;

import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that ranks available download sources.  This will provide callers 
 * with the best available source for downloading the next chunk.
 */
public class SourceRankerImpl implements SourceRanker
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass()); 
    
    private final PriorityBlockingQueue<RangeDownloader> m_sources;
    
    private final Collection<RangeDownloader> m_uniqueDownloaders =
        new HashSet<RangeDownloader>();
    
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
            final RangeDownloader rd = this.m_sources.take();
            if (rd != null)
                {
                this.m_uniqueDownloaders.remove(rd);
                }
            return rd;
            }
        catch (final InterruptedException e)
            {
            m_log.warn("Interrupt waiting for sources!!", e);
            return null;
            }
        }

    public void onAvailable(final RangeDownloader downloader)
        {
        m_log.debug("New source available: {}", downloader);
        this.m_sources.add(downloader);
        
        /*
        synchronized (this.m_uniqueDownloaders)
            {
            if (this.m_uniqueDownloaders.contains(downloader))
                {
                m_log.warn("We already have the downloader!! {}", downloader);
                }
            else
                {
                this.m_uniqueDownloaders.add(downloader);
                this.m_sources.add(downloader);
                }
            }
            */
        }

    public void onFailed()
        {
        // We add a dummy failed downloader just to break the source ranker
        // out of its wait.  This is never used during the download process,
        // as this failure indicates all sources have failed, and we're done.
        this.m_sources.add(new DummyRangeDownloader());
        }
    
    private static final class DummyRangeDownloader implements RangeDownloader
        {

        public void download(LongRange range)
            {
            }

        public Optional<Integer> getKbs()
            {
            return null;
            }

        public long getNumBytesDownloaded()
            {
            return 0;
            }

        public URI getSourceUri()
            {
            return null;
            }

        public void issueHeadRequest()
            {
            }
        }
    }
