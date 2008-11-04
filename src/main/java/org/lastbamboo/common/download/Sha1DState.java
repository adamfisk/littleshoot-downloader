package org.lastbamboo.common.download;


/**
 * The state for the SHA-1 verifying downloader.  This downloader delegates to
 * a delegate downloader and verifies that the SHA-1 hash of the file downloaded
 * by the delegate matches an expected value.
 * 
 * @param <DelegateStateT>
 *      The state of the downloader delegate.  This allows any delegate
 *      downloader to be plugged in.
 */
public interface Sha1DState<DelegateStateT> extends DownloaderState
    {
    /**
     * A visitor for a SHA-1 downloader state.
     * 
     * @param <T> The type of this visitor's return value.
     * @param <DelegateStateT> The type of delegate state held in the SHA-1 
     * state.
     */
    public interface Visitor<T,DelegateStateT>
        {
        
        /**
         * Visits a downloading state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitDownloading (Downloading<DelegateStateT> state);
        
        /**
         * Visits a downloading state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitFailed (Failed<DelegateStateT> state);
        
        /**
         * Visits a verifying SHA-1 state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitVerifyingSha1 (VerifyingSha1<DelegateStateT> state);
        
        /**
         * Visits a verified SHA-1 state.
         * 
         * @param state The state.
         *      
         * @return The result of the visitation.
         */
        T visitVerifiedSha1 (VerifiedSha1<DelegateStateT> state);
        
        /**
         * Visits a SHA-1 mismatch state.
         * 
         * @param state The state.
         *      
         * @return  The result of the visitation.
         */
        T visitSha1Mismatch (Sha1Mismatch<DelegateStateT> state);
        }
    
    /**
     * An adaptor to help implement visitors.
     * 
     * @param <T> The type of this visitor's return value.
     * @param <DelegateStateT> The type of delegate state held in the SHA-1 
     * state.
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

        public T visitSha1Mismatch (final Sha1Mismatch<DelegateStateT> state)
            {
            return m_defaultValue;
            }

        public T visitVerifyingSha1 (final VerifyingSha1<DelegateStateT> state)
            {
            return m_defaultValue;
            }

        public T visitVerifiedSha1 (final VerifiedSha1<DelegateStateT> state)
            {
            return m_defaultValue;
            }
        }

    /**
     * A state that indicates that the delegate downloader is downloading.
     * 
     * @param <T> The delegate state type.
     */
    public interface Downloading<T> extends Sha1DState<T>
        {
        /**
         * Returns the delegate downloader state.
         * 
         * @return The delegate downloader state.
         */
        T getDelegateState ();
        }
    
    /**
     * A state that indicates that the delegate downloader has failed.
     * 
     * @param <T> The delegate state type.
     */
    public interface Failed<T> extends Sha1DState<T>
        {
        /**
         * Returns the delegate downloader state.
         * 
         * @return The delegate downloader state.
         */
        T getDelegateState ();
        }
    
    /**
     * A state that indicates the SHA-1 is in the process of being verified.
     * 
     * @param <T> The delegate state type.
     */
    public interface VerifyingSha1<T> extends Sha1DState<T> {}
    
    /**
     * A state that indicates the SHA-1 has been verified.
     * 
     * @param <T> The delegate state type.
     */
    public interface VerifiedSha1<T> extends Sha1DState<T> {}
    
    /**
     * A state that indicates the SHA-1 did not match the expected value.
     * 
     * @param <T> The delegate state type.
     */
    public interface Sha1Mismatch<T> extends Sha1DState<T> {}

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
            return visitor.visitFailed(this);
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
        
        /**
         * {@inheritDoc}
         */
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
     * An implementation of the verifying SHA-1 state.
     * 
     * @param <T> The delegate state type.
     */
    public class VerifyingSha1Impl<T>
            extends DownloaderState.AbstractRunning implements VerifyingSha1<T>
        {
        /**
         * {@inheritDoc}
         */
        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitVerifyingSha1 (this);
            }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof VerifyingSha1;
            }
        }

    /**
     * An implementation of the verified SHA-1 state.
     * 
     * @param <T> The delegate state type.
     */
    public class VerifiedSha1Impl<T>
        extends DownloaderState.AbstractSucceeded implements VerifiedSha1<T>
        {

        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitVerifiedSha1 (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof VerifiedSha1;
            }
        }

    /**
     * An implementation of the SHA-1 mismatch state.
     * 
     * @param <T> The delegate state type.
     */
    public class Sha1MismatchImpl<T>
        extends DownloaderState.AbstractFailed implements Sha1Mismatch<T>
        {

        public <ReturnT> ReturnT accept (final Visitor<ReturnT,T> visitor)
            {
            return visitor.visitSha1Mismatch (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Sha1Mismatch;
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
    <T> T accept (Visitor<T,DelegateStateT> visitor);
    }
