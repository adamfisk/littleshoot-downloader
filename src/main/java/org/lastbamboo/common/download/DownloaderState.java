package org.lastbamboo.common.download;

import java.util.concurrent.TimeUnit;

import org.littleshoot.util.DateUtils;
import org.littleshoot.util.TimeUtils;

/**
 * A downloader state.  
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
        
        public String calculateTimeRemaining(final long read, final long size,
            final double kbs)
            {
            final long bytesRemaining = size - read;
            final double bs = kbs * 1024;
            
            final long secondsRemaining = (long) (bytesRemaining/bs);
            
            return TimeUtils.secondsToHoursMinutesSeconds(secondsRemaining);
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
