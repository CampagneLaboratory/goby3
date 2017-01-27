package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;


/**
 * This class stores the information from a VCF pertinent to one variant.
 * Will be stored in a map for use by AddTrueGenotypes.
 */
public class Variant implements Serializable {
    public String reference;
    public Set<String> trueAlleles;
    public boolean isIndel;
    public int position;
    public int referenceIndex;
    int maxLen;
    private static final Logger LOG = LoggerFactory.getLogger(Variant.class);
    static WarningCounter fromMismatch = new WarningCounter(10);
    public static int numFromMistmaches = 0;
    public static int numIndelsEncountered = 0;



    /**
     * @param reference ref field of vcf
     * @param alleles list of
     * @param position zero-indexed position on genome
     * @param referenceIndex
     */
    public Variant(String reference, Set<String> alleles, int position, int referenceIndex) {
        this.reference = reference;
        this.trueAlleles = alleles;
        maxLen = getMaxLen();
        this.isIndel = (maxLen > 1);
        this.position = position;
        this.referenceIndex = referenceIndex;
    }

    Map<Integer,Variant> realign(EquivalentIndelRegionCalculator equivalentIndelRegionCalculator) {

        Map<Integer,Variant> equivVariants = new Int2ObjectArrayMap<Variant>(trueAlleles.size());

        //handle varas with only snps or ref
        if (maxLen == 1) {
            equivVariants.put(position,this);
            return equivVariants;
        }
        for (String s : trueAlleles) {
            //ignore ref alleles, they are not written to new Varset.
            if (s.equals(reference)) {
                continue;
            }
            int maxLenRefThisAllele = Math.max(reference.length(), s.length());
            String refAffix = pad(maxLenRefThisAllele, reference);
            String alleleAffix = pad(maxLenRefThisAllele, s);

            int allelePos = position;
            int diffStart;
            //clip the start further if it is the same.
            for (diffStart = 0; diffStart < refAffix.length(); diffStart++){
                if (refAffix.charAt(diffStart)!=alleleAffix.charAt(diffStart)){
                    refAffix = refAffix.substring(diffStart);
                    alleleAffix = alleleAffix.substring(diffStart);
                    allelePos += diffStart;
                    break;
                }
            }
            for (diffStart = refAffix.length()-1; diffStart >= 0; diffStart--){
                if (refAffix.charAt(diffStart)!=alleleAffix.charAt(diffStart)){
                    refAffix = refAffix.substring(0,diffStart+1);
                    alleleAffix = alleleAffix.substring(0,diffStart+1);
                    break;
                }
            }




            EquivalentIndelRegion result = new EquivalentIndelRegion();

            ObservedIndel indel = null;

            //very rare case where indel and snp are at same position: ie CA -> C/AA or G -> A,GTC
            if (refAffix.length() <= 1 && !(refAffix.contains("-") || alleleAffix.contains("-"))){
                result.from = reference.substring(0,reference.length());
                result.to = s.substring(0,s.length());
                result.startPosition = allelePos;
            } else {
                //get new indel with goby
                indel = new ObservedIndel(allelePos, refAffix, alleleAffix, referenceIndex);
                result = equivalentIndelRegionCalculator.determine(referenceIndex, indel);
                numIndelsEncountered++;
            }
            String refBase;
            String trueAlleleWithRef;
            String trueFromWithRef;
            if (result.flankLeft!=null){
//                //realigne indel case:
//                refBase = result.flankLeft.substring(result.flankLeft.length()-1);
//                trueAlleleWithRef = refBase + result.to + result.flankRight.substring(0,1);
//                trueFromWithRef = refBase + result.from;
                refBase = result.flankLeft.substring(result.flankLeft.length()-1);
                trueAlleleWithRef = result.toInContext();
                trueFromWithRef = result.fromInContext();
            } else {
                //snp case, no need to prepend reference base.
                refBase = result.from;
                trueAlleleWithRef = result.to;
                trueFromWithRef = result.from;
            }

            if (equivVariants.containsKey(result.startPosition)) {
                if (!equivVariants.get(result.startPosition).reference.equals(trueFromWithRef)) {
                    fromMismatch.warn(LOG,
                        "\nrealigning from VCF: " + this
                        + "\n" + "current allele observedIndel: " + ((indel==null)?"NA, snp encountered":indel)
                        + "\n" + "trying to add EquivalentIndel: " + result
                        + "\n" + "already in equivalent map: " + equivVariants.get(result.startPosition)
                        + "\n" + "Goby realigner produced two different from fields at the same start position\n");
                    numFromMistmaches++;
                }
                equivVariants.get(result.startPosition).trueAlleles.add(trueAlleleWithRef);
            } else {
                Set<String> trueAlleles = new ObjectArraySet<>();
                //start out with assumption that reference is a true allele
                trueAlleles.add(refBase);
                trueAlleles.add(trueAlleleWithRef);
                equivVariants.put(result.startPosition, new Variant(trueFromWithRef, trueAlleles, result.startPosition, referenceIndex));
            }
            //if we have moved ploidy number of alleles into a position, remove its reference
            if (equivVariants.get(result.startPosition).trueAlleles.size() > this.trueAlleles.size()) {
                equivVariants.get(result.startPosition).trueAlleles.remove(refBase);
            }
        }
        return equivVariants;
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



    private int getMaxLen(){
        int maxLen = 0;
        for (String allele : trueAlleles){
            maxLen = Math.max(maxLen,allele.length());
        }
        maxLen = Math.max(maxLen,reference.length());
        return maxLen;

    }


    @Override
    public String toString() {
        return "Variant{" +
                "reference='" + reference + '\'' +
                ", trueAlleles=" + trueAlleles +
                ", isIndel=" + isIndel +
                ", position=" + position +
                ", referenceIndex=" + referenceIndex +
                ", maxLen=" + maxLen +
                '}';
    }
}
