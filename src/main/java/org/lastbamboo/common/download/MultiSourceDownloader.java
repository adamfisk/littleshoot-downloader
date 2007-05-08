package org.lastbamboo.common.download;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Downloader that downloads different parts of files from different sources.
 */
public class MultiSourceDownloader implements Downloader, RangeDownloadListener
    {
    /**
     * The log for this class.
     */
    private static final Log LOG = 
            LogFactory.getLog(MultiSourceDownloader.class);

    /**
     * The default number of connections to use for connecting to an HTTP
     * server.
     */
    private static final int DEFAULT_CONNECTIONS_PER_HOST = 4;

    /**
     * Limit on the number of connections to maintain.  We could set this 
     * higher in the future and more aggressively purge slow sources.
     */
    private static final int CONNECTION_LIMIT = 20;

    private final Object DOWNLOAD_STREAM_LOCK = new Object();
    
    private final URI m_uri;
    
    private final RangeTracker m_rangeTracker;

    private final long m_size;

    private final RandomAccessFile m_randomAccessFile;
    
    private final LaunchFileTracker m_launchFileTracker;

    /**
     * Variable for the number of hosts we've connected to and are actively
     * downloading from.
     */
    private int m_numConnections = 0;
    
    /**
     * List of listeners to notify of download events.
     */
    private final Collection<DownloadListener> m_downloadListeners = 
        Collections.synchronizedList(new LinkedList<DownloadListener>());

    private File m_file;

    private final String m_sessionId;

    private boolean m_isDownloading = false;

    private URI m_sha1;

    private String m_status;

    private final Collection<RangeDownloader> m_activeRangeDownloaders = 
        Collections.synchronizedSet(new HashSet<RangeDownloader>());

    private final String m_contentType;

    private boolean m_receivedExpectedSha1;

    private volatile int m_activeWriteCalls;

    private volatile boolean m_cancelled = false;

    /**
     * Creates a new multi-source downloader.
     * 
     * @param sessionId The ID of the browser session this downloader is for.
     * @param file The file path to download to.
     * @param uri The URI to download.
     * @param size The size of the file in bytes.
     * @param mimeType The MIME type of the file.
     * @throws NullPointerException If any args are empty or <code>null</code>.
     */
    public MultiSourceDownloader(final String sessionId, final File file, 
        final URI uri, final long size, final String mimeType)
        {
        if (StringUtils.isBlank(sessionId))
            {
            throw new NullPointerException("Null session ID");
            }
        if (file == null)
            {
            throw new NullPointerException("Null file");
            }
        if (uri == null)
            {
            throw new NullPointerException("Null URI");
            }
        if (StringUtils.isBlank(mimeType))
            {
            throw new NullPointerException("Null MIME type");
            }
        this.m_sessionId = sessionId;
        this.m_file = file;
        try
            {
            this.m_randomAccessFile = new RandomAccessFile(file, "rw");
            }
        catch (final FileNotFoundException e)
            {
            LOG.error("Could not create file: "+file, e);
            throw new IllegalArgumentException("Cannot create file: "+file);
            }
        this.m_uri = uri;
        this.m_size = size;
        this.m_contentType = mimeType;
        
        this.m_rangeTracker = new RangeTrackerImpl(file.getName(), size);
        final int numChunks = this.m_rangeTracker.getNumChunks();
        this.m_launchFileTracker = 
            new LaunchFileDispatcher(file, this.m_randomAccessFile, numChunks);
        }

    public void download(final Collection<URI> sources) throws IOException
        {
        download(sources, DEFAULT_CONNECTIONS_PER_HOST);
        }
    
    public void download(final Collection<URI> sources, 
        final int connectionsPerHost) throws IOException
        {
        LOG.debug("Starting download from "+sources.size()+
            " sources...");
        this.m_isDownloading = true;
        if (sources.isEmpty())
            {
            LOG.warn("No sources available!");
            throw new IOException("No sources found!!");
            }
        
        final Comparator<RangeDownloader> speedComparator = 
            new DownloadSpeedComparator();
        final SourceRanker downloadingRanker = 
            new SourceRankerImpl(speedComparator);
        connect(sources, downloadingRanker, connectionsPerHost);
        
        while(this.m_rangeTracker.hasMoreRanges())
            {
            LOG.debug("Accessing next source...");
            final RangeDownloader dl = downloadingRanker.getBestSource();
            LOG.debug("Accessed source...downloading...");
            if (this.m_cancelled)
                {
                LOG.debug("The download was cancelled, not downloading " +
                    "any more ranges.");
                return;
                }
            else if (singleRangeDownload(dl))
                {
                // This means we're done, so break out of the loop.
                handleDownloadComplete();
                return;
                }
            }
        }
    
    private void handleDownloadComplete()
        {
        LOG.debug("Downloaded whole file...");
        
        // First notify the launcher because it needs access to the open
        // random access file.
        this.m_launchFileTracker.onFileComplete();
        
        // Since the download completion and the file launching happen on
        // different threads, we need to wait until all the launchers have
        // finished here before notifying listeners that have free reign over
        // the downloaded file (like moving it!).
        // We need to make this call after the file complete notification
        // above because that's the only way the luancher ever completes it's
        // write (the launcher waits for the complete notification).
        waitForLaunchersToComplete();
        try
            {
            this.m_randomAccessFile.close();
            }
        catch (final IOException e)
            {
            LOG.warn("Could not close file: "+this.m_randomAccessFile, e);
            }
        
        onDownloadComplete();
        }

    /**
     * Waits until all active launcher have finished their writes, typically
     * to the browser.
     */
    private void waitForLaunchersToComplete()
        {
        if (this.m_activeWriteCalls == 0)
            {
            LOG.debug("No active launchers...");
            return;
            }
        synchronized (this.DOWNLOAD_STREAM_LOCK)
            {
            LOG.debug("Waiting for active streams to finish streaming");
            try
                {
                // It shouldn't take forever to stream the already
                // downloaded file to the browser, so cap the wait.
                this.DOWNLOAD_STREAM_LOCK.wait(1*60*1000);
                if (this.m_activeWriteCalls > 0)
                    {
                    LOG.warn("Still " + this.m_activeWriteCalls + 
                        " active writes!!");
                    }
                }
            catch (final InterruptedException e)
                {
                LOG.warn("Unexpected interrupt!!", e);
                }
            }
        }

    public void cancel()
        {
        this.m_cancelled = true;
        setStatus("Canceled");
        synchronized (this.m_downloadListeners)
            {
            for (final DownloadListener dl : this.m_downloadListeners)
                {
                dl.onDownloadCancelled(this);
                }
            }
        }
    
    private void onDownloadComplete()
        {
        setStatus("Complete");
        synchronized (this.m_downloadListeners)
            {
            for (final DownloadListener dl : this.m_downloadListeners)
                {
                dl.onDownloadComplete(this);
                }
            }
        }

    public void onConnect(final RangeDownloader downloader)
        {
        LOG.debug("Connected to: "+downloader);
        
        if (this.m_numConnections > CONNECTION_LIMIT)
            {
            LOG.debug("We already have " + this.m_numConnections +
                " connections.  Ignoring new host...");
            return;
            }
        else if (
            this.m_numConnections >= 
            this.m_rangeTracker.getNumChunks())
            {
            LOG.debug("We already have a downloader for every chunk!!");
            return;
            }

        this.m_numConnections++;
        setStatus("Connected to " + this.m_numConnections + 
            " hosts...");
        
        if (singleRangeDownload(downloader))
            {
            LOG.debug("Completed download on connect...");
            }
        }

    private boolean singleRangeDownload(final RangeDownloader downloader)
        {
        // It's possible another thread has already resulted in the download
        // completing.
        if (!this.m_rangeTracker.hasMoreRanges())
            {
            return true;
            }
        final LongRange range = this.m_rangeTracker.getNextRange();
        LOG.debug("Accessed range: "+range);
        
        // The call to get the next range would block if there were no more
        // ranges, we use this special, dummy range to signify that we're
        // done with the download.
        if (range.getMinimumLong() == 183L &&
            range.getMaximumLong() == 183L)
            {
            LOG.debug("Received file complete signifier!!");
            return true;
            }
        
        LOG.debug("Downloading from downloader: "+downloader);
        downloader.download(range);
        return false;
        }

    private void connect(final Collection<URI> sources, 
        final SourceRanker downloadSpeedRanker, final int connectionsPerHost)
        {
        LOG.debug("Attempting to connection to "+connectionsPerHost+" hosts..");
        
        
        // Keep a counter of the number of hosts we've issued head requests to.
        // We don't want to send out 1000 head requests, for example, so we
        // cap it.
        int numHosts = 0;
        for (final URI uri : sources)
            {
            if (numHosts > 100)
                {
                LOG.debug("Already sent HEAD request to 100 hosts, returning");
                return;
                }
            final int connectionsPerHostToCreate;
            
            // We only use multiple connections to a single host if it's a 
            // straight HTTP server on the public Internet.
            if (uri.getScheme().equals("http"))
                {
                connectionsPerHostToCreate = connectionsPerHost;
                }
            else
                {
                connectionsPerHostToCreate = 1;
                }
            for (int i = 0; i < connectionsPerHostToCreate; i++)
                {
                LOG.debug("Creating connection...");
                final RangeDownloader dl = 
                    new SingleSourceDownloader(uri, this, 
                        downloadSpeedRanker, this.m_rangeTracker, 
                        this.m_launchFileTracker, this.m_randomAccessFile);
                dl.issueHeadRequest();
                }
            numHosts++;
            }
        }

    public void setStatus(final String status)
        {
        this.m_status = status;
        }

    public void writeFile(final HttpServletResponse response) 
        {
        LOG.debug("Writing response headers...");
        this.m_activeWriteCalls++;
        response.setStatus(HttpStatus.SC_OK);
        
        response.setContentType(this.m_contentType);
        response.setContentLength((int) this.m_size);
        response.setDateHeader("Last-Modified", new Date().getTime());
        try
            {
            this.m_launchFileTracker.write(response.getOutputStream());
            LOG.debug("Finished launcher write call...");
            this.m_activeWriteCalls--;
            synchronized (this.DOWNLOAD_STREAM_LOCK)
                {
                if (this.m_activeWriteCalls == 0)
                    {
                    this.DOWNLOAD_STREAM_LOCK.notify();
                    }
                }
            }
        catch (final IOException e)
            {
            // This indicates the user has closed the browser window.  We
            // only cancel the download in this case if there are no other
            // active readers of the download.
            LOG.debug("Could not complete write -- user closed browser?", e);
            this.m_activeWriteCalls--;
            if (this.m_activeWriteCalls == 0)
                {
                LOG.debug("Cancelling download...");
                cancel();
                }
            }
        }

    public void addListener(final DownloadListener dl)
        {
        this.m_downloadListeners.add(dl);
        }

    public File getFile()
        {
        return this.m_file;
        }

    public String getSessionId()
        {
        return m_sessionId;
        }

    public URI getUri()
        {
        return m_uri;
        }
    
    public boolean isDownloading()
        {
        return this.m_isDownloading;
        }

    public void setDownloading(final boolean downloading)
        {
        this.m_isDownloading = downloading;
        }

    public URI getSha1Urn()
        {
        return this.m_sha1;
        }

    public void setSha1Urn(final URI sha1, final boolean matchesExpected)
        {
        this.m_sha1 = sha1;
        this.m_receivedExpectedSha1 = matchesExpected;
        }

    public String getStatus()
        {
        return this.m_status;
        }
    
    /**
     * {@inheritDoc}
     */
    public List<SourceStatus> getSourceStatuses
            ()
        {
        return new LinkedList<SourceStatus> ();
        }

    public void onDownloadStarted(final RangeDownloader downloader)
        {
        this.m_activeRangeDownloaders.add(downloader);
        setStatus("Downloading from "+this.m_activeRangeDownloaders.size()+
            " sources...");
        }

    public void setFile(final File file)
        {
        this.m_file = file;
        this.m_launchFileTracker.setFile(file);
        }

    public String getMimeType()
        {
        return m_contentType;
        }

    public boolean downloadedExpectedSha1()
        {
        return this.m_receivedExpectedSha1;
        }
    }
