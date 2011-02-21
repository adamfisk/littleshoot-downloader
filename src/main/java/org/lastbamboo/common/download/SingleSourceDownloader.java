package org.lastbamboo.common.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.http.client.CommonsHttpClient;
import org.lastbamboo.common.http.client.HttpClientRunner;
import org.lastbamboo.common.http.client.HttpListener;
import org.lastbamboo.common.http.client.NoContentRangeException;
import org.lastbamboo.common.http.client.RuntimeHttpException;
import org.littleshoot.util.InputStreamHandler;
import org.littleshoot.util.IoUtils;
import org.littleshoot.util.NoneImpl;
import org.littleshoot.util.Optional;
import org.littleshoot.util.RuntimeIoException;
import org.littleshoot.util.SomeImpl;
import org.littleshoot.util.WriteListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Downloads data from a single source.
 */
public class SingleSourceDownloader implements RangeDownloader, 
    InputStreamHandler, HttpListener
    {

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    
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
     * Records the number of times this source has failed.  Sources can
     * occasionally make recoverable failures, so we keep trying.
     */
    private volatile int m_numFailures = 0;

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
        if (!this.m_uri.toString().startsWith("http://"))
            {
            final HttpMethodRetryHandler retryHandler = 
                new DefaultHttpMethodRetryHandler(0, false);
            this.m_httpClient.getParams().setParameter(
                HttpMethodParams.RETRY_HANDLER, retryHandler);
            }
        this.m_numBytesDownloaded = 0L;
        }
    
    public void download(final LongRange range)
        {
        this.m_completedTime = -1;
        this.m_startedTime = -1;
        this.m_contentLength = -1;
        this.m_assignedRange = range;
        m_log.debug("Downloading from: "+this.m_uri);
        final GetMethod method = new GetMethod(this.m_uri.toString());
        method.getParams().setBooleanParameter(
            HttpMethodParams.WARN_EXTRA_INPUT, true);
        
        // See RFC 2616 section 14.35.1 - "Byte Ranges"
        final String rangesSpecifier = 
            "bytes="+range.getMinimumLong()+"-"+range.getMaximumLong();
        method.addRequestHeader("Range", rangesSpecifier);
        
        m_log.debug("HTTP connection manager: " +
            m_httpClient.getHttpConnectionManager().getClass());
        
        final Runnable runner = 
            new HttpClientRunner(this, this.m_httpClient, method, this);

        // Tempting to get rid of this extra thread here.
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
                    // We skip the HEAD request for now.
                    //m_rangeDownloadListener.onConnect(
                      //  SingleSourceDownloader.this);
                    sendHeadRequest();
                    }
                catch (final Throwable t)
                    {
                    m_log.error("Unexpected throwable.", t);
                    m_rangeDownloadListener.onFail(SingleSourceDownloader.this);
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
        // Note for HTTP requests we can often see duplicate
        // requests/downloaders for the same URI.  That's because we maintain
        // 2 connection to the same host to speed things up.
        final String uri = this.m_uri.toString();
        m_log.debug("Sending request to URI: {}", uri);
        final HeadMethod method = new HeadMethod(uri);
        try
            {
            this.m_httpClient.executeMethod(method);
            m_log.debug("Finished executing method for HEAD request...");
            final int statusCode = method.getStatusCode ();
            if (statusCode == HttpStatus.SC_OK)
                {
                method.releaseConnection();
                this.m_rangeDownloadListener.onConnect(this);
                }
            else if (statusCode == HttpStatus.SC_PARTIAL_CONTENT)
                {
                method.releaseConnection();
                final Header range = method.getResponseHeader ("Content-Range");
                if (range != null)
                    {
                    this.m_rangeDownloadListener.onConnect(this);
                    }
                else
                    {
                    this.m_rangeDownloadListener.onFail(this);
                    }
                }
            else
                {
                m_log.debug("Status code: " + statusCode);
                }
            }
        catch (final RuntimeHttpException e)
            {
            // We just won't end up using this source.
            m_log.debug("HTTP exception contacting source", e);
            this.m_rangeDownloadListener.onFail(this);
            }
        catch (final RuntimeIoException e)
            {
            // We just won't end up using this source.
            m_log.debug("IO error contacting source", e);
            this.m_rangeDownloadListener.onFail(this);
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
            m_log.debug ("Trying to get kbs without enough" +
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
                m_log.warn ("Completed time same as connected time: " +
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
        m_log.debug("Handling input stream -- copying to file and stream.");
        
        // We reset the bytes downloaded with each new HTTP method body read.
        m_numBytesDownloaded = 0;
        copy(is);
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
            m_log.error("No Content-Range header from: {} ...expecting: " + 
                this.m_assignedRange, this.m_uri);
            
            // We don't notify of failure because the exception eventually
            // triggers the failure notification.
            throw new NoContentRangeException("No content range");
            }
        final long min = m_contentRange.getMinimumLong();
        final long max = m_contentRange.getMaximumLong();
        
        // The number of bytes we expect to read from the stream.  This is
        // simply the number of bytes in the range for which we are responsible.
        final int expectedBytes = (int) ((max - min) + 1);

        // We create an anonymous class here because there's a method naming
        // class with HttpClientListener.
        final WriteListener writeListener = new WriteListener()
            {
            public void onBytesRead(final int bytesRead)
                {
                //m_log.debug("Adding bytes read...");
                m_numBytesDownloaded +=bytesRead;
                m_rangeDownloadListener.onBytesRead(SingleSourceDownloader.this);
                }
            
            };
        // The copy method handles synchronizing the RAF.
        IoUtils.copy(is, this.m_randomAccessFile, min, expectedBytes, 
            this.m_launchFileTracker, writeListener);
        } 

    public void onContentLength(final long contentLength)
        {
        m_log.debug("Received content length: "+contentLength);
        this.m_contentLength = contentLength;
        }

    public void onCouldNotConnect()
        {
        m_log.debug("Received could not connect...");
        onFailure();
        }
    
    public void onConnect(final long ms)
        {
        // Ignored.
        }

    public void onFailure()
        {
        m_log.debug("Received download failure number: "+this.m_numFailures+
            " for: "+this);
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        this.m_numFailures++;
        if (this.m_numFailures < 4)
            {
            this.m_sourceRanker.onAvailable(this);
            }
        }
    
    public void onPermanentFailure()
        {
        this.m_rangeTracker.onRangeFailed(this.m_assignedRange);
        }
    
    public void onHttpException(final HttpException httpException)
        {
        m_log.debug("Received HTTP exception");
        onFailure();
        }

    public void onNoTwoHundredOk(final int responseCode)
        {
        m_log.debug("No 200-level response...re-submitting range...");
        onFailure();
        }
    
    public void onMessageBodyRead()
        {
        m_log.debug("Read message body!!");
        this.m_completedTime = System.currentTimeMillis();
        m_numBytesDownloaded = m_contentLength;
        m_log.info ("Completed time recorded as: " + m_completedTime);
        
        // This is notifying the class that just keeps track of ranges we 
        // need. This is NOT the class that streams the file to the browser.
        this.m_rangeTracker.onRangeComplete(this.m_assignedRange);
        
        this.m_completedRanges++;
        if (this.m_numFailures > 0)
            {
            this.m_numFailures--;
            }
        
        this.m_rangeDownloadListener.onDownloadFinished (this);
        
        // Everything's going well with this downloader, so add it to the 
        // available downloaders to keep going.
        this.m_sourceRanker.onAvailable(this);
        }

    public void onBadHeader(final String header)
        {
        m_log.warn("Could not understand header: {}", header);
        onFailure();
        }

    public void onContentRange(final LongRange range) throws IOException
        {
        m_log.debug("Received Content-Range: "+range);
        if (!range.equals (m_assignedRange))
            {
            final String msg = "Bad range -- expected: " +this.m_assignedRange+
                " but was: "+range; 
            m_log.error(msg);
            throw new IOException(msg);
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
        m_log.info ("Connected time recorded as: " + m_startedTime);
        
        m_rangeDownloadListener.onDownloadStarted(this);
        }
    
    public void onBytesRead(final int bytesRead)
        {
        // Ignored for now.
        }

    public long getRangeStartTime()
        {
        return this.m_startedTime;
        }
    
    public long getRangeIndex()
        {
        return this.m_assignedRange.getMinimumLong();
        }
    
    @Override
    public String toString()
        {
        return getClass().getSimpleName() + " with "+this.m_completedRanges + 
            " completed ranges for: "+this.m_uri+"-"+hashCode();
        }

    // We don't override hashCode and equals here because we can have multiple
    // downloaders for a single URI to optimize HTTP connections.  Since some
    // of our data structures are sets, overriding hashCode and equals would
    // cause problems with those "duplicate" downloaders.
    }
