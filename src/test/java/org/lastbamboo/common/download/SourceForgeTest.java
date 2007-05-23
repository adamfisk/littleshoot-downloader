package org.lastbamboo.common.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.lastbamboo.common.util.Sha1Hasher;

/**
 * Multisource download test using multiple SourceForge mirror sites.
 */
public class SourceForgeTest extends TestCase
    {
//    /**
//     * The log for this class.
//     */
    // Currently unused.
    // private static final Log LOG = LogFactory.getLog(SourceForgeTest.class);
    
    private static Downloader<MsDState> getBaseDownloader
            (final File file) throws URISyntaxException
        {
        final String[] urls =
            {
            "http://brown.edu/sourceforge/emule/file-not-there.exe",
            "http://ufpr.dl.sourceforge.net/sourceforge/emule/file-not-there.exe",
            "http://nchc.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://umn.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://superb-east.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://jaist.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://superb-west.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://puzzle.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://easynews.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://ufpr.dl.sourceforge.net/sourceforge/emule/eMule0.47c-Installer.exe",
            "http://not-a-real-source.net/sourceforge/emule/eMule0.47c-Installer.exe"
            };
        final Collection<URI> uris = new LinkedList<URI>();
        for (int i = 0; i < urls.length; i++)
            {
            try
                {
                uris.add(new URI(urls[i]));
                }
            catch (final URISyntaxException e)
                {
                // Never!
                }
            }

        // This is just random -- we resolve it to the above URLs regardless.
        final URI uri = new URI("urn:sha1:473294720014432DJL");

        final long size = 3534076L;
        if (file.exists())
            {
            assertTrue(file.delete());
            }
        assertFalse(file.isFile());
        file.deleteOnExit();
        
        final UriResolver resolver = new UriResolver ()
            {
            public Collection<URI> resolve
                    (final URI uri) throws IOException
                {
                return uris;
                }
            };
        
        final Downloader<MsDState> downloader =
                new MultiSourceDownloader ("sessionId",
                                    file,
                                    uri,
                                    size,
                                    "video/mpeg",
                                    resolver,
                                    2);
        
        return downloader;
        }

    /**
     * Tests a download from multiple sources, verifying we received the
     * expected file upon completion.
     *
     * @throws Exception If any unexpected error occurs.
     */
    private void oneDownload() throws Exception
        {
        final String title = "Test-File";
        final File file = new File(title);
        
        final String sha1String = "urn:sha1:WUOWD7AATBMW4K3EV3TFMJ6FO6SNLZIS";
        final URI expectedSha1 = new URI(sha1String);
        
        final Downloader<MsDState> downloader = getBaseDownloader (file);
        
        downloader.start ();
        
        assertEquals(3534076L, file.length());
        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        assertEquals("SHA-1s should be equal!!", expectedSha1, sha1);
        
//        final MultiSourceDownloaderImpl downloader =
//            new MultiSourceDownloaderImpl("sessionId", file, uri, size, "video/mpeg");
//        LOG.debug("About to download file...");
//        downloader.download(uris);
//        LOG.debug("Finished call to download...");
//        //Thread.sleep(20*1000);
//
//        assertTrue(file.isFile());
//
//        final URI sha1 = Sha1Hasher.createSha1Urn(file);
//        assertEquals("SHA-1s should be equal!!", expectedSha1, sha1);
        }
    
//    public void testDownload() throws Exception
//        {
//        for(int i = 0; i < 1; ++i)
//            {
//            oneDownload();
//            }
//        }
    
    public void testSha1Verifier
            () throws URISyntaxException
        {
        final String title = "Test-File";
        final File file = new File (title);
        
        final String sha1String = "urn:sha1:WUOWD7AATBMW4K3EV3TFMJ6FO6SNLZIS";
        final URI expectedSha1 = new URI (sha1String);
        
        final Downloader<MsDState> baseDownloader = getBaseDownloader (file);

        final Downloader<Sha1DState<MsDState>> downloader =
                new Sha1Downloader<MsDState> (baseDownloader, expectedSha1);
        
        downloader.start ();
        
        assertEquals (downloader.getState (),
                      new Sha1DState.VerifiedSha1Impl<MsDState> ());
        }
    }
