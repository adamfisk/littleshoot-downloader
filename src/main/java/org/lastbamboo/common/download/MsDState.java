package org.lastbamboo.common.download;


/**
 * The state for the multi-source downloader.
 */
public interface MsDState extends DownloaderState
    {
    /**
     * A visitor for a multi-source downloader state.
     * 
     * @param <T>
     *      The type of this visitor's return value.
     */
    public interface Visitor<T>
        {
        /**
         * Visits an idle state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitIdle (Idle state);

        /**
         * Visits a getting sources state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitGettingSources (GettingSources state);

        /**
         * Visits a downloading state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitDownloading (Downloading state);

        /**
         * Visits a complete state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitComplete (Complete state);

        /**
         * Visits a canceled state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitCanceled (Canceled state);

        /**
         * Visits a no sources available state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitNoSourcesAvailable (NoSourcesAvailable state);

        /**
         * Visits a could not determine sources state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitCouldNotDetermineSources (CouldNotDetermineSources state);
        }
    
    /**
     * An adaptor to help implement visitors.
     * 
     * @param <T> The type of this visitor's return value.
     */
    public abstract class VisitorAdapter<T> implements Visitor<T>
        {
        /**
         * The default value returned by this visitor.
         */
        private final T m_defaultValue;
        
        /**
         * Constructs a new visitor adapter.
         * 
         * @param defaultValue The default value returned by this visitor.
         */
        public VisitorAdapter (final T defaultValue)
            {
            m_defaultValue = defaultValue;
            }

        public T visitCouldNotDetermineSources (
            final CouldNotDetermineSources state)
            {
            return m_defaultValue;
            }

        public T visitComplete (final Complete state)
            {
            return m_defaultValue;
            }

        public T visitCanceled (final Canceled state)
            {
            return m_defaultValue;
            }

        /**
         * {@inheritDoc}
         */
        public T visitDownloading (final Downloading state)
            {
            return m_defaultValue;
            }

        public T visitGettingSources (final GettingSources state)
            {
            return m_defaultValue;
            }
        
        public T visitIdle (final Idle state)
            {
            return m_defaultValue;
            }

        public T visitNoSourcesAvailable (final NoSourcesAvailable state)
            {
            return m_defaultValue;
            }
        }
    
    /**
     * A state that indicates that the downloader is idle.
     */
    public interface Idle extends MsDState {}
    
    /**
     * A state that indicates that the downloader is getting sources.
     */
    public interface GettingSources extends MsDState {}

    /**
     * A state that indicates that the downloader is downloading.
     */
    public interface Downloading extends MsDState
        {
        /**
         * Returns the speed of the download in kilobytes per second.
         * 
         * @return
         *      The speed of the download in kilobytes per second.
         */
        int getKbs ();

        /**
         * Returns the number of sources used by the download.
         * 
         * @return
         *      The number of sources used by the download.
         */
        int getNumSources ();
        }

    /**
     * A state that indicates that the downloader is done downloading.
     */
    public interface Complete extends MsDState {}

    /**
     * A state that indicates that the downloader was canceled.
     */
    public interface Canceled extends MsDState {}

    /**
     * A state that indicates that the downloader could not find any available
     * sources.
     */
    public interface NoSourcesAvailable extends MsDState {}

    /**
     * A state that indicates that the downloader could not determine any
     * sources for the content.
     */
    public interface CouldNotDetermineSources extends MsDState {}

    /**
     * An implementation of the idle state.
     */
    public class IdleImpl
            extends DownloaderState.AbstractRunning implements Idle
        {
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitIdle (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Idle;
            }
        }

    /**
     * An implementation of the getting sources state.
     */
    public class GettingSourcesImpl
            extends DownloaderState.AbstractRunning implements GettingSources
        {
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitGettingSources (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof GettingSources;
            }
        }

    /**
     * An implementation of the downloading state.
     */
    public class DownloadingImpl
            extends DownloaderState.AbstractRunning implements Downloading
        {
        /**
         * The speed of the downloading in kilobytes per second.
         */
        private final int m_kbs;
        
        /**
         * The number of sources used by the download.
         */
        private final int m_numSources;
        
        /**
         * Constructs a new downloading state.
         * 
         * @param kbs The speed of the downloading n kilobytes per second.
         * @param numSources The number of sources used by the download.
         */
        public DownloadingImpl (final int kbs, final int numSources)
            {
            m_kbs = kbs;
            m_numSources = numSources;
            }
        
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitDownloading (this);
            }
        
        public int getKbs ()
            {
            return m_kbs;
            }
        
        public int getNumSources ()
            {
            return m_numSources;
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            if (otherObject instanceof Downloading)
                {
                final Downloading other = (Downloading) otherObject;
                return other.getKbs () == m_kbs;
                }
            else
                {
                return false;
                }
            }
        }

    /**
     * An implementation of the complete state.
     */
    public class CompleteImpl
            extends DownloaderState.AbstractSucceeded implements Complete
        {
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitComplete (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Complete;
            }
        }

    /**
     * An implementation of the canceled state.
     */
    public class CanceledImpl
            extends DownloaderState.AbstractFailed implements Canceled
        {
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitCanceled (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Canceled;
            }
        }

    /**
     * An implementation of the no sources available state.
     */
    public class NoSourcesAvailableImpl
         extends DownloaderState.AbstractFailed implements NoSourcesAvailable
        {
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitNoSourcesAvailable (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof NoSourcesAvailable;
            }
        }

    /**
     * An implementation of the could not determine sources state.
     */
    public class CouldNotDetermineSourcesImpl
            extends DownloaderState.AbstractFailed
            implements CouldNotDetermineSources
        {

        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitCouldNotDetermineSources (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof CouldNotDetermineSources;
            }
        }

    /**
     * Accepts a visitor to this state.
     * 
     * @param <T> The return type of the visitor.
     *      
     * @param visitor The visitor.
     *      
     * @return The result of the visitation.
     */
    <T> T accept (Visitor<T> visitor);

    /**
     * An instance of the idle state.
     */
    public static final MsDState IDLE =
            new IdleImpl ();

    /**
     * An instance of the getting sources state.
     */
    public static final MsDState GETTING_SOURCES =
            new GettingSourcesImpl ();

    /**
     * An instance of the complete state.
     */
    public static final MsDState COMPLETE =
            new CompleteImpl ();

    /**
     * An instance of the canceled state.
     */
    public static final MsDState CANCELED =
            new CanceledImpl ();

    /**
     * An instance of the no sources available state.
     */
    public static final MsDState NO_SOURCES_AVAILABLE =
            new NoSourcesAvailableImpl ();

    /**
     * An instance of the could not determine sources state.
     */
    public static final MsDState COULD_NOT_DETERMINE_SOURCES =
            new CouldNotDetermineSourcesImpl ();
    }
