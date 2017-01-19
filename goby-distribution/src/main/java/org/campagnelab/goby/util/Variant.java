package org.campagnelab.goby.util;

import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;

import java.io.Serializable;
import java.util.List;


/**
 * This class stores the information from a VCF pertinent to one variant.
 * Will be stored in a map for use by AddTrueGenotypes.
 */
public class Variant implements Serializable {
    public String reference;
    public List<String> trueAlleles;
    public boolean isIndel;
    public int position;
    public int referenceIndex;
    int maxLen;

    /**
     * @param reference ref field of vcf
     * @param alleles list of
     * @param position zero-indexed position on genome
     * @param referenceIndex
     */
    public Variant(String reference, List<String> alleles, int position, int referenceIndex) {
        this.reference = reference;
        this.trueAlleles = alleles;
        maxLen = getMaxLen();
        this.isIndel = (maxLen > 1);
        this.position = position;
        this.referenceIndex = referenceIndex;
    }

    void realign(EquivalentIndelRegionCalculator equivalentIndelRegionCalculator){
        padAll();






//        final int startPosition = var.getPosition() + entryPosition - 1;
//        final int lastPosition = var.getPosition() + entryPosition +
//                Math.max(var.getFrom().length(), var.getTo().length()) - 1;


        ObservedIndel indel = new ObservedIndel(4, 5, "-", "T");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(3, indel);
//        assertEquals(3, result.referenceIndex);
//        assertEquals(4, result.startPosition);
//        assertEquals(7, result.endPosition);
//        assertEquals("-TT", result.from);
//        assertEquals("TTT", result.to);
//        assertEquals("AAAC", result.flankLeft);
//        assertEquals("GGGG", result.flankRight);
//        assertEquals("AAAC-TTGGGG", result.fromInContext());
//        assertEquals("AAACTTTGGGG", result.toInContext());

    }


    private String pad(int maxLen, String s) {
        StringBuffer pad = new StringBuffer(s);
        for (int i = 1; i <= maxLen; i++) {
            if (pad.length() < maxLen) {
                pad.append("-");
            }
        }
        return pad.toString();
    }

    private void padAll() {
        reference = pad(maxLen, reference);
        for (int i = 0; i < trueAlleles.size(); i++){
            trueAlleles.set(i,pad(maxLen,trueAlleles.get(i)));
        }
    }

    private int getMaxLen(){
        int maxLen = 0;
        for (String allele : trueAlleles){
            maxLen = Math.max(maxLen,allele.length());
        }
        maxLen = Math.max(maxLen,reference.length());
        return maxLen;

    }
}
