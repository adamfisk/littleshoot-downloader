package org.lastbamboo.common.download;


/**
 * The state for multi-source downloaders.
 */
public interface MsDState extends DownloaderState {
        
    /**
     * A visitor for a downloader state.
     * 
     * @param <T> The type of this visitor's return value.
     */
    public interface Visitor<T> {
        /**
         * Visits an idle state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitIdle (Idle state);

        /**
         * Visits a getting sources state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitGettingSources (GettingSources state);

        /**
         * Visits a complete state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitComplete (Complete state);
        
        /**
         * Visits a paused state.
         * 
         * @param paused The paused state.
         * @return The result of the visitation.
         */
        T visitPaused(Paused paused);

        /**
         * Visits a canceled state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitCanceled (Canceled state);

        /**
         * Visits a no sources available state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitNoSourcesAvailable (NoSourcesAvailable state);

        /**
         * Visits a could not determine sources state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitCouldNotDetermineSources (CouldNotDetermineSources state);

        /**
         * Visits a general failed state.
         * 
         * @param state The state.
         * @return The result of the visitation.
         */
        T visitFailed(Failed state);

        /**
         * Visits a downloading state for a LittleShoot download.
         * 
         * @param littleShootDownloadingState The LittleShoot downloading state
         * to visit.
         * @return The result of the visitation.
         */
        T visitLittleShootDownloading(
            LittleShootDownloading littleShootDownloadingState);
        
        /**
         * Visits a downloading state for a LimeWire download.
         * 
         * @param limeWireDownloadingState The LimeWire downloading state
         * to visit.
         * @return The result of the visitation.
         */
        T visitLimeWireDownloading(
            LimeWireDownloading limeWireDownloadingState);
        
        /**
         * Visits a downloading state for a LibTorrent download.
         * 
         * @param libTorrentDownloadingState The LibTorrent downloading state
         * to visit.
         * @return The result of the visitation.
         */
        T visitLibTorrentDownloading(
            LibTorrentDownloading libTorrentDownloadingState);
    }

    /**
     * An adaptor to help implement visitors.
     * 
     * @param <T> The type of this visitor's return value.
     */
    public abstract class VisitorAdapter<T> implements Visitor<T> {
        /**
         * The default value returned by this visitor.
         */
        private final T m_defaultValue;

        /**
         * Constructs a new visitor adapter.
         * 
         * @param defaultValue The default value returned by this visitor.
         */
        public VisitorAdapter(final T defaultValue) {
            m_defaultValue = defaultValue;
        }

        public T visitCouldNotDetermineSources(
                final CouldNotDetermineSources state) {
            return m_defaultValue;
        }

        public T visitComplete(final Complete state) {
            return m_defaultValue;
        }

        public T visitPaused(final Paused state) {
            return m_defaultValue;
        }

        public T visitCanceled(final Canceled state) {
            return m_defaultValue;
        }

        public T visitFailed(final Failed state) {
            return m_defaultValue;
        }

        public T visitGettingSources(final GettingSources state) {
            return m_defaultValue;
        }

        public T visitIdle(final Idle state) {
            return m_defaultValue;
        }

        public T visitNoSourcesAvailable(final NoSourcesAvailable state) {
            return m_defaultValue;
        }

        public T visitLittleShootDownloading(
                final LittleShootDownloading littleShootDownloadingState) {
            return m_defaultValue;
        }

        public T visitLimeWireDownloading(
                final LimeWireDownloading limeWireDownloadingState) {
            return m_defaultValue;
        }

