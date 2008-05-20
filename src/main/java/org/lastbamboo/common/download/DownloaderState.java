package org.lastbamboo.common.download;

/**
 * A downloader state.  Most downloaders should
 */
public interface DownloaderState
    {
    /**
     * An abstract state for implementing running states.
     */
    public static abstract class AbstractRunning implements DownloaderState
        {
        public DownloaderStateType getType ()
            {
            return DownloaderStateType.RUNNING;
            }
        }
    
    /**
     * An abstract state for implementing failed states.
     */
    public static abstract class AbstractFailed implements DownloaderState
        {
        public DownloaderStateType getType()
            {
            return DownloaderStateType.FAILED;
            }
        }
    
    /**
     * An abstract state for implementing succeeded states.
     */
    public static abstract class AbstractSucceeded implements DownloaderState
        {
        public DownloaderStateType getType()
            {
            return DownloaderStateType.SUCCEEDED;
            }
        }
    
    /**
     * Returns the type of this state.
     * 
     * @return The type of this state.
     */
    DownloaderStateType getType();
    }
