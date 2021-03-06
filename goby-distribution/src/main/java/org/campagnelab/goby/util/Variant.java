package org.campagnelab.goby.util;


import java.io.Serializable;
import java.util.Set;


/**
 * This class stores the information from a VCF pertinent to one variant.
 * Will be stored in a map for use by AddTrueGenotypes.
 */
public class Variant implements Serializable {
    public String referenceBase;
    public Set<FromTo> trueAlleles;
    public int position;
    public int referenceIndex;
    int maxLen;
    public static int numFromMistmaches = 0;
    public static int numIndelsEncountered = 0;

    public static final long serialVersionUID = -8298131465187158713L;

    /**
     * Merge another variant with this one.
     *
     * @param reVar
     */
    public void merge(Variant reVar) {
        assert referenceBase.equals(reVar.referenceBase) : String.format("reference base must match for correct merging. %s !=%s Position=%d",
                referenceBase, reVar.referenceBase, position);
        assert position == reVar.position : String.format("position must match for correct merging. %d != %d", position);
        assert referenceIndex == reVar.referenceIndex : "referenceIndex must match for correct merging.";
        //remove a ref allele from map's variant, since we are are replacing it with a variant.
        for (FromTo trueAllele : trueAlleles) {
            if (trueAllele.getTo().equals(trueAllele.getFrom())) {
                trueAlleles.remove(trueAllele);
                break;
            }
        }
        for (FromTo addingAllele : reVar.trueAlleles) {
            if (!addingAllele.getTo().equals(addingAllele.getFrom())) {
                trueAlleles.add(addingAllele);
            }
        }
        maxLen = getMaxLen();

    }

    /**
     * @param referenceBase  refbase corresponding to base in genome at position
     * @param alleles        set of alleles containing a from and to field.
     * @param position       zero-indexed position on genome
     * @param referenceIndex
     */
    public Variant(char referenceBase, Set<FromTo> alleles, int position, int referenceIndex) {
        this.referenceBase = Character.toString(referenceBase);
        this.trueAlleles = alleles;
        maxLen = getMaxLen();
        this.position = position;
        this.referenceIndex = referenceIndex;
    }

    /**
     * Returns true iff the set of alleles contains an indel.
     *
     * @return True or False.
     */
    public boolean isIndel() {
        for (FromTo allele : trueAlleles) {
            if (allele.isIndel()) return true;
        }
        return false;
    }

    /**
     * Returns true iff the set of alleles contains a single nucleotide polymophism.
     *
     * @return True or False.
     */
    public boolean isSNP() {
        for (FromTo allele : trueAlleles) {
            if (allele.isSnp()) return true;
        }
        return false;
    }

    /**
     * Return True if the genomic site represented is a no call.
     *
     * @return True or False
     */
    public boolean isNoCall() {
        return trueAlleles.size() == 0;
    }

    /**
     * Return True if the genomic site represented by this variant has homozygous alleles.
     *
     * @return True or False
     */
    public boolean isHomozygous() {
        return trueAlleles.size() == 1;
    }

    /**
     * Return True if the genomic site represented by this variant has heterozygous alleles.
     *
     * @return True or False
     */
    public boolean isHeterozygous() {
        return !isNoCall() && !isHomozygous();
    }

    static String pad(int maxLen, String s) {
        StringBuffer pad = new StringBuffer(s);
        for (int i = 1; i <= maxLen; i++) {
            if (pad.length() < maxLen) {
                pad.append("-");
            }
        }
        return pad.toString();
    }


    private int getMaxLen() {
        int maxLen = 0;
        for (FromTo allele : trueAlleles) {
            maxLen = Math.max(maxLen, allele.maxLen());
        }
        return maxLen;

    }


    @Override
    public String toString() {
        return "Variant{" +
                "reference='" + referenceBase + '\'' +
                ", trueAlleles=" + trueAlleles +
                ", isIndel=" + isIndel() +
                ", isSNP=" + isSNP() +
                ", position=" + position +
                ", referenceIndex=" + referenceIndex +
                ", maxLen=" + maxLen +
                '}';
    }