        public T visitLibTorrentDownloading(
                final LibTorrentDownloading libTorrentDownloadingState) {
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
     * A state that indicates that the downloader is paused.
     */
    public interface Paused extends MsDState {
        /**
         * Accessor for the underlying downloading state.
         * 
         * @return The underlying downloading state.
         */
        Downloading getDownloadingState();
    }
    
    /**
     * A state that indicates that the downloader is downloading.
     */
    public interface Downloading extends MsDState {
        /**
         * Returns the speed of the download in kilobytes per second.
         * 
         * @return The speed of the download in kilobytes per second.
         */
        double getKbs();

        /**
         * Returns the number of sources used by the download.
         * 
         * @return The number of sources used by the download.
         */
        int getNumSources();

        /**
         * Accessor for the number of bytes read.
         * 
         * @return The number of bytes read.
         */
        long getBytesRead();

        /**
         * Accessor for the time remaining.
         * 
         * @return
         */
        String getTimeRemaining();
    }

    /**
     * A state for LittleShoot downloading.
     */
    public interface LittleShootDownloading extends Downloading {

    }
    
    /**
     * A state for LimeWire downloading.
     */
    public interface LimeWireDownloading extends Downloading {
    }

    /**
     * A state for LibTorrent downloading.
     */
    public interface LibTorrentDownloading extends Downloading {
        /**
         * Returns the maximum contiguous byte we've read for this download.
         * This is the maximum byte starting from the beginning of the file with
         * no gaps.
         * 
         * @return The maximum contiguous byte we've read for this download.
         */
        long getMaxContiguousByte();

        /**
         * Gets the number of files in this torrent.
         * 
         * @return The number of files in this torrent.
         */
        int getNumFiles();
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
     * A state that indicates that the downloader failed.
     */
    public interface Failed extends MsDState {}
    
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
    public class IdleImpl extends DownloaderState.AbstractRunning implements
            Idle {
        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitIdle(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof Idle;
        }
    }

    /**
     * An implementation of the getting sources state.
     */
    public class GettingSourcesImpl extends DownloaderState.AbstractRunning
        implements GettingSources {
        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitGettingSources(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof GettingSources;
        }
    }

    /**
     * An implementation of the LibTorrent downloading state.
     */
    public class LibTorrentDownloadingState
        extends DownloaderState.AbstractRunning implements LibTorrentDownloading {
        
        /**
         * The speed of the downloading in kilobytes per second.
         */
        private final double m_kbs;

        /**
         * The number of sources used by the download.
         */
        private final int m_numSources;

        private final long m_bytesRead;

        private final int m_numFiles;

        private final long m_maxByte;

        private final long m_size;

        /**
         * Constructs a new downloading state.
         *
         * @param bytesPerSecond The speed of the download in bytes per second.
         * @param numSources The number of sources used by the download.
         * @param bytesRead The number of bytes read.
         * @param maxByte The maximum contiguous byte we've read.
         * @param numFiles The number of files in the torrent.
         */
        public LibTorrentDownloadingState(final int bytesPerSecond,
            final int numSources, final long bytesRead, final int numFiles,
            final long maxByte, final long size) {
            this.m_kbs = bsToKbs(bytesPerSecond);
            this.m_numSources = numSources;
            this.m_bytesRead = bytesRead;
            this.m_numFiles = numFiles;
            this.m_maxByte = maxByte;
            this.m_size = size;
        }

        private double bsToKbs(final double bytesPerSecond) {
            final double kbs = bytesPerSecond/1024.0;
            return kbs;
        }

        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitLibTorrentDownloading(this);
        }

        public double getKbs() {
            return m_kbs;
        }

        public int getNumSources() {
            return m_numSources;
        }

        public long getBytesRead() {
            return this.m_bytesRead;
        }

        public long getMaxContiguousByte() {
            return this.m_maxByte;
        }

        public int getNumFiles() {
            return this.m_numFiles;
        }

        public String getTimeRemaining() {
            return calculateTimeRemaining(this.m_bytesRead, this.m_size,
                    this.m_kbs);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + (int) (this.m_bytesRead ^ (this.m_bytesRead >>> 32));
            long temp;
            temp = Double.doubleToLongBits(this.m_kbs);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result
                    + (int) (this.m_maxByte ^ (this.m_maxByte >>> 32));
            result = prime * result + this.m_numFiles;
            result = prime * result + this.m_numSources;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LibTorrentDownloadingState other = (LibTorrentDownloadingState) obj;
            if (this.m_bytesRead != other.m_bytesRead)
                return false;
            if (Double.doubleToLongBits(this.m_kbs) != Double
                    .doubleToLongBits(other.m_kbs))
                return false;
            if (this.m_maxByte != other.m_maxByte)
                return false;
            if (this.m_numFiles != other.m_numFiles)
                return false;
            if (this.m_numSources != other.m_numSources)
                return false;
            return true;
        }
    }
    
    /**
     * An implementation of the LimeWire downloading state.
     */
    public class LimeWireDownloadingState
        extends DownloaderState.AbstractRunning implements LimeWireDownloading {
        
        /**
         * The speed of the downloading in kilobytes per second.
         */
        private final double m_kbs;

        /**
         * The number of sources used by the download.
         */
        private final int m_numSources;

        private final long m_bytesRead;

        private final long m_size;

        /**
         * Constructs a new downloading state.
         *
         * @param kbs The speed of the downloading n kilobytes per second.
         * @param numSources The number of sources used by the download.
         * @param bytesRead The number of bytes read.
         */
        public LimeWireDownloadingState(final double kbs, final int numSources,
                final long bytesRead, final long size) {
            m_kbs = kbs;
            m_numSources = numSources;
            this.m_bytesRead = bytesRead;
            this.m_size = size;
        }

        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitLimeWireDownloading(this);
        }

        public double getKbs() {
            return m_kbs;
        }

        public int getNumSources() {
            return m_numSources;
        }

        public long getBytesRead() {
            return this.m_bytesRead;
        }

        public String getTimeRemaining() {
            return calculateTimeRemaining(this.m_bytesRead, this.m_size,
                    this.m_kbs);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + (int) (this.m_bytesRead ^ (this.m_bytesRead >>> 32));
            long temp;
            temp = Double.doubleToLongBits(this.m_kbs);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result + this.m_numSources;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LimeWireDownloadingState other = (LimeWireDownloadingState) obj;
            if (this.m_bytesRead != other.m_bytesRead)
                return false;
            if (Double.doubleToLongBits(this.m_kbs) != Double
                    .doubleToLongBits(other.m_kbs))
                return false;
            if (this.m_numSources != other.m_numSources)
                return false;
            return true;
        }
    }

