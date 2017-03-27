package org.campagnelab.goby.modes.dsv;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.PositionBaseInfo;

import javax.swing.text.Position;
import java.util.Arrays;

/**
 * Remove bases who genotype match the 1-base flanking prefix of an indel. This prevents double counting on the first
 * base (e.g., counting a C base as well as a C--A indel).
 * This filter must always been used when training neural networks. It prevents double counting.
 * Created by fac2003 on 2/14/17.
 */
public class RemoveBasesMatchingIndelsGenotypeFilter extends GenotypeFilter {

    public void initStorage(int numSamples) {
        super.initStorage(numSamples);

    }

    ObjectSet<Alignments.AlignmentEntry> indelSupportingEntries = new ObjectOpenHashSet<>();

    @Override
    public void filterGenotypes(DiscoverVariantPositionData list, SampleCountInfo[] sampleCounts, ObjectSet<PositionBaseInfo> filteredSet) {
// determine the set of all entries supporting indels:
        indelSupportingEntries.clear();
        for (SampleCountInfo sampleCount : sampleCounts) {
            if (sampleCount.getEquivalentIndelRegions() != null)
                for (EquivalentIndelRegion indel : sampleCount.getEquivalentIndelRegions()) {
                    indelSupportingEntries.addAll(indel.supportingEntries);
                }
        }
        if (indelSupportingEntries.size() == 0) {
            return;
        }
        int removed = 0;
        // we now remove any base also supported by the entry:
        for (PositionBaseInfo info : list) {
            if (indelSupportingEntries.contains(info.alignmentEntry)) {

                // indels have a quality score  of zero but should not be removed at this stage.
                final SampleCountInfo sampleCountInfo = sampleCounts[info.readerIndex];
                final int baseIndex = sampleCountInfo.baseIndex(info.to);

                sampleCountInfo.suggestRemovingGenotype(baseIndex, info.matchesForwardStrand);
                removeGenotype(info, filteredSet);
                removed++;
            }
            if (removed >= indelSupportingEntries.size()){
                break;
            }
        }
        //    System.out.println("122we33 Removed " + removed);
    }

    @Override
    public int getThresholdForSample(int sampleIndex) {
        return 0;
    }
}
