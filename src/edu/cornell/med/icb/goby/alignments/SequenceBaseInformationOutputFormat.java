package edu.cornell.med.icb.goby.alignments;


import edu.cornell.med.icb.goby.baseinfo.SequenceBaseInformationWriter;
import edu.cornell.med.icb.goby.modes.DiscoverSequenceVariantsMode;
import edu.cornell.med.icb.goby.modes.SequenceVariationOutputFormat;
import edu.cornell.med.icb.goby.reads.RandomAccessSequenceInterface;
import edu.cornell.med.icb.goby.util.OutputInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Created by Remi Torracinta on 5/16/16 on the goby2 parquet-proto-output branch.
 * Moved to Goby3 master on August 28 2016.
 */


/**
 * Example console input
 * -m
 * discover-sequence-variants
 * /Users/rct66/data/cfs/TILJHQN-MHFC-MHFC-85-CTL-A24-55_BCell.header
 * /Users/rct66/data/cfs/TSYEAZS-MHFC-MHFC-85-CTL-A24-55_NKCell.header
 * --format
 * SEQUENCE_BASE_INFO
 * -o
 * /Users/rct66/data/cfs/genotypes_proto
 * --genome
 * human_g1k_v37
 */


public class SequenceBaseInformationOutputFormat implements SequenceVariationOutputFormat {
    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;
    //update this as new features are included

    static private Logger LOG = LoggerFactory.getLogger(SequenceBaseInformationOutputFormat.class);
    //ProgressLogger pgReadWrite;

    private SequenceBaseInformationWriter parquetWriter;

    public void defineColumns(OutputInfo statsWriter, DiscoverSequenceVariantsMode mode) {

        try {
            parquetWriter = new SequenceBaseInformationWriter(statsWriter.getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
    }

    public void writeRecord(DiscoverVariantIterateSortedAlignments iterator, SampleCountInfo[] sampleCounts,
                            int referenceIndex, int position, DiscoverVariantPositionData list, int groupIndexA, int groupIndexB) {
       // pgReadWrite.update();
        //if (minCountsFilter(sampleCounts)) return;
        int maxGenotypeIndex=0;
        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];

            maxGenotypeIndex=Math.max(sampleCountInfo.getGenotypeMaxIndex(), maxGenotypeIndex);
        }

        IntArrayList[][][] qualityScores = new IntArrayList[2][maxGenotypeIndex][2];
        IntArrayList[][][] readIdxs = new IntArrayList[2][maxGenotypeIndex][2];

        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];
            final int genotypeMaxIndex = sampleCountInfo.getGenotypeMaxIndex();

            for (int genotypeIndex = 0; genotypeIndex < genotypeMaxIndex; genotypeIndex++) {
                for (int k = 0; k < 2; k++) {
                    qualityScores[sampleIndex][genotypeIndex][k] = new IntArrayList();
                    readIdxs[sampleIndex][genotypeIndex][k] = new IntArrayList();
                }
            }
        }
        for (PositionBaseInfo baseInfo : list) {
            int baseInd = sampleCounts[0].baseIndex(baseInfo.to);
            int sampleInd = baseInfo.readerIndex;
            int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
            qualityScores[sampleInd][baseInd][strandInd].add(baseInfo.qualityScore & 0xFF);
            //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
            readIdxs[sampleInd][baseInd][strandInd].add(baseInfo.readIndex);
        }


        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();
        builder.setMutated(false);
        builder.setPosition(position);
        if (list.size() > 0) {
            builder.setReferenceBase(Character.toString(list.getReferenceBase()));
        }
        builder.setReferenceIndex(referenceIndex);

        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            BaseInformationRecords.SampleInfo.Builder sampleBuilder = BaseInformationRecords.SampleInfo.newBuilder();
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];

            for (int genotypeIndex = 0; genotypeIndex < sampleCountInfo.getGenotypeMaxIndex(); genotypeIndex++) {
                BaseInformationRecords.CountInfo.Builder infoBuilder = BaseInformationRecords.CountInfo.newBuilder();
                infoBuilder.setFromSequence(sampleCountInfo.getReferenceGenotype());
                infoBuilder.setToSequence(sampleCountInfo.getGenotypeString(genotypeIndex));
                infoBuilder.setMatchesReference(sampleCountInfo.isReferenceGenotype(genotypeIndex));
                infoBuilder.setGenotypeCountForwardStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, true));
                infoBuilder.setGenotypeCountReverseStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, false));

                infoBuilder.addAllQualityScoresForwardStrand(ProtoPredictor.compressFreq(qualityScores[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllQualityScoresReverseStrand(ProtoPredictor.compressFreq(qualityScores[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));
                infoBuilder.addAllReadIndicesForwardStrand(ProtoPredictor.compressFreq(readIdxs[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllReadIndicesReverseStrand(ProtoPredictor.compressFreq(readIdxs[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));
                infoBuilder.setIsIndel(sampleCountInfo.isIndel(genotypeIndex));
                sampleBuilder.addCounts(infoBuilder.build());
            }
            sampleBuilder.setFormattedCounts(sampleCounts[sampleIndex].toString());
            builder.addSamples(sampleBuilder.build());
        }
        try {
            parquetWriter.appendEntry(builder.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private boolean minCountsFilter(SampleCountInfo[] sampleCounts) {
        for (int i = 0; i < 2; i++) {
            if (sampleCounts[i].getSumCounts() < 10) return true;
        }
        return false;
    }

    public void close() {
    //    pgReadWrite.stop();
        try {
            parquetWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setGenome(RandomAccessSequenceInterface genome) {

    }

    public void setGenomeReferenceIndex(int index) {

    }
}
