package org.lastbamboo.common.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.LongRange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.common.http.client.CommonsHttpClient;
import org.lastbamboo.common.http.client.CommonsHttpClientImpl;
import org.lastbamboo.common.http.client.HttpClientRunner;
import org.lastbamboo.common.http.client.HttpListener;
import org.lastbamboo.util.InputStreamHandler;
import org.lastbamboo.util.RuntimeHttpException;
import org.lastbamboo.util.RuntimeIoException;

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

    private long m_connectedTime = -1L;

    private long m_contentLength = -1L;

    private long m_completedTime = -1L;

    /**
     * The number of ranges this downloader has completed.
     */
    private int m_completedRanges = 0;

    private final LaunchFileTracker m_launchFileTracker;

    private String m_contentType;

    /**
     * Our HTTP client used to execute our HTTP methods.
     */
    private final CommonsHttpClient m_httpClient;
    
    /**
     * The default buffer size to use.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    
    private static HttpConnectionManager getDefaultConnectionManager
            ()
        {
        return new MultiThreadedHttpConnectionManager();
        }

    /**
     * Creates a downloader for downloading from a specific source.
     * 
     * @param source The URI for the source to download from.
     * @param rangeDownloadListener The listener for range download events.
     * @param downloadSpeedRanker The class for ranking sources.
     * @param rangeTracker The class for tracking needed ranges in the file.
     * @param launchTracker The tracker for bytes to send to the launch file.
     * @param randomAccessFile The class to store downloaded bytes to.
     */
    public SingleSourceDownloader
            (final CommonsHttpClient httpClient,
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
        }

    /**
     * Creates a downloader for downloading from a specific source.
     * 
     * @param source The URI for the source to download from.
     * @param rangeDownloadListener The listener for range download events.
     * @param downloadSpeedRanker The class for ranking sources.
     * @param rangeTracker The class for tracking needed ranges in the file.
     * @param launchTracker The tracker for bytes to send to the launch file.
     * @param randomAccessFile The class to store downloaded bytes to.
     */
    public SingleSourceDownloader
            (final URI source, 
             final RangeDownloadListener rangeDownloadListener,
             final SourceRanker downloadSpeedRanker, 
             final RangeTracker rangeTracker, 
             final LaunchFileTracker launchTracker,
             final RandomAccessFile randomAccessFile)
        {
        this(new CommonsHttpClientImpl(getDefaultConnectionManager()),
             source,
             rangeDownloadListener,
             downloadSpeedRanker,
             rangeTracker,
             launchTracker,
             randomAccessFile);
        }
    
    public void download(final LongRange range)
        {
        this.m_completedTime = -1;
        this.m_connectedTime = -1;
        this.m_contentLength = -1;
        this.m_assignedRange = range;
        final GetMethod method = new GetMethod(this.m_uri.toString());
        
        // Don't attempt to connect 3 times unless it's a public web server.
        if (!this.m_uri.toString().startsWith("http://"))
            {
            // Override the default of attempting to connect 3 times.
            final HttpMethodRetryHandler retryHandler = 
                new DefaultHttpMethodRetryHandler(0, false);
            this.m_httpClient.getParams().setParameter(
                HttpMethodParams.RETRY_HANDLER, retryHandler);
            this.m_httpClient.getHttpConnectionManager().getParams().
                setConnectionTimeout(10*1000);
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
                sendHeadRequest(); 
                }
            };
        final Thread headThread = new Thread(headRunner, 
            "HTTP-Head-Thread-"+headRunner.hashCode());
        headThread.setDaemon(true);
        headThread.start();
        }

    private void sendHeadRequest()
        {
        // Override the default of attempting to connect 3 times.
        /*
        final HttpMethodRetryHandler retryHandler = 
            new DefaultHttpMethodRetryHandler(0, false);
        this.m_httpClient.getParams().setParameter(
            HttpMethodParams.RETRY_HANDLER, retryHandler);
        this.m_httpClient.getHttpConnectionManager().getParams().
            setConnectionTimeout(10*1000);
            */
        
        final String uri = SingleSourceDownloader.this.m_uri.toString();
        LOG.debug("Sending request to URI: "+uri);
        final HeadMethod method = new HeadMethod(uri);
        
        try
            {
            this.m_httpClient.executeMethod(method);
            final int statusCode = method.getStatusCode ();
            if (statusCode == HttpStatus.SC_OK)
                {
                this.setContentType(method);
                this.m_rangeDownloadListener.onConnect(this);
                }
            else if (statusCode == HttpStatus.SC_PARTIAL_CONTENT)
                {
                this.setContentType(method);
                // Do something else here?
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
        }
    
    protected void setContentType(final HttpMethod method)
        {
        if (StringUtils.isBlank(this.m_contentType))
            {
            final Header contentTypeHeader = 
                method.getResponseHeader("Content-Type");
            this.m_contentType = contentTypeHeader.getValue();
            LOG.debug("Setting content type to: "+this.m_contentType);
            }
        }

    public int getKbs()
        {
        if (this.m_contentLength == -1 ||
            this.m_connectedTime == -1 ||
            this.m_completedTime == -1)
            {
            LOG.debug("Trying to connect the connected time without enough" +
                          " data.  Content Length: "+this.m_contentLength +
                          " Connect Time: "+this.m_connectedTime+" Completed " +
                          "Time: " + this.m_completedTime);
            return -1;
            }
        
        if (this.m_completedTime == this.m_connectedTime)
            {
            LOG.error("About to divide by zero -- times both " +
                          this.m_completedTime);
            }
        
        final long downloadMs = this.m_completedTime - this.m_connectedTime;
        //LOG.debug(downloadMs + " ms to download segment...");
        return (int)(this.m_contentLength*1000/downloadMs*1024);
        }

    /**
     * {@inheritDoc}
     */
    public void handleInputStream
            (final InputStream is) throws IOException
        {
        copy(is);
        }
    
    /**
     * Copies the data from a given input stream into the part of the file for
     * which we are responsible.  The input stream is queued up to the part of
     * the file for which we are responsible.
     * 
     * @param is
     *      The input stream from which to read data to put into the file.
     *      
     * @throws IOException
     *      If there are any I/O problems.
     */
    private void copy
            (final InputStream is) throws IOException
        {
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        
        final long min = m_contentRange.getMinimumLong();
        final long max = m_contentRange.getMaximumLong();
        
        // The number of bytes we expect to read from the stream.  This is
        // simply the number of bytes in the range for which we are responsible.
        final int expectedBytes = (int) ((max - min) + 1);
        
        // The position in the file to which we are writing.
        long filePosition = min;
        
        // The total number of bytes we have read (and written to the file).
        // We track this so that we may know if have read enough data to handle
        // our range.
        int totalBytesRead = 0;
        
        // The number of bytes we have left to read to satisfy our range.
        int remaining = expectedBytes - totalBytesRead;
        
        // We either try and read whatever we have left to satisfy our range, or
        // we read as much as can fit in our buffer.
        int bytesToRead = Math.min(remaining, buffer.length);
        
        // The number of bytes read in one pass of our read loop.
        int bytesRead = 0;
        
        while (-1 != (bytesRead = is.read(buffer, 0, bytesToRead))) 
            {
            synchronized (this.m_randomAccessFile)
                {
                this.m_randomAccessFile.seek(filePosition);
                this.m_randomAccessFile.write(buffer, 0, bytesRead);
                }
            
            filePosition += bytesRead;
            totalBytesRead += bytesRead;
            remaining -= bytesRead;
            bytesToRead = Math.min(remaining, buffer.length);
            }
        
        LOG.trace("Wrote " + totalBytesRead + " bytes to file.");
        
        if (totalBytesRead == expectedBytes)
            {
            // We read the amount we expected to read.  Everything is okay.
            }
        else
            {
            // We were not able to read enough data.  This is an error, since we
            // are unable to handle the range for which we are responsible.
            throw new IOException("Not enough data in response body");
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

    public void onConnect(final long ms)
        {
        this.m_connectedTime = System.currentTimeMillis();
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
        this.m_launchFileTracker.onRangeComplete(this.m_assignedRange);
        this.m_rangeTracker.onRangeComplete(this.m_assignedRange);
        
        this.m_completedRanges++;
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
        if (range.getMinimumLong() != this.m_assignedRange.getMinimumLong())
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
        m_rangeDownloadListener.onDownloadStarted(this);
        }
    
    public void onBytesRead(final int bytesRead)
        {
        // Ingored for now.
        }
    
    public String getContentType()
        {
        return this.m_contentType;
        }
    
    public String toString()
        {
        return "Downloader at "+getKbs()+" kbs with "+this.m_completedRanges + 
            " completed ranges for: "+this.m_uri;
        }
    }
