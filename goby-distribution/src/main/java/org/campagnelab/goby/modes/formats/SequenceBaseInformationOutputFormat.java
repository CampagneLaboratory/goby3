package org.campagnelab.goby.modes.formats;


import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.baseinfo.SequenceBaseInformationWriter;
import org.campagnelab.goby.modes.DiscoverSequenceVariantsMode;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.modes.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.modes.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.OutputInfo;
import org.campagnelab.goby.util.dynoptions.DynamicOptionClient;
import org.campagnelab.goby.util.dynoptions.RegisterThis;
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

    public static final DynamicOptionClient doc() {
        return doc;
    }
    @RegisterThis
    public static final DynamicOptionClient doc = new DynamicOptionClient(SequenceBaseInformationOutputFormat.class,
            "sampling-rate:float, ratio of sites to write to output. The default writes all sites. Use 0.1 to sample 10% of sites.:1.0",
            "random-seed:long, random seed used for sampling sites.:2390239"
    );

    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;
    //update this as new features are included

    static private Logger LOG = LoggerFactory.getLogger(SequenceBaseInformationOutputFormat.class);
    //ProgressLogger pgReadWrite;

    private SequenceBaseInformationWriter sbiWriter;


    private float samplingRate;
    XoRoShiRo128PlusRandom randomGenerator;

    public void defineColumns(OutputInfo statsWriter, DiscoverSequenceVariantsMode mode) {
        samplingRate = doc.getFloat("sampling-rate");
        int seed = doc.getInteger("random-seed");
        randomGenerator = new XoRoShiRo128PlusRandom(seed);
        try {
            sbiWriter = new SequenceBaseInformationWriter(statsWriter.getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void allocateStorage(int numberOfSamples, int numberOfGroups) {
    }

    private MutableString genomicContext = new MutableString();

    public void writeRecord(DiscoverVariantIterateSortedAlignments iterator, SampleCountInfo[] sampleCounts,
                            int referenceIndex, int position, DiscoverVariantPositionData list, int groupIndexA, int groupIndexB) {
        int numSamples = sampleCounts.length;
        if (samplingRate < 1.0) {
            if (randomGenerator.nextFloat() > samplingRate) {
                // do not process the site.
                return;
            }
        }

        // pgReadWrite.update();
        //if (minCountsFilter(sampleCounts)) return;
        int maxGenotypeIndex = 0;
        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];

            maxGenotypeIndex = Math.max(sampleCountInfo.getGenotypeMaxIndex(), maxGenotypeIndex);
        }


        IntArrayList[][][] qualityScores = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][][] readMappingQuality = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][][] readIdxs = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][] numVariationsInReads = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] insertSizes = new IntArrayList[numSamples][maxGenotypeIndex];

        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];
            final int genotypeMaxIndex = sampleCountInfo.getGenotypeMaxIndex();

            for (int genotypeIndex = 0; genotypeIndex < genotypeMaxIndex; genotypeIndex++) {
                for (int k = 0; k < 2; k++) {
                    qualityScores[sampleIndex][genotypeIndex][k] = new IntArrayList();
                    readMappingQuality[sampleIndex][genotypeIndex][k] = new IntArrayList();
                    readIdxs[sampleIndex][genotypeIndex][k] = new IntArrayList();
                    numVariationsInReads[sampleIndex][genotypeIndex] = new IntArrayList();
                    insertSizes[sampleIndex][genotypeIndex] = new IntArrayList();
                }
            }
        }
        for (PositionBaseInfo baseInfo : list) {
            int baseInd = sampleCounts[0].baseIndex(baseInfo.to);
            int sampleInd = baseInfo.readerIndex;
            int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
            qualityScores[sampleInd][baseInd][strandInd].add(baseInfo.qualityScore & 0xFF);
            readMappingQuality[sampleInd][baseInd][strandInd].add(baseInfo.readMappingQuality & 0xFF);
            numVariationsInReads[sampleInd][baseInd].add(baseInfo.numVariationsInRead);
            insertSizes[sampleInd][baseInd].add(baseInfo.insertSize);
            //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
            readIdxs[sampleInd][baseInd][strandInd].add(baseInfo.readIndex);
        }
        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();
        builder.setMutated(false);
        builder.setPosition(position);
        builder.setReferenceId(iterator.getReferenceId(referenceIndex).toString());
        // store 10 bases of genomic context around the site:
        genomicContext.setLength(0);
        RandomAccessSequenceInterface genome = iterator.getGenome();
        int referenceSequenceLength = iterator.getGenome().getLength(referenceIndex);
        for (int refPos = Math.max(position - 10, 0); refPos < Math.min(position + 11, referenceSequenceLength); refPos++) {
            genomicContext.append(genome.get(referenceIndex, refPos));
        }
        builder.setGenomicSequenceContext(genomicContext.toString());

        if (list.size() > 0) {
            builder.setReferenceBase(Character.toString(list.getReferenceBase()));
        }
        builder.setReferenceIndex(referenceIndex);

        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
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

                infoBuilder.addAllReadMappingQualityForwardStrand(ProtoPredictor.compressFreq(readMappingQuality[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllReadMappingQualityReverseStrand(ProtoPredictor.compressFreq(readMappingQuality[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));

                infoBuilder.addAllNumVariationsInReads(ProtoPredictor.compressFreq(numVariationsInReads[sampleIndex][genotypeIndex]));
                infoBuilder.addAllInsertSizes(ProtoPredictor.compressFreq(insertSizes[sampleIndex][genotypeIndex]));

                infoBuilder.setIsIndel(sampleCountInfo.isIndel(genotypeIndex));
                sampleBuilder.addCounts(infoBuilder.build());
            }
            sampleBuilder.setFormattedCounts(sampleCounts[sampleIndex].toString());
            builder.addSamples(sampleBuilder.build());
        }
        try {
            sbiWriter.appendEntry(builder.build());
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
            sbiWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setGenome(RandomAccessSequenceInterface genome) {

    }

    public void setGenomeReferenceIndex(int index) {

    }
}
