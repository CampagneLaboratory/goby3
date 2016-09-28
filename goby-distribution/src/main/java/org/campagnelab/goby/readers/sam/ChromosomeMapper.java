package org.campagnelab.goby.readers.sam;

import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

/**
 *  Chromosome mapper.
 *
 * @author Manuele Simi
 *         Date: Sep 28, 2016
 *         Time: 12:23:50 PM
 */
public class ChromosomeMapper {

    /**
     * Adjust reference names to match genome.
     *
     * @param genome        reference genome
     * @param referenceName reference name
     * @return the possibly re-mapped reference name
     */

    public static String chromosomeNameMapping(final RandomAccessSequenceInterface genome, final String referenceName) {
        if (genome.getReferenceIndex(referenceName) == -1) {
            if (referenceName.contentEquals("chrM")) {
                return "MT";
            }
            if (referenceName.startsWith("chr")) {
                return referenceName.substring(3);
            } else {
                return referenceName;
            }
        } else {
            return referenceName;
        }
    }
}
