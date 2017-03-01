package org.campagnelab.goby.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Created by rct66 on 2/22/17.
 */
public class VariantMapCreator extends VariantMapHelper {

    private EquivalentIndelRegionCalculator equivalentIndelRegionCalculator;
    protected RandomAccessSequenceInterface genome;
    static WarningCounter overLappingIndels = new WarningCounter(10);

    /**
     * Generate a new empty variant map
     */
    public VariantMapCreator(RandomAccessSequenceInterface genome) {
        chMap = new Object2ObjectOpenHashMap<String, Int2ObjectMap<Variant>>(40);
        this.genome = genome;
        this.equivalentIndelRegionCalculator = new EquivalentIndelRegionCalculator(genome);
        this.equivalentIndelRegionCalculator.setFlankLeftSize(1); // VCF output requires one base before the indel
        this.equivalentIndelRegionCalculator.setFlankRightSize(0);

    }


    /**
     * add variant to map with reference and true genotype strings.
     *
     * @param chrom
     * @param gobyPos
     * @param reference
     * @param trueAlleles
     */
    public void addVariant(int gobyPos, String chrom, char reference, Set<Variant.FromTo> trueAlleles){
        //make sure there is a map for this chromosome
        if (!chMap.containsKey(chrom)) {
            chMap.put(chrom, new Int2ObjectArrayMap<Variant>(50000));
        }
        //zero-based positions
        Variant var = new Variant(reference, trueAlleles, gobyPos, genome.getReferenceIndex(chrom));
        Map<Integer, Variant> realignedVars = realign(var, equivalentIndelRegionCalculator);
        for (Variant reVar : realignedVars.values()) {
            Variant previousVariant = chMap.get(chrom).get(reVar.position);
            if (previousVariant!=null) {
                //  TODO merge previous variant with new one and update map.
                previousVariant.merge(reVar);
                overLappingIndels.warn(LOG,
                        "\nmerged variant. in map froms,tos: " +  chMap.get(chrom).get(reVar.position).trueAlleles +
                                "\nintended adding from,to: " +  reVar.trueAlleles + "\nat " + chrom + ":" + reVar.position);
                numOverlaps++;
            } else {
                chMap.get(chrom).put(reVar.position, reVar);
            }
        }
    }


    /**
     * Save current map to specified file.
     *
     * @param pathToMap
     * @throws IOException
     */
    public void saveMap(String pathToMap) throws IOException {
        BinIO.storeObject(chMap, new File(pathToMap));
    }


    /**
     * This method takes a single variant (generated from vcf), realigns its component alleles, and new set of variants mapped to by position.
     * @param variant
     * @param equivalentIndelRegionCalculator
     * @return a map of positions to variants.
     */
    static Map<Integer,Variant> realign(Variant variant, EquivalentIndelRegionCalculator equivalentIndelRegionCalculator) {

        Map<Integer,Variant> equivVariants = new Int2ObjectArrayMap<Variant>(variant.trueAlleles.size());

        //handle varas with only snps or ref
        if (variant.maxLen == 1) {
            equivVariants.put(variant.position, variant);
            return equivVariants;
        }
        for (Variant.FromTo s : variant.trueAlleles) {
            //ignore ref alleles, they are not written to new Varset.
            if (s.isRef()) {
                continue;
            }
            int maxLenRefThisAllele = Math.max(s.from.length(), s.to.length());
            String fromAffix = variant.pad(maxLenRefThisAllele, s.from);
            String toAffix = variant.pad(maxLenRefThisAllele, s.to);

            //we are going to clip left flank and incrememt position for each clip, but gobyPos should point to pos before first "-",
            //we subtract 1 to reflect this.
            int allelePos = variant.position -1;

            //2/10/2017: we also need to increment snp position here, because the above wrongly deincriments them.
            if (toAffix.substring(1).equals(fromAffix.substring(1))){
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
                result.from = variant.referenceBase;
                result.to = toAffix.substring(0,1);
                result.startPosition = allelePos;
            } else {
                //get new indel with goby
                indel = new ObservedIndel(allelePos, fromAffix, toAffix, variant.referenceIndex);
                result = equivalentIndelRegionCalculator.determine(variant.referenceIndex, indel);
                Variant.numIndelsEncountered++;
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
            Variant.FromTo realigned = new Variant.FromTo(trueFromWithRef,trueAlleleWithRef);
            Variant.FromTo reference = new Variant.FromTo(refBase,refBase);

            if (equivVariants.containsKey(result.startPosition)) {
                equivVariants.get(result.startPosition).trueAlleles.add(realigned);
            } else {
                Set<Variant.FromTo> trueAlleles = new ObjectArraySet<>();
                //start out with assumption that reference is a true allele
                trueAlleles.add(reference);
                trueAlleles.add(realigned);
                char referenceBaseResult;
                if (result.flankLeft!=null && result.flankLeft.length()>0){
                    referenceBaseResult = result.flankLeft.charAt(0);
                } else {
                    referenceBaseResult = variant.referenceBase.charAt(0);
                }
                equivVariants.put(result.startPosition, new Variant(referenceBaseResult, trueAlleles, result.startPosition, variant.referenceIndex));            }
            //if we have moved ploidy number of alleles into a position, remove its reference
            if (equivVariants.get(result.startPosition).trueAlleles.size() > variant.trueAlleles.size()) {
                equivVariants.get(result.startPosition).trueAlleles.remove(reference);
            }
        }
        return equivVariants;
    }


}