    /**
     * An implementation of the downloading state.
     */
    public class LittleShootDownloadingState
        extends DownloaderState.AbstractRunning 
        implements LittleShootDownloading {
        
        /**
         * The number of sources used by the download.
         */
        private final int m_numSources;

        private final RateCalculator m_rateCalculator;

        private final long m_size;

        /**
         * Constructs a new downloading state.
         * 
         * @param rateCalculator The class that calculates the download rate
         * and bytes read. 
         * @param numSources The number of sources used by the download.
         */
        public LittleShootDownloadingState(final RateCalculator rateCalculator,
                final int numSources, final long size) {
            this.m_rateCalculator = rateCalculator;
            this.m_numSources = numSources;
            this.m_size = size;
        }

        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitLittleShootDownloading(this);
        }

        public double getKbs() {
            return this.m_rateCalculator.getRate();
        }

        public int getNumSources() {
            return m_numSources;
        }

        public long getBytesRead() {
            return this.m_rateCalculator.getBytesRead();
        }

        public String getTimeRemaining() {
            return calculateTimeRemaining(getBytesRead(), this.m_size, getKbs());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + this.m_numSources;
            result = prime
                    * result
                    + ((this.m_rateCalculator == null) ? 0
                            : this.m_rateCalculator.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LittleShootDownloadingState other = (LittleShootDownloadingState) obj;
            if (this.m_numSources != other.m_numSources)
                return false;
            if (this.m_rateCalculator == null) {
                if (other.m_rateCalculator != null)
                    return false;
            } else if (!this.m_rateCalculator.equals(other.m_rateCalculator))
                return false;
            return true;
        }

        }

    
    /**
     * An implementation of the paused state.
     */
    public class PausedImpl
        extends DownloaderState.AbstractRunning implements Paused
        {
        
        private final Downloading m_downloading;

        /**
         * Creates a new paused state.
         * 
         * @param downloading The downloading state at the moment the pause
         * was initiated.
         */
        public PausedImpl(final Downloading downloading)
            {
            this.m_downloading = downloading;
            }
        
        public <T> T accept (final Visitor<T> visitor)
            {
            return visitor.visitPaused (this);
            }
        
        @Override
        public boolean equals (final Object otherObject)
            {
            return otherObject instanceof Canceled;
            }

        public Downloading getDownloadingState()
            {
            return m_downloading;
            }
        }
    
    /**
     * An implementation of the complete state.
     */
    public class CompleteImpl extends DownloaderState.AbstractSucceeded
            implements Complete {

        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitComplete(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof Complete;
        }
    }

    /**
     * An implementation of the canceled state.
     */
    public class CanceledImpl extends DownloaderState.AbstractFailed implements
            Canceled {
        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitCanceled(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof Canceled;
        }
    }

    /**
     * An implementation of the failed state.
     */
    public class FailedImpl extends DownloaderState.AbstractFailed implements
            Failed {
        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitFailed(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof Failed;
        }
    }
    
    /**
     * An implementation of the no sources available state.
     */
    public class NoSourcesAvailableImpl extends DownloaderState.AbstractFailed
            implements NoSourcesAvailable {
        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitNoSourcesAvailable(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof NoSourcesAvailable;
        }
    }

    /**
     * An implementation of the could not determine sources state.
     */
    public class CouldNotDetermineSourcesImpl extends
            DownloaderState.AbstractFailed implements CouldNotDetermineSources {

        public <T> T accept(final Visitor<T> visitor) {
            return visitor.visitCouldNotDetermineSources(this);
        }

        @Override
        public boolean equals(final Object otherObject) {
            return otherObject instanceof CouldNotDetermineSources;
        }
    }

    /**
     * Accepts a visitor to this state.
     * 
     * @param <T> The return type of the visitor.
     * @param visitor The visitor.
     * @return The result of the visitation.
     */
    <T> T accept (Visitor<T> visitor);

    /**
     * An instance of the idle state.
     */
    public static final MsDState IDLE = new IdleImpl ();

    /**
     * An instance of the getting sources state.
     */
    public static final MsDState GETTING_SOURCES = new GettingSourcesImpl ();

    /**
     * An instance of the complete state.
     */
    public static final MsDState COMPLETE = new CompleteImpl ();

    /**
     * An instance of the canceled state.
     */
    public static final MsDState CANCELED = new CanceledImpl ();

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

    /**
     * Failed state.
     */
    public static final MsDState FAILED = new FailedImpl();

    }
