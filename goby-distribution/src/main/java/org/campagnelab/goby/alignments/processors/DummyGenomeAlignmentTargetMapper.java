package org.campagnelab.goby.alignments.processors;

import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

/**
 * An identity mapper.
 * Created by fac2003 on 2/20/17.
 */
public class DummyGenomeAlignmentTargetMapper extends GenomeAlignmentTargetMapper {
    public DummyGenomeAlignmentTargetMapper(RandomAccessSequenceInterface genome) {
        super(create(genome), genome);
    }
    private static IndexedIdentifier create( RandomAccessSequenceInterface genome) {
        IndexedIdentifier targetIdentifiers=new IndexedIdentifier();
        for (int i=0;i<genome.size(); i++) {
            targetIdentifiers.registerIdentifier(new MutableString(genome.getReferenceName(i)));
        }
        return targetIdentifiers;
    }
}