    /**
     * Helper class to keep from/to together for a variation.
     */
    static public class FromTo implements Serializable {
        public static final long serialVersionUID = -894836569024658713L;
        public int sampleIndex;
        String from;
        String to;
        String originalFrom;
        String originalTo;

        public FromTo(String from, String to) {
            this(from, to, -1);
        }

        /**
         * The constructor normalizes variations such that
         * a mix of SNP and indel which may look like this: G--CCCC to C because another indel is G--CCCC to GCCCCCC
         * will become G--CCCC to C--CCCC.
         *
         * @param from
         * @param to
         * @param sampleIndex
         */
        public FromTo(String from, String to, int sampleIndex) {
            this.originalFrom = from;
            this.originalTo = to;
            this.from = from;
            this.to = to;
            this.sampleIndex = sampleIndex;
            if (to.length() < from.length()) {
                // extend to up to the length of from:
                this.to += from.substring(to.length(), from.length());
            }
            if (from.length() < to.length()) {
                // extend from up to the length of to:
                this.from += to.substring(from.length(), to.length());
            }
        }


        public void makeUpperCase() {
            from = from.toUpperCase();
            to = to.toUpperCase();
        }

        public boolean isRef() {
            return from.equals(to);
        }

        public boolean isSnp() {
            return (from.length() == 1 && to.length() == 1 && !isRef());
        }

        public boolean isIndel() {
            return (from.length() != to.length()) || from.contains("-") || to.contains("-");
        }


        public int maxLen() {
            return Math.max(from.length(), to.length());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!FromTo.class.isAssignableFrom(obj.getClass())) {
                return false;
            }
            final FromTo other = (FromTo) obj;
            return from.equals(other.from) && to.equals(other.to) && sampleIndex == other.sampleIndex;
        }

        @Override
        public int hashCode() {
            return from.hashCode() ^ to.hashCode();
        }

        @Override
        public String toString() {
            return "from:" + from + " to:" + to;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public void append(String substring) {
            from += substring;
            to += substring;
        }

        public String getOriginalFrom() {
            return originalFrom;
        }

        public String getOriginalTo() {
            return originalTo;
        }
    }

    /**
     * VCFToGobyFormatFromTo converts a vcf format indel ("GATC -> GA") to a goby format indel ("TC -> --").
     * Goby formatted indels are needed when making ObservedIndel objects, such as for the equivalent indel calculator
     * or for for the realigner.
     */
    public static class GobyIndelFromVCF {

        private FromTo gobyFromTo;
        private int allelePos;


        public FromTo getGobyFromTo() {
            return gobyFromTo;
        }


        public int getAllelePos() {
            return allelePos;
        }

        public GobyIndelFromVCF(FromTo vcfFromTo, int gobyPosOfRefBase) {
            int maxLenRefThisAllele = Math.max(vcfFromTo.from.length(), vcfFromTo.to.length());
            String fromAffix = pad(maxLenRefThisAllele, vcfFromTo.from);
            String toAffix = pad(maxLenRefThisAllele, vcfFromTo.to);

            //we are going to clip left flank and incrememt position for each clip, but gobyPos should point to pos before first "-",
            //we subtract 1 to reflect this.
            allelePos = gobyPosOfRefBase - 1;

            //2/10/2017: we also need to increment snp position here, because the above wrongly deincriments them.
            if (toAffix.substring(1).equals(fromAffix.substring(1))) {
                allelePos++;
            }

            int diffStart;
            //clip the start further if it is the same.
            for (diffStart = 0; diffStart < fromAffix.length(); diffStart++) {
                if (fromAffix.charAt(diffStart) != toAffix.charAt(diffStart)) {
                    fromAffix = fromAffix.substring(diffStart);
                    toAffix = toAffix.substring(diffStart);
                    allelePos += diffStart;
                    break;
                }
            }

            for (diffStart = fromAffix.length() - 1; diffStart >= 0; diffStart--) {
                if (fromAffix.charAt(diffStart) != toAffix.charAt(diffStart)) {
                    fromAffix = fromAffix.substring(0, diffStart + 1);
                    toAffix = toAffix.substring(0, diffStart + 1);
                    break;
                }
            }

            gobyFromTo = new FromTo(fromAffix, toAffix);


        }


    }
}
