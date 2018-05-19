package org.campagnelab.goby.alignments.processors;

import edu.cornell.med.icb.identifier.IndexedIdentifier;
import it.unimi.dsi.util.XorShift1024StarRandom;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.ConcatSortedAlignmentReader;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.io.IOException;

/**
 * Processor to downsample input reads. Will return only a random subset of input reads, with probability keepRate.
 * For instance, keepRate=0.1 will return about 10% of aligned reads.
 * Created by fac2003 on 5/19/18.
 */
public class DownsamplerProcessor implements AlignmentProcessorInterface {
    ConcatSortedAlignmentReader sortedReaders;
    double keepRate = 1;
    int processedCount = 0;
    int modifiedCount = 0;

    XorShift1024StarRandom random = new XorShift1024StarRandom();

    public DownsamplerProcessor(ConcatSortedAlignmentReader sortedReaders, double keepRate) {
        this.sortedReaders = sortedReaders;
        this.keepRate = keepRate;
    }

    @Override
    public Alignments.AlignmentEntry nextRealignedEntry(int targetIndex, int position) throws IOException {
        for (; ; ) {
            // keep trying until we can return an entry:
            Alignments.AlignmentEntry nextEntry = sortedReaders.skipTo(targetIndex, position);
            if (random.nextFloat() < keepRate) {
                processedCount++;
                return nextEntry;
            } else {
                modifiedCount++;
            }
        }
    }

    @Override
    public void setGenome(RandomAccessSequenceInterface genome, IndexedIdentifier targetIdentifiers) {
        // genome not needed for downsampling.
    }

    @Override
    public int getModifiedCount() {
        return modifiedCount;
    }

    @Override
    public int getProcessedCount() {
        return processedCount;
    }
}
