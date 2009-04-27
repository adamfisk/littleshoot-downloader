package org.lastbamboo.common.download;

import java.io.File;
import java.io.OutputStream;

import org.lastbamboo.common.download.Sha1DState.Downloading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A downloader that checks the SHA-1 of a resource downloaded by a delegate
 * downloader.
 * 
 * @param <DsT> The state type of the delegate downloader.
 */
public final class DummySha1Downloader<DsT extends DownloaderState>
    extends AbstractDownloader<Sha1DState<DsT>>
    implements Downloader<Sha1DState<DsT>>
    {
    /**
     * The logger for this class.
     */
    private final Logger m_log = LoggerFactory.getLogger(getClass());
    
    /**
     * The delegate downloader whose download product's SHA-1 is checked.
     */
    private final Downloader<DsT> m_delegate;

    /**
     * The current state of this downloader.
     */
    private Sha1DState<DsT> m_state;

    private final long m_expectedSize;
    
    /**
     * Constructs a new downloader.
     * 
     * @param delegate The delegate downloader.
     * @param expectedSize The expected size of the file.
     */
    public DummySha1Downloader (final Downloader<DsT> delegate,
        final long expectedSize)
        {
        m_delegate = delegate;
        m_expectedSize = expectedSize;
        m_state = new Sha1DState.DownloadingImpl<DsT> (m_delegate.getState ());
        m_delegate.addListener (new DelegateListener ());
        }
    
    /**
     * Sets the current state of this SHA-1 verifying downloader.
     * 
     * @param state The new state.
     */
    private void setState (final Sha1DState<DsT> state)
        {
        if (m_state.equals (state))
            {
            // Do nothing.  The state has not changed.
            }
        else
            {
            m_state = state;
            fireStateChanged (state);
            }
        }
    
    public File getIncompleteFile ()
        {
        return m_delegate.getIncompleteFile ();
        }
    
    public long getSize ()
        {
        return m_delegate.getSize ();
        }
    
    public Sha1DState<DsT> getState ()
        {
        return m_state;
        }

    public void start ()
        {
        m_delegate.start ();
        }
 
    public void stop(final boolean removeFiles)
        {
        this.m_delegate.stop(removeFiles);
        }
    
    public void pause()
        {
        this.m_delegate.pause();
        }
    
    public void resume()
        {
        this.m_delegate.resume();
        }
    
    public boolean isStarted ()
        {
        return m_delegate.isStarted();
        }
    
    public void write (final OutputStream os, final boolean cancelOnStreamClose)
        {
        m_delegate.write (os, cancelOnStreamClose);
        }

    public String getFinalName()
        {
        return m_delegate.getFinalName();
        }

    public File getCompleteFile()
        {
        return m_delegate.getCompleteFile();
        }
    
    /**
     * The listener attached to the delegate downloader.
     */
    private class DelegateListener implements DownloaderListener<DsT>
        {        

        public void stateChanged (final DsT state)
            {
            if (state.getType () == DownloaderStateType.SUCCEEDED)
                {
                downloadComplete ();
                }
            else if (state.getType() == DownloaderStateType.FAILED)
                {
                m_log.debug("Download failed...");
                setState(new Sha1DState.FailedImpl<DsT> (state));
                }
            else
                {
                if (isDownloading (m_state))
                    {
                    m_log.debug ("Is downloading");
                    setState (new Sha1DState.DownloadingImpl<DsT> (state));
                    }
                else
                    {
                    m_log.debug ("Is not downloading");
                    
                    // Ignore events from the delegate downloader if we do not
                    // think that we are in the downloading state.  Something is
                    // odd if this happens, though.
                    m_log.warn ("Got delegate downloader event despite being " +
                                  "done with the downloader: " + state);
                    }
                }
            }
        
        /**
         * Performs the necessary actions when the delegate downloader has
         * successfully completed.
         */
        private void downloadComplete ()
            {
            final File file = m_delegate.getIncompleteFile ();
            
            if (file == null)
                {
                // The file can be null if we're doing a BitTorrent download
                // for multiple files, for example.
                setState (new Sha1DState.VerifiedSha1Impl<DsT> ());
                }
            else
                {
                // First just make sure the size is correct.
                if (file.length() != m_expectedSize)
                    {
                    m_log.warn ("The downloaded file has an unexpected size.  " +
                        "Expected: "+ m_expectedSize + " but was: " + file.length());
               
                    setState (new Sha1DState.Sha1MismatchImpl<DsT> ());
                    }
                else
                    {
                    setState (new Sha1DState.VerifiedSha1Impl<DsT> ());
                    }
                }
            }
        }
    
    
    /**
     * Returns whether a given state is the downloading state.
     * 
     * @param <DsT> The type of the underlying delegate downloading state.
     * @param state The state.
     * @return True if the state is the downloading state, false otherwise.
     */
    private static <DsT> boolean isDownloading (final Sha1DState<DsT> state)
        {
        final Sha1DState.VisitorAdapter<Boolean,DsT> visitor =
            new Sha1DState.VisitorAdapter<Boolean,DsT> (Boolean.FALSE)
            {
            @Override
            public Boolean visitDownloading (final Downloading<DsT> downloading)
                {
                return Boolean.TRUE;
                }
            };
            
        return state.accept (visitor).booleanValue();
        }
    }
