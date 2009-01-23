package org.lastbamboo.common.download;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang.math.LongRange;

/**
 * Adapter class for file launchers.  Can be used a stub for failed downloaders
 * or for anything else.
 */
public class LaunchFileTrackerAdapter implements LaunchFileTracker
    {

    public int getActiveWriteCalls()
        {
        return 0;
        }

    public void onDownloadStopped()
        {

        }

    public void onFailure()
        {

        }

    public void onFileComplete()
        {

        }

    public void waitForLaunchersToComplete()
        {

        }

    public void write(OutputStream os, boolean cancelOnStreamClose)
            throws IOException
        {

        }

    public void onRangeComplete(LongRange range)
        {

        }

    }
