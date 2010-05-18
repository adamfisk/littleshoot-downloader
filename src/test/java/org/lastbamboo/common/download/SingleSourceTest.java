package org.lastbamboo.common.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests downloading from a single source.
 */
public class SingleSourceTest extends TestCase
    {
    
    private static final Logger LOG = LoggerFactory.getLogger(SingleSourceTest.class);

    private static final boolean TEST_ACTIVE = false;
    
    /**
     * Tests the speed of downloading with a single source using multiple
     * connections, steadily increasing the number of connections to guage 
     * the benefit.
     * 
     * @throws Exception If any unexpected error occurs.
     */
    public void testNumConnections() throws Exception
        {
        // This test takes awhile, so we only activate it on special occasions.
        if(!TEST_ACTIVE)
            {
            return;
            }
        final String urlString = 
            "http://movies.crooksandliars.com/CNN-Borat.mov";
        final URI uri = new URI(urlString);
        final Collection<URI> uris = new LinkedList<URI>();
        uris.add(uri);
        
        final File testFile = new File("borat.mov");
        LOG.debug("Found file: "+testFile.exists()+" bytes: "+testFile.length());
        testFile.delete();
        assertFalse(testFile.isFile());
        
        final long straighBaseline = download(urlString);
        final int straighBaselineSecs = (int) (straighBaseline/1000);
        //final String dateString = format.format(new Date(duration));
        LOG.debug("DOWNLOADED STRAIGHT BASELINE IN "+straighBaselineSecs+" SECONDS");
        
        final UriResolver resolver = new UriResolver ()
            {
            public Collection<URI> resolve (final URI uri) throws IOException
                {
                return uris;
                }

            public URI getSha1()
                {
                // TODO Auto-generated method stub
                return null;
                }
            };
        
        long baseline = 0;
        
        final File tempDownloadDir = new File("tempDownloadDir");
        tempDownloadDir.mkdir();
        tempDownloadDir.deleteOnExit();
        for (int i = 1; i < 16; i++)
            {
//            final MultiSourceDownloader dl = new MultiSourceDownloaderImpl("sessionId", 
//                testFile, uri, 6509767L, "video/mpeg");
            
            final Downloader<MsDState> dl = 
                new MultiSourceDownloader (testFile, uri,
                    6509767L, resolver, i, null, tempDownloadDir, true);
                                        
            final long start = System.currentTimeMillis();
            dl.start ();
            final long end = System.currentTimeMillis();
            
            LOG.debug("Found file: "+testFile.exists()+" bytes: "+testFile.length());
            testFile.delete();
            assertFalse(testFile.isFile());
            
            final long duration = end-start;
            final int durationSecs = (int) (duration/1000);
            LOG.debug("DOWNLOADED IN "+durationSecs+" SECONDS");
            if (i == 1)
                {
                LOG.debug("Setting baseline to: "+durationSecs+" seconds...");
                baseline = duration;
                }
            else
                {
                if (baseline > duration)
                    {
                    final long msSaved = baseline-duration;
                    final int secondsSaved = (int) (msSaved/1000);
                    LOG.debug("Saved "+secondsSaved+" seconds...");
                    final float percentSaved = (float)msSaved/(float)baseline;
                    LOG.debug((percentSaved * 100) +"% faster at "+i+" connections...");
                    }
                else
                    {
                    final int secondsLost = (int) ((duration- baseline)/60);
                    LOG.debug("Lost "+secondsLost);
                    }
                }
            }
        }

    private long download(String urlString) throws IOException
        {
        final HttpClient client = new HttpClient();
        final GetMethod method = new GetMethod(urlString);
        
        final long start = System.currentTimeMillis();
        try
            {
            client.executeMethod(method);
            }
        finally
            {
            method.releaseConnection();
            }
        final long end = System.currentTimeMillis();
        return end-start;
        }
    }
