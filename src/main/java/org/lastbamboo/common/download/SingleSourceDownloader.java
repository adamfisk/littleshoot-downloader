package org.lastbamboo.common.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.common.http.client.CommonsHttpClient;
import org.lastbamboo.common.http.client.HttpClientRunner;
import org.lastbamboo.common.http.client.HttpListener;
import org.lastbamboo.common.http.client.RuntimeHttpException;
import org.lastbamboo.common.util.InputStreamHandler;
import org.lastbamboo.common.util.IoUtils;
import org.lastbamboo.common.util.NoneImpl;
import org.lastbamboo.common.util.Optional;
import org.lastbamboo.common.util.RuntimeIoException;
import org.lastbamboo.common.util.SomeImpl;

/**
 * Downloads data from a single source.
 */
public class SingleSourceDownloader implements RangeDownloader, 
    InputStreamHandler, HttpListener
    {

    private static final Log LOG = 
        LogFactory.getLog(SingleSourceDownloader.class);
    
    private final URI m_uri;
    private final SourceRanker m_sourceRanker;

    private final RandomAccessFile m_randomAccessFile;

    private LongRange m_contentRange;

    private LongRange m_assignedRange;

    private final RangeTracker m_rangeTracker;

    private final RangeDownloadListener m_rangeDownloadListener;

    private long m_startedTime = -1L;

    private long m_contentLength = -1L;

    private long m_completedTime = -1L;
    
    /**
     * The number of bytes that have been downloaded.
     */
    private long m_numBytesDownloaded;

    /**
     * The number of ranges this downloader has completed.
     */
    private int m_completedRanges = 0;

    private final LaunchFileTracker m_launchFileTracker;

    /**
     * Our HTTP client used to execute our HTTP methods.
     */
    private final CommonsHttpClient m_httpClient;

    /**
     * Creates a downloader for downloading from a specific source.
     * 
     * @param httpClient The HTTP client instance to use for performing 
     * downloads.
     * @param source The URI for the source to download from.
     * @param rangeDownloadListener The listener for range download events.
     * @param downloadSpeedRanker The class for ranking sources.
     * @param rangeTracker The class for tracking needed ranges in the file.
     * @param launchTracker The tracker for bytes to send to the launch file.
     * @param randomAccessFile The class to store downloaded bytes to.
     */
    public SingleSourceDownloader(final CommonsHttpClient httpClient,
        final URI source, 
        final RangeDownloadListener rangeDownloadListener,
        final SourceRanker downloadSpeedRanker, 
        final RangeTracker rangeTracker, 
        final LaunchFileTracker launchTracker,
        final RandomAccessFile randomAccessFile)
        {
        this.m_uri = source;
        this.m_rangeDownloadListener = rangeDownloadListener;
        this.m_sourceRanker = downloadSpeedRanker;
        this.m_rangeTracker = rangeTracker;
        this.m_launchFileTracker = launchTracker;
        this.m_randomAccessFile = randomAccessFile;
        this.m_httpClient = httpClient;
        this.m_numBytesDownloaded = 0L;
        }
    
    public void download(final LongRange range)
        {
        this.m_completedTime = -1;
        this.m_startedTime = -1;
        this.m_contentLength = -1;
        this.m_assignedRange = range;
        LOG.debug("Downloading from: "+this.m_uri);
        final GetMethod method = new GetMethod(this.m_uri.toString());
        method.getParams().setBooleanParameter(
            HttpMethodParams.WARN_EXTRA_INPUT, true);
        
        // Revert to default config if it's a public web server.
        if (this.m_uri.toString().startsWith("http://"))
            {
            // Override the default of attempting to connect 3 times.
            final HttpMethodRetryHandler retryHandler = 
                new DefaultHttpMethodRetryHandler();
            method.getParams().setParameter(
                HttpMethodParams.RETRY_HANDLER, retryHandler);
            }
        
        // See RFC 2616 section 14.35.1 - "Byte Ranges"
        final String rangesSpecifier = 
            "bytes="+range.getMinimumLong()+"-"+range.getMaximumLong();
        method.addRequestHeader("Range", rangesSpecifier);
        
        LOG.debug("HTTP connection manager: " +
                      m_httpClient.getHttpConnectionManager().getClass());
        
        final Runnable runner = 
            new HttpClientRunner(this, this.m_httpClient, method, this);
        final Thread httpThread = 
            new Thread(runner, "HTTP-Download-Thread-"+this.hashCode());
        httpThread.setDaemon(true);
        httpThread.start();
        }
    
    public void issueHeadRequest()
        {
        // The head request is threaded to allow us to send a lot of them
        // quickly and to use whoever responds the quickest.
        final Runnable headRunner = new Runnable()
            {
            public void run()
                {
                try
                    {
                    sendHeadRequest();
                    }
                catch (final Throwable t)
                    {
                    LOG.error("Unexpected throwable.", t);
                    }
                }
            };
        final Thread headThread = new Thread(headRunner, 
            "HTTP-Head-Thread-"+headRunner.hashCode());
        headThread.setDaemon(true);
        headThread.start();
        }

    private void sendHeadRequest()
        {
        final String uri = SingleSourceDownloader.this.m_uri.toString();
        LOG.debug("Sending request to URI: "+uri);
        final HeadMethod method = new HeadMethod(uri);
        
        try
            {
            this.m_httpClient.executeMethod(method);
            final int statusCode = method.getStatusCode ();
            if (statusCode == HttpStatus.SC_OK)
                {
                method.releaseConnection();
                this.m_rangeDownloadListener.onConnect(this);
                }
            else if (statusCode == HttpStatus.SC_PARTIAL_CONTENT)
                {
                // Do something else here?
                method.releaseConnection();
                this.m_rangeDownloadListener.onConnect(this);
                }
            else
                {
                LOG.debug("Status code: " + statusCode);
                }
            }
        catch (final RuntimeHttpException e)
            {
            // We just won't end up using this source.
            LOG.debug("HTTP exception contacting source", e);
            }
        catch (final RuntimeIoException e)
            {
            // We just won't end up using this source.
            LOG.debug("IO error contacting source", e);
            }
        finally
            {
            method.releaseConnection();
            }
        }

    public Optional<Integer> getKbs ()
        {
        if (this.m_contentLength == -1 ||
            this.m_startedTime == -1 ||
            this.m_completedTime == -1)
            {
            LOG.debug ("Trying to get kbs without enough" +
                          " data.  Content Length: "+this.m_contentLength +
                          " Connect Time: "+this.m_startedTime+" Completed " +
                          "Time: " + this.m_completedTime);
            
            return new NoneImpl<Integer> ();
            }
        else
            {
            if (m_completedTime == m_startedTime)
                {
                // We warn here, since it is odd that the connection and
                // completion happened at the same time.
                LOG.warn ("Completed time same as connected time: " +
                              m_completedTime);
                }
            
            // We adjust the completed time to make sure that we do not divide
            // by zero.  If the connected time is the same as the completed
            // time, it appears to us that the download took no time, which
            // means that we have infinite download speed.  We adjust the
            // completed time to be at least one millisecond greater than the
            // connected time to make sure we do not get this infinity.
            final long safeCompletedTime =
                    Math.max (m_completedTime, m_startedTime + 1);
            
            final long downloadMs = safeCompletedTime - m_startedTime;
            
            final int kbs = (int) (m_contentLength * 1000 / downloadMs * 1024);
            
            return new SomeImpl<Integer> (kbs);
            }
        }
    
    public long getNumBytesDownloaded()
        {
        return m_numBytesDownloaded;
        }
    
    public URI getSourceUri()
        {
        return m_uri;
        }

    public void handleInputStream(final InputStream is) throws IOException
        {
        copy(is);
        
        m_numBytesDownloaded = m_contentLength;
        }
    
    /**
     * Copies the data from a given input stream into the part of the file for
     * which we are responsible.  The input stream is queued up to the part of
     * the file for which we are responsible.
     * 
     * @param is The input stream from which to read data to put into the file.
     *      
     * @throws IOException If there are any I/O problems.
     */
    private void copy(final InputStream is) throws IOException 
        {
        // It's possible the server never provided a content range.
        if (this.m_contentRange == null)
            {
            LOG.error("No content range provided");
            this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
            return;
            }
        final long min = m_contentRange.getMinimumLong();
        final long max = m_contentRange.getMaximumLong();
        
        // The number of bytes we expect to read from the stream.  This is
        // simply the number of bytes in the range for which we are responsible.
        final int expectedBytes = (int) ((max - min) + 1);
        
        synchronized (this.m_randomAccessFile)
            {
            IoUtils.copy(is, this.m_randomAccessFile, min, expectedBytes);
            }
        } 

    public void onContentLength(final long contentLength)
        {
        LOG.debug("Received content length: "+contentLength);
        this.m_contentLength = contentLength;
        }

    public void onCouldNotConnect()
        {
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        }
    
    /**
     * {@inheritDoc}
     */
    public void onConnect(final long ms)
        {
        // Ignored.
        }

    public void onHttpException(final HttpException httpException)
        {
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        }

    public void onNoTwoHundredOk(final int responseCode)
        {
        LOG.debug("No 200-level response...re-submitting range...");
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        }
    
    public void onMessageBodyRead()
        {
        LOG.debug("Read message body!!");
        this.m_completedTime = System.currentTimeMillis();
        LOG.info ("Completed time recorded as: " + m_completedTime);
        this.m_launchFileTracker.onRangeComplete(this.m_assignedRange);
        this.m_rangeTracker.onRangeComplete(this.m_assignedRange);
        
        this.m_completedRanges++;
        
        this.m_rangeDownloadListener.onDownloadFinished (this);
        
        // Everything's going well with this downloader, so add it to the 
        // available downloaders to keep going.
        this.m_sourceRanker.onAvailable(this);
        }

    public void onBadHeader(final String header)
        {
        LOG.warn("Could not understand header: "+header);
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        }

    public void onContentRange(final LongRange range)
        {
        LOG.debug("Received Content-Range: "+range);
        if (!range.equals (m_assignedRange))
            {
            LOG.error("Bad range -- expected: " +this.m_assignedRange+
                " but was: "+range);
            this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
            }
        this.m_contentRange = range;
        }
    
    public void onStatusEvent(final String status)
        {
        // Ignored for now.
        }

    public void onDownloadStarted()
        {
        m_startedTime = System.currentTimeMillis ();
        LOG.info ("Connected time recorded as: " + m_startedTime);
        
        m_rangeDownloadListener.onDownloadStarted(this);
        }
    
    public void onBytesRead(final int bytesRead)
        {
        // Ingored for now.
        }
    
    @Override
    public String toString()
        {
        return "Downloader with "+this.m_completedRanges + 
            " completed ranges for: "+this.m_uri;
        }
    }
