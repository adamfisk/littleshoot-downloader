package org.lastbamboo.common.download;

import java.util.Collection;
import java.util.LinkedList;

/**
 * An abstract base class to help implement downloaders.
 * 
 * @param <T>
 *      The downloader state type.
 */
public abstract class AbstractDownloader<T extends DownloaderState>
        implements Downloader<T>
    {
    /**
     * The listeners to be notified of events involving this downloader.
     */
    private final Collection<DownloaderListener<T>> m_listeners;
    
    /**
     * Initializes this abstract base class.
     */
    public AbstractDownloader
            ()
        {
        m_listeners = new LinkedList<DownloaderListener<T>> ();
        }
    
    /**
     * Fires notification that this downloader's state has changed.
     * 
     * @param state
     *      The new state.
     */
    protected final void fireStateChanged
            (final T state)
        {
        for (final DownloaderListener<T> listener : m_listeners)
            {
            listener.stateChanged (state);
            }
        }
    
    /**
     * {@inheritDoc}
     */
    public final void addListener
            (final DownloaderListener<T> listener)
        {
        m_listeners.add (listener);
        }

    /**
     * {@inheritDoc}
     */
    public void removeListener
            (final DownloaderListener<T> listener)
        {
        m_listeners.remove (listener);
        }
    }
