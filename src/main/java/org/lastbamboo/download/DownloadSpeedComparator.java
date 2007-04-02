package org.lastbamboo.download;

import java.util.Comparator;


/**
 * Comparator that compares downloaders based on their speed.  Faster 
 * downloaders are preferenced over slower ones.
 */
public class DownloadSpeedComparator implements Comparator<RangeDownloader>
    {

    public int compare(final RangeDownloader dl0, final RangeDownloader dl1)
        {
        if (dl0.getKbs() > dl1.getKbs())
            {
            return -1;
            }
        if (dl0.getKbs() < dl1.getKbs())
            {
            return 1;
            }

        return 0;
        }

    }
