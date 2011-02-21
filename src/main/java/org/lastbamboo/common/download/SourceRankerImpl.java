package org.lastbamboo.common.download;

import java.net.URI;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.commons.lang.math.LongRange;
import org.littleshoot.util.Optional;
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
            m_log.warn("Interrupt waiting for sources!!", e);
            return null;
            }
        }

    public void onAvailable(final RangeDownloader downloader)
        {
        m_log.debug("New source available: {}", downloader);
        this.m_sources.add(downloader);
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

        public long getRangeIndex()
            {
            return 0;
            }

        public long getRangeStartTime()
            {
            return 0;
            }
        }
    }
