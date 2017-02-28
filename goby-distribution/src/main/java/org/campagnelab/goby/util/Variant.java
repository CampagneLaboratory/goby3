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
    public boolean isIndel;
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
        assert referenceBase == reVar.referenceBase : "reference base must match for correct merging.";
        assert position == reVar.position : "position must match for correct merging.";
        assert referenceIndex == reVar.referenceIndex : "referenceIndex must match for correct merging.";
        //remove a ref allele from map's variant, since we are are replacing it with a variant.
        for (FromTo trueAllele : trueAlleles){
            if (trueAllele.getTo().equals(trueAllele.getFrom())){
                trueAlleles.remove(trueAllele);
                break;
            }
        }
        for (FromTo addingAllele:reVar.trueAlleles){
            if (!addingAllele.getTo().equals(addingAllele.getFrom())){
                trueAlleles.add(addingAllele);
            }
        }
        maxLen = getMaxLen();
        this.isIndel = (maxLen > 1);
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
        this.isIndel = (maxLen > 1);
        this.position = position;
        this.referenceIndex = referenceIndex;
    }


    String pad(int maxLen, String s) {
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
                ", isIndel=" + isIndel +
                ", position=" + position +
                ", referenceIndex=" + referenceIndex +
                ", maxLen=" + maxLen +
                '}';
    }


    static public class FromTo implements Serializable {

        String from;
        String to;

        public FromTo(String from, String to) {
            this.from = from;
            this.to = to;
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
            return from.equals(other.from) && to.equals(other.to);
        }

        @Override
        public int hashCode() {
            return from.hashCode()^to.hashCode();
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
    }
}
