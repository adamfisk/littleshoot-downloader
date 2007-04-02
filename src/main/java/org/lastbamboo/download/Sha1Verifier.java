package org.lastbamboo.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lastbamboo.util.Sha1Hasher;


/**
 * Verifies that downloads received the bytes they expected.
 */
public class Sha1Verifier implements DownloadListener
    {

    private static final Log LOG = LogFactory.getLog(Sha1Verifier.class);
    private final URI m_expectedSha1;
    
    /**
     * Creates a new verifier.
     * 
     * @param expectedSha1 The URN we expect to download.
     */
    public Sha1Verifier(final URI expectedSha1)
        {
        this.m_expectedSha1 = expectedSha1;
        }

    public void onDownloadComplete(final Downloader dl)
        {        
        final File file = dl.getFile();
        try
            {
            final URI sha1 = Sha1Hasher.createSha1Urn(file);
            if (sha1.equals(this.m_expectedSha1))
                {
                dl.setStatus("File Complete");
                dl.setSha1Urn(sha1, true);
                }
            else
                {
                LOG.warn("The downloaded file is corrupt.  Expected: "+
                    this.m_expectedSha1+"\n but was: "+sha1);
                dl.setStatus("File Corrupt!");
                dl.setSha1Urn(sha1, false);
                }
            }
        catch (final IOException e)
            {
            LOG.warn("Could not create SHA-1 for file: "+file);
            dl.setStatus("File Corrupt!");
            }
        }

    public void onDownloadCancelled(final Downloader dl)
        {
        // Ignore cancelled downloads...
        }

    }
