package org.lastbamboo.common.download;

import java.util.Comparator;

import org.littleshoot.util.None;
import org.littleshoot.util.Optional;
import org.littleshoot.util.OptionalVisitor;
import org.littleshoot.util.Some;

/**
 * Comparator that compares downloaders based on their speed.  Faster 
 * downloaders are preferred over slower ones.
 */
public class DownloadSpeedComparator implements Comparator<RangeDownloader>
    {
    /**
     * Returns a canonical integer for comparison from an optional kilobytes
     * per second value.
     * 
     * @param kbs The optional kilobytes per second value.
     *      
     * @return A canonical integer for comparison.
     */
    private static int canonicalize (final Optional<Integer> kbs)
        {
        final OptionalVisitor<Integer,Integer> visitor =
            new OptionalVisitor<Integer,Integer> ()
            {
            public Integer visitSome (final Some<Integer> some)
                {
                return some.object ();
                }
            
            public Integer visitNone (final None<Integer> none)
                {
                // We use -1 for unknown values to make sure that they are less
                // than all real values.  All real values are >= 0.
                return -1;
                }
            };
            
        return kbs.accept (visitor);
        }
    
    /**
     * {@inheritDoc}
     */
    public int compare (final RangeDownloader dl0, final RangeDownloader dl1)
        {
        // The ordering goes from faster to slower.
        
        final int kbs0 = canonicalize (dl0.getKbs ());
        final int kbs1 = canonicalize (dl1.getKbs ());
        
        if (kbs0 < kbs1)
            {
            return 1;
            }
        else if (kbs1 < kbs0)
            {
            return -1;
            }
        else
            {
            return 0;
            }
        }
    }
