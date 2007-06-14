package org.lastbamboo.common.download;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.common.util.Assert;
import org.lastbamboo.common.util.RuntimeIoException;

/**
 * A downloader that can download from multiple sources simultaneously.
 */
public final class MultiSourceDownloader
        extends AbstractDownloader<MsDState>
        implements Downloader<MsDState>
    {
    /**
     * The listener we use for notification of events on single source
     * downloaders.  We use one of these for all single downloads (as opposed to
     * one each).
     */
    private final class SingleDownloadListener implements RangeDownloadListener
        {
        /**
         * {@inheritDoc}
         */
        public void onConnect
                (final RangeDownloader downloader)
            {
            LOG.debug ("Connected to: " + downloader);
            
            if (m_numConnections > CONNECTION_LIMIT)
                {
                LOG.debug ("We already have " + m_numConnections +
                                " connections.  Ignoring new host...");
                }
            else if (m_numConnections >= m_rangeTracker.getNumChunks ())
                {
                LOG.debug ("We already have a downloader for every chunk!!");
                }
            else
                {
                m_uniqueSourceUris.add (downloader.getSourceUri ());
                
                m_numConnections++;
                
                if (singleRangeDownload (downloader))
                    {
                    LOG.debug ("Completed download on connect...");
                    }
                }
            }
        
        /**
         * {@inheritDoc}
         */
        public void onDownloadFinished
                (final RangeDownloader downloader)
            {
            if (downloader == null)
                {
                LOG.error("Downloader is null");
                throw new NullPointerException("Null Downloader");
                }
            final long start = m_startTimes.remove (downloader);
            final long end = System.currentTimeMillis ();
            final long size = downloader.getNumBytesDownloaded ();
            
            final RateSegment segment = new RateSegmentImpl (start,
                                                             end - start,
                                                             size);
            
            m_rateSegments.add (segment);
            
            if (isDownloading (m_state))
                {
                setState (new MsDState.DownloadingImpl (getKbs (),
                                                        getNumUniqueHosts ()));
                }
            else
                {
                // We are not in the downloading state.  This is a stray
                // notification.  Do nothing.
                }
            }

        /**
         * {@inheritDoc}
         */
        public void onDownloadStarted
                (final RangeDownloader downloader)
            {
            m_activeRangeDownloaders.add (downloader);
            }
        }
    
    /**
     * The log for this class.
     */
    private static final Log LOG =
            LogFactory.getLog (MultiSourceDownloader.class);
    
    /**
     * Limit on the number of connections to maintain.  We could set this higher
     * in the future and more aggressively purge slow sources.
     */
    private static final int CONNECTION_LIMIT = 20;
    
    private final Object DOWNLOAD_STREAM_LOCK = new Object ();
    
    private final RateCalculator m_rateCalculator;
    
    private final String m_sessionId;
    
    /**
     * The file path to which to write the resource we are downloading.
     */
    private final File m_file;
    
    /**
     * The identifier for the resource we are downloading.
     */
    private final URI m_uri;
    
    private final long m_size;
    
    private final String m_contentType;
    
    /**
     * The resolver used to find sources for the resource we are downloading.
     */
    private final UriResolver m_uriResolver;
    
    /**
     * The number of connections to allow per host.
     */
    private final int m_connectionsPerHost;
    
    /**
     * The listener used to listen to each single range downloader.  We only 
     * need one of these, since the callback methods on the listener indicate
     * on which downloader the event occurred.
     */
    private final RangeDownloadListener m_singleDownloadListener;

    private final Collection<RangeDownloader> m_activeRangeDownloaders;
    
    /**
     * The set of unique source URIs to which we are connected.
     */
    private final Set<URI> m_uniqueSourceUris;
    
    /**
     * The map that we use to track the start times for downloaders.  We use 
     * this to calculate our download rate.
     */
    private final Map<RangeDownloader,Long> m_startTimes;
    
    /**
     * The collection of rate segments that we use to calculate our download
     * rate.
     */
    private final Collection<RateSegment> m_rateSegments;
    
    /**
     * The random access file that we use to write the file we are downloading.
     * We use a random access file, since we download many different parts of
     * the file at once.
     */
    private final RandomAccessFile m_randomAccessFile;
    
    private final RangeTracker m_rangeTracker;
    
    private final LaunchFileTracker m_launchFileTracker;
    
    /**
     * Variable for the number of hosts we've connected to and are actively
     * downloading from.
     */
    private int m_numConnections;
    
    private volatile int m_activeWriteCalls;
    
    /**
     * The current state of this downloader.
     */
    private MsDState m_state;
    
    private boolean m_cancelled;
    
    /**
     * Constructs a new downloader.
     */
    public MultiSourceDownloader
            (final String sessionId, 
             final File file,
             final URI uri,
             final long size,
             final String mimeType,
             final UriResolver uriResolver,
             final int connectionsPerHost)
        {
        Assert.notBlank (sessionId, "Null session ID");
        Assert.notNull (file, "Null file");
        Assert.notNull (uri, "Null URI");
        Assert.notBlank (mimeType, "Null MIME type");
        
        m_rateCalculator = new RateCalculatorImpl ();
        
        m_sessionId = sessionId;
        m_file = file;
        m_uri = uri;
        m_size = size;
        m_contentType = mimeType;
        m_uriResolver = uriResolver;
        m_connectionsPerHost = connectionsPerHost;
        m_singleDownloadListener = new SingleDownloadListener (); 
        
        m_activeRangeDownloaders =
                Collections.synchronizedSet (new HashSet<RangeDownloader> ());
        
        m_uniqueSourceUris =
                Collections.synchronizedSet (new HashSet<URI> ());
        
        m_startTimes = new HashMap<RangeDownloader,Long> ();
        m_rateSegments = new LinkedList<RateSegment> ();
        
        try
            {
            m_randomAccessFile = new RandomAccessFile (file, "rw");
            
            m_rangeTracker = new RangeTrackerImpl (file.getName (), size);
        
            final int numChunks = m_rangeTracker.getNumChunks ();
            
            m_launchFileTracker = new LaunchFileDispatcher (file,
                                                            m_randomAccessFile,
                                                            numChunks);
            
            m_numConnections = 0;
            m_activeWriteCalls = 0;
            m_state = MsDState.IDLE;
            m_cancelled = false;
            }
        catch (final FileNotFoundException e)
            {
            LOG.error ("Could not create file: " + file, e);
            throw new IllegalArgumentException ("Cannot create file: "+file);
            }
        }
    
    /**
     * Returns whether a given state indicates that the download is complete.
     * 
     * @param state
     *      The state.
     *      
     * @return
     *      True if the state indicates that the download is complete, false
     *      otherwise.
     */
    private static boolean isDownloadComplete
            (final MsDState state)
        {
        final MsDState.Visitor<Boolean> visitor =
                new MsDState.VisitorAdapter<Boolean> (false)
            {
            public Boolean visitComplete
                    (final MsDState.Complete state)
                {
                return true;
                }
            };
            
        return state.accept (visitor);
        }
    
    /**
     * Returns whether a given state indicates that we are downloading.
     * 
     * @param state
     *      The state.
     *      
     * @return
     *      True if the state indicates that we are downloading, false
     *      otherwise.
     */
    private static boolean isDownloading
            (final MsDState state)
        {
        final MsDState.Visitor<Boolean> visitor =
                new MsDState.VisitorAdapter<Boolean> (false)
            {
            public Boolean visitDownloading
                    (final MsDState.Downloading state)
                {
                return true;
                }
            };
            
        return state.accept (visitor);
        }
    
    private void cancel
            ()
        {
        m_cancelled = true;
        }
    
    private void connect
            (final Collection<URI> sources, 
             final SourceRanker downloadSpeedRanker,
             final int connectionsPerHost)
        {
        LOG.debug ("Attempting to connection to " + connectionsPerHost +
                      " hosts..");
        
        // Keep a counter of the number of hosts we've issued head requests to.
        // We don't want to send out 1000 head requests, for example, so we
        // cap it.
        int numHosts = 0;
        
        final Iterator<URI> sourcesIter = sources.iterator ();
        
        while (sourcesIter.hasNext () && (numHosts < 100))
            {
            final URI uri = sourcesIter.next ();
            
            // We only use multiple connections to a single host if it's a 
            // straight HTTP server on the public Internet.
            final int connectionsPerHostToCreate =
                    uri.getScheme ().equals ("http") ? connectionsPerHost : 1;
            
            for (int i = 0; i < connectionsPerHostToCreate; i++)
                {
                LOG.debug ("Creating connection...");
                
                final RangeDownloader dl = 
                        new SingleSourceDownloader (uri,
                                                    m_singleDownloadListener, 
                                                    downloadSpeedRanker,
                                                    m_rangeTracker, 
                                                    m_launchFileTracker,
                                                    m_randomAccessFile);
                
                dl.issueHeadRequest ();
                }
            
            ++numHosts;
            }
        }
    
    private void download
            (final Collection<URI> sources)
        {
        if (sources.isEmpty ())
            {
            setState (MsDState.NO_SOURCES_AVAILABLE);
            }
        else
            {
            setState (new MsDState.DownloadingImpl (getKbs (),
                                                    getNumUniqueHosts ()));
            
            final Comparator<RangeDownloader> speedComparator = 
                    new DownloadSpeedComparator ();
            
            final SourceRanker downloadingRanker = 
                    new SourceRankerImpl (speedComparator);
            
            connect (sources, downloadingRanker, m_connectionsPerHost);
            
            boolean done = false;
            
            while (m_rangeTracker.hasMoreRanges () && !m_cancelled && !done)
                {
                LOG.debug ("Accessing next source...");
                
                final RangeDownloader dl = downloadingRanker.getBestSource ();
            
                LOG.debug ("Accessed source...downloading...");
                    
                if (m_cancelled)
                    {
                    done = true;
                    }
                else if (singleRangeDownload (dl))
                    {
                    // This means we're done, so break out of the loop.
                    handleDownloadComplete ();
                    done = true;
                    }
                }
            
            if (m_cancelled)
                {
                setState (MsDState.CANCELED);
                LOG.debug ("The download was cancelled, not downloading " +
                                "any more ranges.");
                }
            }
        }
    
    private int getKbs
            ()
        {
        final long since = System.currentTimeMillis () - 5000;
        
        // The rate comes back in bytes/ms which is the same as kilobytes/s.
        final double rate = m_rateCalculator.getRate (m_rateSegments, since);
        
        return rate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rate;
        }
    
    private int getNumUniqueHosts
            ()
        {
        return m_uniqueSourceUris.size ();
        }
    
    private void handleDownloadComplete
            ()
        {
        LOG.debug ("Downloaded whole file...");
        
        // First notify the launcher because it needs access to the open
        // random access file.
        m_launchFileTracker.onFileComplete ();
        
        // Since the download completion and the file launching happen on
        // different threads, we need to wait until all the launchers have
        // finished here before notifying listeners that have free reign over
        // the downloaded file (like moving it!).
        // We need to make this call after the file complete notification
        // above because that's the only way the luancher ever completes it's
        // write (the launcher waits for the complete notification).
        waitForLaunchersToComplete ();
        
        try
            {
            m_randomAccessFile.close ();
            }
        catch (final IOException e)
            {
            LOG.warn ("Could not close file: " + m_randomAccessFile, e);
            }
        
        setState (MsDState.COMPLETE);
        }
    
    private void setState
            (final MsDState state)
        {
        if (m_state.equals (state))
            {
            // Do nothing.  The state has not changed.
            }
        else
            {
            m_state = state;
            
            LOG.debug ("Setting state to: " + state);
            
            fireStateChanged (state);
            }
        }
    
    private boolean singleRangeDownload
            (final RangeDownloader downloader)
        {
        // It's possible another thread has already resulted in the download
        // completing.
        if (m_rangeTracker.hasMoreRanges ())
            {
            final LongRange range = m_rangeTracker.getNextRange ();
            
            LOG.debug ("Accessed range: " + range);
            
            // The call to get the next range would block if there were no more
            // ranges.  We use this special dummy range to signify that we are
            // done with the download.
            if (range.getMinimumLong () == 183L &&
                    range.getMaximumLong () == 183L)
                {
                LOG.debug ("Received file complete signifier!!");
                return true;
                }
            else
                {
                m_startTimes.put (downloader, System.currentTimeMillis ());
                
                LOG.debug ("Downloading from downloader: " + downloader);
                downloader.download (range);
                return false;
                }
            }
        else
            {
            return true;
            }
        }
    
    /**
     * Waits until all active launcher have finished their writes, typically
     * to the browser.
     */
    private void waitForLaunchersToComplete
            ()
        {
        if (this.m_activeWriteCalls == 0)
            {
            LOG.debug ("No active launchers...");
            return;
            }
        synchronized (this.DOWNLOAD_STREAM_LOCK)
            {
            LOG.debug ("Waiting for active streams to finish streaming");
            try
                {
                // It shouldn't take forever to stream the already
                // downloaded file to the browser, so cap the wait.
                this.DOWNLOAD_STREAM_LOCK.wait (1*60*1000);
                if (this.m_activeWriteCalls > 0)
                    {
                    LOG.warn ("Still " + this.m_activeWriteCalls + 
                        " active writes!!");
                    }
                }
            catch (final InterruptedException e)
                {
                LOG.warn ("Unexpected interrupt!!", e);
                }
            }
        }
    
    /**
     * {@inheritDoc}
     */
    public String getContentType
            ()
        {
        return m_contentType;
        }
    
    /**
     * {@inheritDoc}
     */
    public File getFile
            ()
        {
        return m_file;
        }
    
    /**
     * {@inheritDoc}
     */
    public int getSize
            ()
        {
        assert (m_size <= Integer.MAX_VALUE);
        
        return (int) m_size;
        }
    
    /**
     * {@inheritDoc}
     */
    public String getMimeType
            ()
        {
        return m_contentType;
        }
    
    /**
     * {@inheritDoc}
     */
    public MsDState getState
            ()
        {
        return m_state;
        }

    /**
     * {@inheritDoc}
     */
    public void start
            ()
        {
        LOG.debug ("Resolving download sources...");
        
        setState (MsDState.GETTING_SOURCES);
        
        try
            {
            final Collection<URI> sources = m_uriResolver.resolve (m_uri);
            
            download (sources);
            }
        catch (final IOException e)
            {
            // There was a problem resolving download sources.
            setState (MsDState.COULD_NOT_DETERMINE_SOURCES);
            }
        }
    
    /**
     * {@inheritDoc}
     */
    public void write
            (final OutputStream os)
        {
        try
            {
            ++m_activeWriteCalls;
            
            m_launchFileTracker.write (os);
            
            LOG.debug ("Finished launcher write call...");
            
            synchronized (DOWNLOAD_STREAM_LOCK)
                {
                if (m_activeWriteCalls == 1)
                    {
                    DOWNLOAD_STREAM_LOCK.notify ();
                    }
                }
            }
        catch (final IOException ioe)
            {
            if (m_activeWriteCalls == 1)
                {
                cancel ();
                }
            
            throw new RuntimeIoException (ioe);
            }
        finally
            {
            --m_activeWriteCalls;
            }
        }
    }
