package org.lastbamboo.download;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.util.Sha1Hasher;

/**
 * Multisource download test using multiple SourceForge mirror sites.
 */
public class SourceForgeTest extends TestCase
    {

    private static final Log LOG = LogFactory.getLog(SourceForgeTest.class);
    
    public void testDownload() throws Exception
        {
        for(int i = 0; i < 1; ++i)
            {
            oneDownload();
            }
        }

    /**
     * Tests a download from multiple sources, verifying we received the
     * expected file upon completion.
     *
     * @throws Exception If any unexpected error occurs.
     */
    private void oneDownload() throws Exception
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

        final String sha1String = "urn:sha1:WUOWD7AATBMW4K3EV3TFMJ6FO6SNLZIS";
        final URI expectedSha1 = new URI(sha1String);

        // This is just random -- we resolve it to the above URLs regardless.
        final URI uri = new URI("urn:sha1:473294720014432DJL");

        final long size = 3534076L;
        final String title = "Test-File";
        final File file = new File(title);
        if (file.exists())
            {
            assertTrue(file.delete());
            }
        assertFalse(file.isFile());
        file.deleteOnExit();
        final MultiSourceDownloader downloader =
            new MultiSourceDownloader("sessionId", file, uri, size, "video/mpeg");
        LOG.debug("About to download file...");
        downloader.download(uris);
        LOG.debug("Finished call to download...");
        //Thread.sleep(20*1000);

        assertTrue(file.isFile());

        final URI sha1 = Sha1Hasher.createSha1Urn(file);
        assertEquals("SHA-1s should be equal!!", expectedSha1, sha1);
        }
    }
