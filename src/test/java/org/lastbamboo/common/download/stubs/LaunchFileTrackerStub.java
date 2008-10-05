package org.lastbamboo.common.download.stubs;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.lang.math.LongRange;
import org.lastbamboo.common.download.LaunchFileTracker;

public class LaunchFileTrackerStub implements LaunchFileTracker
    {

    public int getActiveWriteCalls()
        {
        // TODO Auto-generated method stub
        return 0;
        }

    public void onDownloadStopped()
        {
        // TODO Auto-generated method stub

        }

    public void onFailure()
        {
        // TODO Auto-generated method stub

        }

    public void onFileComplete()
        {
        // TODO Auto-generated method stub

        }

    public void waitForLaunchersToComplete()
        {
        // TODO Auto-generated method stub

        }

    public void write(OutputStream os, boolean cancelOnStreamClose)
            throws IOException
        {
        // TODO Auto-generated method stub

        }

    public void onRangeComplete(LongRange range)
        {
        // TODO Auto-generated method stub

        }

    }
