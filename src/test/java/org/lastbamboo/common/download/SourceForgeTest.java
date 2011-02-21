package org.lastbamboo.common.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;
import org.littleshoot.util.Sha1Hasher;

/**
 * Multisource download test using multiple SourceForge mirror sites.
 */
public class SourceForgeTest
    {

    private static final Logger LOG = LoggerFactory.getLogger(SourceForgeTest.class);
    
    private static Downloader<MsDState> getBaseDownloader
            (final File file, final URI expectedSha1) throws URISyntaxException
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
            
        final File downloadDir = new File("tempDownloadDir");
        downloadDir.mkdir();
        downloadDir.deleteOnExit();
        final Downloader<MsDState> downloader =
            new MultiSourceDownloader (file, uri, size,
                resolver, 2, expectedSha1, downloadDir, true);
        
        return downloader;
        }
    
    private void recordAllSha1s(final File file) throws Exception
        {
        LOG.debug("Recording SHA1s....");
        
        final int chunkSize = 100000;
        final ByteBuffer[] bufs = createBuffers(file, chunkSize);
        //final long read = fc.read(bufs);
        //assertEquals(fc.size(), read);
        
        ByteBuffer lastBuf = null;
        for (final ByteBuffer buf : bufs)
            {
            assertFalse("ByteBuffers equal!! "+buf+"  "+lastBuf, buf.equals(lastBuf));
            final URI sha1 = Sha1Hasher.createSha1Urn(buf);
            //LOG.debug(sha1.toString());
            
            lastBuf = buf;
            }
        }

    private ByteBuffer[] createBuffers(final File file, final int chunkSize)
        throws Exception
        {
        final FileInputStream fis = new FileInputStream(file);
        //final FileChannel fc = fis.getChannel();
        //fc.position(0);
        final int numChunks = (int) Math.ceil(file.length()/(double) chunkSize);
        final ByteBuffer[] bufs = new ByteBuffer[numChunks];
        int totalSize = 0;
        for (int i = 0; i < bufs.length; i++)
            {
            final int curSize;
            if (i == bufs.length - 1)
                {
                //LOG.debug("Adding last chunk");
                curSize = (int) (file.length() - (i * chunkSize));
                }
            else
                {
                curSize = chunkSize;
                }
            final byte[] bytes = new byte[curSize];
            LOG.debug("Reading from "+totalSize+" to "+(totalSize+curSize) +
                " of file with size: "+file.length());
            fis.read(bytes, totalSize, curSize);
            
            bufs[i] = ByteBuffer.wrap(bytes);
            
            /*
            final int read = fc.read(bufs[i]);
            assertEquals(curSize, read);
            LOG.debug("FC position: "+fc.position());
            totalSize += curSize;
            assertEquals(totalSize, fc.position());
            */
            totalSize += curSize;
            }
        fis.close();
        
        assertEquals("Did not create bufs correctly", file.length(), totalSize);
        return bufs;
        }

    @Test public void testSha1Verifier() throws Exception
        {
        final String title = "Test-File";
        final File file = new File (title);
        
        final long expectedSize = 3534076L;
        
        final String sha1String = "urn:sha1:WUOWD7AATBMW4K3EV3TFMJ6FO6SNLZIS";
        final URI expectedSha1 = new URI (sha1String);
        
        final Downloader<MsDState> baseDownloader = 
            getBaseDownloader (file, expectedSha1);

        final Downloader<Sha1DState<MsDState>> downloader =
                new Sha1Downloader<MsDState> (baseDownloader, expectedSha1, 
                    expectedSize);
        
        downloader.start ();
        
        assertEquals ("Unexpected state: "+downloader.getState (), 
            new Sha1DState.VerifiedSha1Impl<MsDState> (),
            downloader.getState ());
        
        //recordAllSha1s(file);
        }
    }
