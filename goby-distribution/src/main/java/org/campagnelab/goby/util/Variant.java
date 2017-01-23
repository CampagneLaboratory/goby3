package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.campagnelab.goby.modes.VCFToGenotypeMapMode;
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
    private static final Logger LOG = LoggerFactory.getLogger(VCFToGenotypeMapMode.class);
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
            //create indel strings without vcf first base
            String refAffix = pad(maxLen, reference).substring(1);
            String alleleAffix = pad(maxLen, s).substring(1);


            EquivalentIndelRegion result = new EquivalentIndelRegion();
            ObservedIndel indel = null;

            //very rare case where indel and snp are at same position: ie CA -> C/AA
            if (refAffix.equals(alleleAffix)){
                result.from = reference.substring(0,reference.length()-1);
                result.to = s.substring(0,s.length()-1);
                result.startPosition = position;
            } else {
                //get new indel with goby
                indel = new ObservedIndel(position, position + Math.max(refAffix.length(), alleleAffix.length()), refAffix, alleleAffix);
                result = equivalentIndelRegionCalculator.determine(referenceIndex, indel);
                numIndelsEncountered++;
            }
            if (equivVariants.containsKey(result.startPosition)) {
                if (!equivVariants.get(result.startPosition).reference.equals(result.from)) {
                    fromMismatch.warn(LOG,
                        "\nrealigning from VCF: " + this
                        + "\n" + "current allele observedIndel: " + ((indel==null)?"NA, snp encountered":indel)
                        + "\n" + "trying to add EquivalentIndel: " + result
                        + "\n" + "already in equivalent map: " + equivVariants.get(result.startPosition)
                        + "\n" + "Goby realigner produced two different from fields at the same start position\n");
                    numFromMistmaches++;
                }
                equivVariants.get(result.startPosition).trueAlleles.add(result.to);
            } else {
                Set<String> trueAllele = new ObjectArraySet<>();
                //start out with assumption that reference is a true allele
                trueAllele.add(result.from);
                trueAllele.add(result.to);
                equivVariants.put(result.startPosition, new Variant(result.from, trueAllele, result.startPosition, referenceIndex));
            }
            //if we have moved ploidy number of alleles into a position, remove its reference
            if (equivVariants.get(result.startPosition).trueAlleles.size() > this.trueAlleles.size()) {
                equivVariants.get(result.startPosition).trueAlleles.remove(result.from);
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
