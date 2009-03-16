package org.lastbamboo.common.download;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

/**
 * An abstract base class to help implement downloaders.
 * 
 * @param <T> The downloader state type.
 */
public abstract class AbstractDownloader<T extends DownloaderState>
    implements Downloader<T>
    {
    /**
     * The listeners to be notified of events involving this downloader.
     */
    private final Collection<DownloaderListener<T>> m_listeners;
    private final long m_startTime;
    
    /**
     * Initializes this abstract base class.
     */
    public AbstractDownloader()
        {
        m_listeners = new LinkedList<DownloaderListener<T>> ();
        m_startTime = new Date().getTime();
        }
    
    /**
     * Fires notification that this downloader's state has changed.
     * 
     * @param state The new state.
     */
    protected final void fireStateChanged (final T state)
        {
        synchronized (this.m_listeners)
            {
            for (final DownloaderListener<T> listener : m_listeners)
                {
                listener.stateChanged (state);
                }
            }
        }
    
    public final void addListener (final DownloaderListener<T> listener)
        {
        synchronized (this.m_listeners)
            {
            m_listeners.add (listener);
            }
        }

    public void removeListener (final DownloaderListener<T> listener)
        {
        synchronized (this.m_listeners)
            {
            m_listeners.remove (listener);
            }
        }

    public long getStartTime()
        {
        return m_startTime;
        }
    }
