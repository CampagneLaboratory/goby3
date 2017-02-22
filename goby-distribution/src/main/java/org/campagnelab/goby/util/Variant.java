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
    public String referenceBase;
    public Set<FromTo> trueAlleles;
    public boolean isIndel;
    public int position;
    public int referenceIndex;
    int maxLen;
    private static final Logger LOG = LoggerFactory.getLogger(Variant.class);
    static WarningCounter fromMismatch = new WarningCounter(10);
    public static int numFromMistmaches = 0;
    public static int numIndelsEncountered = 0;

   public static final long serialVersionUID = -8298131465187158713L;

    /**
     * @param referenceBase refbase corresponding to base in genome at position
     * @param alleles set of alleles containing a from and to field.
     * @param position zero-indexed position on genome
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

    Map<Integer,Variant> realign(EquivalentIndelRegionCalculator equivalentIndelRegionCalculator) {

        Map<Integer,Variant> equivVariants = new Int2ObjectArrayMap<Variant>(trueAlleles.size());

        //handle varas with only snps or ref
        if (maxLen == 1) {
            equivVariants.put(position,this);
            return equivVariants;
        }
        for (FromTo s : trueAlleles) {
            //ignore ref alleles, they are not written to new Varset.
            if (s.isRef()) {
                continue;
            }
            int maxLenRefThisAllele = Math.max(s.from.length(), s.to.length());
            String fromAffix = pad(maxLenRefThisAllele, s.from);
            String toAffix = pad(maxLenRefThisAllele, s.to);

            //we are going to clip left flank and incrememt position for each clip, but gobyPos should point to pos before first "-",
            //we subtract 1 to reflect this.
            int allelePos = position-1;

            //2/10/2017: we also need to increment snp position here, because the above wrongly deincriments them.
            if (toAffix.length()==1 && fromAffix.length()==1){
                allelePos++;
            }

            int diffStart;
            //clip the start further if it is the same.
            for (diffStart = 0; diffStart < fromAffix.length(); diffStart++){
                if (fromAffix.charAt(diffStart)!=toAffix.charAt(diffStart)){
                    fromAffix = fromAffix.substring(diffStart);
                    toAffix = toAffix.substring(diffStart);
                    allelePos += diffStart;
                    break;
                }
            }

            for (diffStart = fromAffix.length()-1; diffStart >= 0; diffStart--){
                if (fromAffix.charAt(diffStart)!=toAffix.charAt(diffStart)){
                    fromAffix = fromAffix.substring(0,diffStart+1);
                    toAffix = toAffix.substring(0,diffStart+1);
                    break;
                }
            }


            EquivalentIndelRegion result = new EquivalentIndelRegion();

            ObservedIndel indel = null;

                //very rare case where indel and snp are at same position: ie CA -> C/AA or G -> A,GTC
                if (fromAffix.length() <= 1 && !(fromAffix.contains("-") || toAffix.contains("-"))){
                    result.from = referenceBase;
                    result.to = toAffix.substring(0,1);
                    result.startPosition = allelePos;
                } else {
                    //get new indel with goby
                indel = new ObservedIndel(allelePos, fromAffix, toAffix, referenceIndex);
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
            FromTo realigned = new FromTo(trueFromWithRef,trueAlleleWithRef);
            FromTo reference = new FromTo(refBase,refBase);

            if (equivVariants.containsKey(result.startPosition)) {
                equivVariants.get(result.startPosition).trueAlleles.add(realigned);
            } else {
                Set<FromTo> trueAlleles = new ObjectArraySet<>();
                //start out with assumption that reference is a true allele
                trueAlleles.add(reference);
                trueAlleles.add(realigned);
                equivVariants.put(result.startPosition, new Variant(referenceBase.charAt(0), trueAlleles, result.startPosition, referenceIndex));
            }
            //if we have moved ploidy number of alleles into a position, remove its reference
            if (equivVariants.get(result.startPosition).trueAlleles.size() > this.trueAlleles.size()) {
                equivVariants.get(result.startPosition).trueAlleles.remove(reference);
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
        for (FromTo allele : trueAlleles){
            maxLen = Math.max(maxLen,allele.maxLen());
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

        private String from;
        private String to;

        public FromTo(String from, String to){
            this.from = from;
            this.to = to;
        }


        public void makeUpperCase(){
            from = from.toUpperCase();
            to = to.toUpperCase();
        }

        public boolean isRef(){
            return from.equals(to);
        }

        public boolean isSnp(){
            return (from.length() == 1 && to.length() == 1 && !isRef());
        }

        public int maxLen(){
            return Math.max(from.length(), to.length());
        }

        @Override
        public boolean equals(Object obj){
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
        public String toString(){
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
