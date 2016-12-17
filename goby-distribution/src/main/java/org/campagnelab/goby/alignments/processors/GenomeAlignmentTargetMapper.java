package org.campagnelab.goby.alignments.processors;

import edu.cornell.med.icb.identifier.DoubleIndexedIdentifier;
import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

/**
 * A utility to easily convert target indices between genome and alignment.
 * Created by fac2003 on 12/17/16.
 */
public class GenomeAlignmentTargetMapper {
    private final IndexedIdentifier targetIdentifiers;
    private final DoubleIndexedIdentifier reverse;
    private RandomAccessSequenceInterface genome;
    private int[] genomeToAlignment;
    private int[] alignmentToGenome;

    public GenomeAlignmentTargetMapper(IndexedIdentifier targetIdentifiers, RandomAccessSequenceInterface genome) {
        this.genome = genome;
        this.targetIdentifiers = targetIdentifiers;

        alignmentToGenome = new int[targetIdentifiers.size()];
        reverse = new DoubleIndexedIdentifier(targetIdentifiers);
        for (int alignmentTargetIndex = 0; alignmentTargetIndex < reverse.size(); alignmentTargetIndex++) {
            MutableString id = reverse.getId(alignmentTargetIndex);
            alignmentToGenome[alignmentTargetIndex] = genome.getReferenceIndex(id.toString());
        }
        genomeToAlignment = new int[genome.size()];
        MutableString targetId = new MutableString();
        for (int genomeTargetIndex = 0; genomeTargetIndex < genome.size(); genomeTargetIndex++) {
            String id = genome.getReferenceName(genomeTargetIndex);
            targetId.setLength(0);
            targetId.append(id);
            genomeToAlignment[genomeTargetIndex] = targetIdentifiers.getOrDefault(targetId, -1);
        }


        //      System.out.println("BREAKPOINT");
    }

    int toGenome(int alignmentTargetIndex) {
        return alignmentToGenome[alignmentTargetIndex];
    }

    int toAlignment(int genomeTargetIndex) {
        return genomeToAlignment[genomeTargetIndex];
    }

    public String getAlignmentId(int targetIndex) {
        return reverse.getId(targetIndex).toString();
    }
}
