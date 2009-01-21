package org.lastbamboo.common.download;

/**
 * The state for a downloader that moves a file from a temporary location to a
 * permanent one.  This downloader delegates to a delegate downloader and moves
 * the product of the delegate to a permanent location.
 * 
 * @param <DelegateStateT> The state of the downloader delegate.  This allows 
 *  any delegate downloader to be plugged in.
 */
public interface MoverDState<DelegateStateT> extends DownloaderState
    {
    
    /**
     * A visitor for a mover downloader state.
     * 
     * @param <T> The type of this visitor's return value.
     * @param <DelegateStateT> The type of delegate state held in the mover 
     *  state.
     */
    public interface Visitor<T,DelegateStateT>
        {
        /**
         * Visits a downloading state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitDownloading (Downloading<DelegateStateT> state);
        
        /**
         * Visits a failed state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitFailed (Failed<DelegateStateT> state);
        
        /**
         * Visits a moving state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitMoving (Moving<DelegateStateT> state);
        
        /**
         * Visits a moved state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitMoved (Moved<DelegateStateT> state);
 
        /**
         * Visits a moved to iTunes state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitMovedToITunes (MovedToITunes<DelegateStateT> state);
        
        /**
         * Visits a move failed state.
         * 
         * @param state The state.
         * @return  The result of the visitation.
         */
        T visitMoveFailed (MoveFailed<DelegateStateT> state);
        }
    
    /**
     * An adaptor to help implement visitors.
     * 
     * @param <T> The type of this visitor's return value.
     * @param <DelegateStateT> The type of delegate state held in the mover 
     *  state.
     */
    public abstract class VisitorAdapter<T,DelegateStateT>
            implements Visitor<T,DelegateStateT>
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

        public T visitDownloading (final Downloading<DelegateStateT> state)
            {
            return m_defaultValue;
            }
        
        public T visitFailed (final Failed<DelegateStateT> state)
            {
            return m_defaultValue;
            }

        public T visitMoving (final Moving<DelegateStateT> state)
            {
            return m_defaultValue;
            }

        public T visitMoved (final Moved<DelegateStateT> state)
            {
            return m_defaultValue;
            }
        
        public T visitMovedToITunes (final MovedToITunes<DelegateStateT> state)
            {
            return m_defaultValue;
            }

        public T visitMoveFailed (final MoveFailed<DelegateStateT> state)
            {
            return m_defaultValue;
            }
        }
    
    /**
     * A state that indicates that the delegate downloader is downloading.
     * 
     * @param <T> The delegate state type.
     */
    public interface Downloading<T> extends MoverDState<T>
        {
        /**
         * Returns the delegate downloader state.
         * @return The delegate downloader state.
         */
        T getDelegateState ();
        }
    
    /**
     * A state that indicates that the delegate downloader has failed.
     * 
     * @param <T> The delegate state type.
     */
    public interface Failed<T> extends MoverDState<T>
        {
        /**
         * Returns the delegate downloader state.
         * @return The delegate downloader state.
         */
        T getDelegateState ();
        }
        
    /**
     * A state that indicates that the temporary file is in the process of being
     * moved.
     * 
     * @param <T> The delegate state type.
     */
    public interface Moving<T> extends MoverDState<T> {}
        
    /**
     * A state that indicates that the temporary file has been moved.
     * 
     * @param <T> The delegate state type.
     */
    public interface Moved<T> extends MoverDState<T> {}
    
    /**
     * A state that indicates that the file has been added to iTunes.
     * 
     * @param <T> The delegate state type.
     */
    public interface MovedToITunes<T> extends MoverDState<T> {}
        
    /**
     * A state that indicates that the attempt to move the temporary file
     * failed.
     * 
     * @param <T> The delegate state type.
     */
    public interface MoveFailed<T> extends MoverDState<T> {}

    /**
     * An implementation of the downloading state.
     * 
     * @param <T> The delegate state type.
     */
    public class DownloadingImpl<T>
        extends DownloaderState.AbstractRunning implements Downloading<T>
        {
        /**
         * The delegate state.
         */
        private final T m_delegateState;
        
        /**
         * Constructs a new state.
         * 
         * @param delegateState The delegate state.
         */
        public DownloadingImpl (final T delegateState)
            {
            m_delegateState = delegateState;
            }
        
        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitDownloading (this);
            }
        
        public T getDelegateState ()
            {
            return m_delegateState;
            }
        
        @Override
        @SuppressWarnings("unchecked")
        public boolean equals (final Object otherObject)
            {
            if (otherObject instanceof Downloading)
                {
                final Downloading other = (Downloading) otherObject;
                
                return other.getDelegateState ().equals (m_delegateState);
                }
            else
                {
                return false;
                }
            }
        }
    
    /**
     * An implementation of the failed state.
     * 
     * @param <T> The delegate state type.
     */
    public class FailedImpl<T>
        extends DownloaderState.AbstractFailed implements Failed<T>
        {
        /**
         * The delegate state.
         */
        private final T m_delegateState;
        
        /**
         * Constructs a new state.
         * 
         * @param delegateState The delegate state.
         */
        public FailedImpl (final T delegateState)
            {
            m_delegateState = delegateState;
            }
        
        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitFailed (this);
            }
        
        public T getDelegateState ()
            {
            return m_delegateState;
            }
        
        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public boolean equals (final Object otherObject)
            {
            if (otherObject instanceof Failed)
                {
                final Failed other = (Failed) otherObject;
                
                return other.getDelegateState ().equals (m_delegateState);
                }
            else
                {
                return false;
                }
            }
        }
    
    /**
     * An implementation of the moving state.
     * 
     * @param <T> The delegate state type.
     */
    public class MovingImpl<T>
        extends DownloaderState.AbstractRunning implements Moving<T>
        {

        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitMoving (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Moving;
            }
        }

    /**
     * An implementation of the moved state.
     * 
     * @param <T> The delegate state type.
     */
    public class MovedImpl<T>
        extends DownloaderState.AbstractSucceeded implements Moved<T>
        {

        /*
        private final MsDState m_downloader;

        public MovedImpl(final MsDState downloader) 
            {
            this.m_downloader = downloader;
            }

        public MsDState getDownloader()
            {
            return m_downloader;
            }
            */
        
        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitMoved (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Moved;
            }
        }
    
    /**
     * An implementation of the moved state.
     * 
     * @param <T> The delegate state type.
     */
    public class MovedToITunesImpl<T>
        extends DownloaderState.AbstractSucceeded implements MovedToITunes<T>
        {

        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            //return visitor.visitMovedToITunes (this);
            return visitor.visitMovedToITunes(this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof MovedToITunes;
            }
        }

    /**
     * An implementation of the move failed state.
     * 
     * @param <T> The delegate state type.
     */
    public class MoveFailedImpl<T>
        extends DownloaderState.AbstractFailed implements MoveFailed<T>
        {
        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitMoveFailed (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof MoveFailed;
            }
        }

    /**
     * Accepts a visitor to this state.
     * 
     * @param <T> The return type of the visitor.
     * @param visitor The visitor.
     * @return The result of the visitation.
     */
    <T> T accept (Visitor<T,DelegateStateT> visitor);
    }
