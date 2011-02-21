package org.lastbamboo.common.download;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.prefs.Preferences;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.http.client.CommonsHttpClient;
import org.lastbamboo.common.http.client.CommonsHttpClientImpl;
import org.littleshoot.util.None;
import org.littleshoot.util.Optional;
import org.littleshoot.util.OptionalVisitor;
import org.littleshoot.util.ResettingMultiThreadedHttpConnectionManager;
import org.littleshoot.util.Some;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A downloader that can download from multiple sources simultaneously.
 */
public final class MultiSourceDownloader extends AbstractDownloader<MsDState>
    implements VisitableDownloader<MsDState>, LittleShootDownloader {
    
    /**
     * The log for this class.
     */
    private final Logger m_log = 
        LoggerFactory.getLogger(MultiSourceDownloader.class);
    
    /**
     * Limit on the number of connections to maintain.  We could set this higher
     * in the future and more aggressively purge slow sources.
     */
    private static final int CONNECTION_LIMIT = 30;

    private final SourceRanker m_downloadingRanker = 
        new SourceRankerImpl (new DownloadSpeedComparator ());
    
    private final RateCalculator m_rateCalculator = new RateCalculatorImpl ();
    
    /**
     * The file path to which to write the resource we are downloading.
     */
    private final File m_incompleteFile;
    
    /**
     * The identifier for the resource we are downloading.
     */
    private final URI m_uri;
    
    private final long m_size;
    
    /**
     * The resolver used to find sources for the resource we are downloading.
     */
    private final UriResolver m_uriResolver;
    
    /**
     * The number of connections to allow per HTTP server.
     */
    private final int m_connectionsPerHttpServer;
    
    /**
     * The listener used to listen to each single range downloader.  We only 
     * need one of these, since the callback methods on the listener indicate
     * on which downloader the event occurred.
     */
    private final RangeDownloadListener m_singleDownloadListener =
        new SingleDownloadListener ();

    private final Collection<RangeDownloader> m_activeRangeDownloaders =
        Collections.synchronizedSet (new HashSet<RangeDownloader> ());
    
    /**
     * The set of unique source URIs to which we are connected.
     */
    private final Set<URI> m_uniqueSourceUris =
        Collections.synchronizedSet (new HashSet<URI> ());
   
    /**
     * The set of unique source URIs that have failed.
     */
    private final Set<URI> m_uniqueFailedSourceUris =
        Collections.synchronizedSet (new HashSet<URI> ());
    
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
    private volatile int m_numConnections = 0;
    
    /**
     * The current state of this downloader.
     */
    private volatile MsDState m_state = MsDState.IDLE;
    
    private volatile boolean m_stopped = false;

    private MultiThreadedHttpConnectionManager m_connectionManager =
        new ResettingMultiThreadedHttpConnectionManager();
    
    private final CommonsHttpClient m_httpClient =
        new CommonsHttpClientImpl(m_connectionManager);

    private volatile boolean m_started;

    private final String m_finalName;

    private final File m_completeFile;

    private Collection<URI> m_sources = Collections.emptyList();

    private volatile boolean m_failed = false;

    private final boolean m_streamable;

    /**
     * Constructs a new downloader.
     * 
     * @param incompleteFile The path for the incomplete file we're downloading
     * to.  This will of course be complete when we're done. 
     * @param uri The URI for the file.
     * @param size The size of the file in bytes. 
     * @param uriResolver The class we'll use to resolve all initial locations
     * for the file.
     * @param connectionsPerHost The number of connections to allow to each
     * HTTP server.  Multiple connections to HTTP servers can speed up 
     * transfers.
     * @param expectedSha1 The expected SHA-1 URN.
     * @param downloadsDir The directory we're ultimately downloading to.
     * @param streamable Whether or not this download can be streamed.
     */
    public MultiSourceDownloader(final File incompleteFile, final URI uri,
        final long size, final UriResolver uriResolver,
        final int connectionsPerHost, final URI expectedSha1,
        final File downloadsDir, final boolean streamable) {
        this.m_streamable = streamable;
        m_incompleteFile = incompleteFile;
        m_finalName = incompleteFile.getName();
        m_uri = uri;
        m_size = size;
        m_uriResolver = uriResolver;
        m_connectionsPerHttpServer = connectionsPerHost;
        final HttpMethodRetryHandler retryHandler = 
            new DefaultHttpMethodRetryHandler(0, false);
        this.m_httpClient.getParams().setParameter(
                HttpMethodParams.RETRY_HANDLER, retryHandler);

        final HttpConnectionManagerParams params = 
            this.m_httpClient.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(50 * 1000);
        params.setSoTimeout(30 * 1000);

        // We set this for now because our funky sockets sometimes can't
        // handle the stale checking details.
        // TODO: We should fix our sockets to properly handle it. See
        // the call sequence in HttpConnection.java isStale() from
        // HTTP client.
        params.setBooleanParameter(
                HttpConnectionManagerParams.STALE_CONNECTION_CHECK, false);
        params.setBooleanParameter(HttpMethodParams.WARN_EXTRA_INPUT, true);

        try {
            m_randomAccessFile = new RandomAccessFile(incompleteFile, "rw");
        } catch (final FileNotFoundException e) {
            m_log.error("Could not create file: " + incompleteFile, e);
            throw new IllegalArgumentException("Cannot create file: "
                    + incompleteFile);
        }

        m_completeFile = new File(downloadsDir, m_finalName);

        m_log.debug("Resolving download sources...");
        setState(MsDState.GETTING_SOURCES);

        try {
            this.m_sources = m_uriResolver.resolve(m_uri);
            if (this.m_sources.isEmpty()) {
                setState(MsDState.NO_SOURCES_AVAILABLE);
            }
        } catch (final IOException e) {
            m_log.warn("Could not access sources for download", e);
            this.m_sources = Collections.emptyList();
            setState(MsDState.COULD_NOT_DETERMINE_SOURCES);
        }
        if (this.m_sources.isEmpty()) {
            m_log.warn("No sources available for uri: " + m_uri);
            m_rangeTracker = new RangeTrackerAdapter();
            m_launchFileTracker = new LaunchFileTrackerAdapter();
            return;
        } else {
            final URI expectedSha1ToUse;
            if (expectedSha1 == null) {
                expectedSha1ToUse = expectedSha1;
            } else {
                expectedSha1ToUse = this.m_uriResolver.getSha1();
            }
            m_rangeTracker = new RangeTrackerImpl(size, this.m_sources.size());
            final int numChunks = m_rangeTracker.getNumChunks();
            m_launchFileTracker = new LaunchFileDispatcher(incompleteFile,
                    m_randomAccessFile, numChunks, expectedSha1ToUse);
        }
    }

    public void start() {
        if (this.m_started) {
            m_log.warn("Already started...");
            return;
        }
        m_started = true;

        try {
            download(m_sources);
        } catch (final Throwable t) {
            m_log.warn("Unexpected throwable during download", t);
            setState(MsDState.FAILED);
            return;
        } finally {
            // Make sure we close the file.
            try {
                m_randomAccessFile.close();
            } catch (final IOException e) {
                m_log.warn("Could not close file: " + m_randomAccessFile, e);
            }
        }
    }

    private void connect(final Collection<URI> sources,
            final SourceRanker downloadSpeedRanker, final int connectionsPerHost) {
        m_log.debug("Attempting to connection to " + connectionsPerHost
                + " hosts..");

        // Keep a counter of the number of hosts we've issued head requests to.
        // We don't want to send out 1000 head requests, for example, so we
        // cap it.
        int numHosts = 0;

        final Preferences prefs = Preferences.userRoot();
        final long id = prefs.getLong("LITTLESHOOT_ID", -1);

        final Iterator<URI> sourcesIter = sources.iterator();

        while (sourcesIter.hasNext() && (numHosts < 100)) {
            final URI uri = sourcesIter.next();
            m_log.info("Creating downloader for: ", uri);
            if (id == Long.parseLong(uri.getHost())) {
                m_log.info("Ignoring request to download from ourselves");
                continue;
            }

            // We only use multiple connections to a single host if it's a
            // straight HTTP server on the public Internet.
            final int connectionsPerHostToCreate = uri.getScheme().equals(
                    "http") ? connectionsPerHost : 1;

            for (int i = 0; i < connectionsPerHostToCreate; i++) {
                m_log.debug("Creating connection...");

                final RangeDownloader dl = new SingleSourceDownloader(
                        m_httpClient, uri, m_singleDownloadListener,
                        downloadSpeedRanker, m_rangeTracker,
                        m_launchFileTracker, m_randomAccessFile);

                dl.issueHeadRequest();
            }

            ++numHosts;
        }
    }
    
    private void download(final Collection<URI> sources) {
        if (sources.isEmpty()) {
            setState(MsDState.NO_SOURCES_AVAILABLE);
        } else {
            setState(new MsDState.LittleShootDownloadingState(m_rateCalculator,
                    getNumUniqueHosts(), getSize()));

            connect(sources, this.m_downloadingRanker,
                    m_connectionsPerHttpServer);

            boolean done = false;

            while (m_rangeTracker.hasMoreRanges() && !m_stopped && !m_failed
                    && !done) {
                m_log.debug("Accessing next source...");

                final RangeDownloader dl = m_downloadingRanker.getBestSource();

                m_log.debug("Accessed source...downloading...");

                if (m_stopped) {
                    done = true;
                } else if (this.m_failed) {
                    done = true;
                } else if (singleRangeDownload(dl)) {
                    // This means we're done, so break out of the loop.
                    onDownloadComplete();
                    done = true;
                }
            }
            if (m_failed) {
                setState(MsDState.FAILED);
                m_log.debug("The download failed");
            }

            else if (m_stopped) {
                setState(MsDState.CANCELED);
                m_log.debug("The download was cancelled");
            }
        }
    }

    private int getNumUniqueHosts() {
        m_log.debug("Getting numSources: " + m_uniqueSourceUris.size());
        return m_uniqueSourceUris.size();
    }

    private void onDownloadComplete() {
        m_log.debug("Downloaded whole file...");

        // First notify the launcher because it needs access to the open
        // random access file.
        m_launchFileTracker.onFileComplete();

        // Since the download completion and the file launching happen on
        // different threads, we need to wait until all the launchers have
        // finished here before notifying listeners that have free reign over
        // the downloaded file (like moving it!).
        // We need to make this call after the file complete notification
        // above because that's the only way the launcher ever completes it's
        // write (the launcher waits for the complete notification).
        m_launchFileTracker.waitForLaunchersToComplete();

        this.m_connectionManager.shutdown();

        try {
            m_randomAccessFile.close();
        } catch (final IOException e) {
            m_log.warn("Could not close file: " + m_randomAccessFile, e);
        }

        setState(MsDState.COMPLETE);
    }

    private void setState(final MsDState state) {
        if (m_state.equals(state)) {
            // Do nothing. The state has not changed.
        } else {
            m_state = state;
            m_log.debug("Setting state to: " + state);
            fireStateChanged(state);
        }
    }

    private boolean singleRangeDownload(final RangeDownloader downloader) {
        final Optional<LongRange> oRange = m_rangeTracker.getNextRange();

        final OptionalVisitor<Boolean, LongRange> visitor = 
            new OptionalVisitor<Boolean, LongRange>() {
            public Boolean visitNone(final None<LongRange> none) {
                return Boolean.TRUE;
            }

            public Boolean visitSome(final Some<LongRange> some) {
                final LongRange range = some.object();
                m_log.debug("Downloading from downloader: {}", downloader);
                downloader.download(range);
                return Boolean.FALSE;
            }
        };

        return oRange.accept(visitor).booleanValue();
    }

    public File getIncompleteFile() {
        return m_incompleteFile;
    }

    public long getSize() {
        return m_size;
    }

    public MsDState getState() {
        return m_state;
    }

    public boolean isStarted() {
        return this.m_started;
    }

    public void write(final OutputStream os, final boolean cancelOnStreamClose) {
        try {
            m_launchFileTracker.write(os, cancelOnStreamClose);
        } catch (final IOException e) {
            // This will typically be an exception from the servlet
            // container indicating the user has closed the browser window,
            // such as a Jetty EofException. Other cases can also
            // cause this however. A great example is Safari QuickTime
            // files where Safari hands off downloading to QuickTime when
            // it gets a Content Type header for a file type QuickTime
            // handles. QuickTime will then send another HTTP request,
            // and Safari will close the initial connection.
            //
            // The key here is we should only cancel the download when
            // this is the last active writer *and* the caller has specified
            // we should cancel the entire download when we've lost the
            // stream. This is important to keep in mind -- an IO error
            // on the stream to the browser or whatever is very different
            // from a concrete indication we should cancel the download!!
            if (m_launchFileTracker.getActiveWriteCalls() == 0
                    && cancelOnStreamClose) {
                m_log.debug("Canceling stream...");
                stop(false);
            }
        } catch (final Throwable t) {
            m_log.error("Throwable writing file.", t);
        }
    }

    /**
     * Returns whether a given state indicates that we are downloading.
     * 
     * @param state
     *            The state.
     * @return True if the state indicates that we are downloading, false
     *         otherwise.
     */
    private static boolean isDownloading(final MsDState state) {
        final MsDState.Visitor<Boolean> visitor = new MsDState.VisitorAdapter<Boolean>(
                Boolean.FALSE) {
            @Override
            public Boolean visitLittleShootDownloading(
                    final MsDState.LittleShootDownloading downloadingState) {
                return Boolean.TRUE;
            }
        };

        return state.accept(visitor).booleanValue();
    }

    private void fail() {
        m_failed = true;
        m_downloadingRanker.onFailed();
        setState(MsDState.FAILED);
        m_launchFileTracker.onFailure();
    }
    
    public void stop(final boolean removeFiles) {
        m_stopped = true;
        setState(MsDState.CANCELED);

        // Note we don't manually clean up the single source downloaders here
        // because they just complete their current operation and stop. They
        // don't really hold on to resources.
        try {
            this.m_randomAccessFile.close();
        } catch (final IOException e) {
            m_log.debug("Error closing file.  Already closed?", e);
        }

        if (removeFiles) {
            m_log.debug("Deleting files");
            this.m_incompleteFile.delete();
            this.m_completeFile.delete();
        }
    }

    public void pause() {
        m_log.error("LittleShoot downloads don't yet support pause.");
    }

    public void resume() {
        m_log.error("LittleShoot downloads don't yet support resume.");
    }

    public String getFinalName() {
        return m_finalName;
    }

    public File getCompleteFile() {
        return this.m_completeFile;
    }
    
    
    /**
     * The listener we use for notification of events on single source
     * downloaders.  We use one of these for all single downloads (as opposed to
     * one each).
     */
    private final class SingleDownloadListener implements RangeDownloadListener {

        public void onConnect(final RangeDownloader downloader) {
            m_log.debug("Connected to: " + downloader);

            if (m_numConnections > CONNECTION_LIMIT) {
                m_log.debug("We already have " + m_numConnections
                        + " connections.  Ignoring new host...");
            } else if (m_numConnections >= m_rangeTracker.getNumChunks()) {
                m_log.debug("We already have a downloader for every chunk!!");
            } else {
                m_uniqueSourceUris.add(downloader.getSourceUri());
                m_numConnections++;

                if (singleRangeDownload(downloader)) {
                    m_log.debug("Completed download on connect...");
                }
            }
        }

        public void onBytesRead(final RangeDownloader downloader) {
            m_rateCalculator.addData(downloader);
        }

        public void onDownloadFinished(final RangeDownloader downloader) {
            if (isDownloading(m_state)) {
                setState(new MsDState.LittleShootDownloadingState(
                        m_rateCalculator, getNumUniqueHosts(), getSize()));
            } else {
                // We are not in the downloading state. This is a stray
                // notification. Do nothing.
            }
        }

        public void onDownloadStarted(final RangeDownloader downloader) {
            m_activeRangeDownloaders.add(downloader);
        }

        public void onFail(final RangeDownloader downloader) {
            m_log.debug("Received a range failure.");
            m_uniqueFailedSourceUris.add(downloader.getSourceUri());
            final int remainingSources = m_sources.size()
                    - m_uniqueFailedSourceUris.size();
            if (remainingSources == 0) {
                MultiSourceDownloader.this.fail();
            } else {
                m_log.debug("Continuing download.  Sources remaining: {}",
                        m_sources.size());
            }
        }
    }

    public <T> T accept(final DownloadVisitor<T> visitor) {
        return visitor.visitLittleShootDownloader(this);
    }

    public boolean isStreamable() {
        return m_streamable;
    }

    public long getBytesRead() {
        return this.m_rateCalculator.getBytesRead();
    }

}
