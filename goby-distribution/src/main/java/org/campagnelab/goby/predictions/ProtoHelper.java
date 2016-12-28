package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.BaseToStringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Created by mas2182 on 11/15/16.
 */
public class ProtoHelper {
    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;

    private static final Logger LOG = LoggerFactory.getLogger(ProtoHelper.class);

    private static ProgressLogger baseProgressLogger = new ProgressLogger(LOG);

    static {
        baseProgressLogger.displayLocalSpeed = true;
        baseProgressLogger.start();

    }

    private static String defaultGenomicContext(int contextLength) {

        String defaultGenomicContext = "";
        for (int i = 0; i < contextLength; i++) {
            defaultGenomicContext += "N";
        }

        return defaultGenomicContext;
    }

    static private MutableString genomicContext = new MutableString();

    private static BaseToStringHelper baseConversion = new BaseToStringHelper();

    /**
     * Convert to proto with a 21 bp genomic context length.
     *
     * @param genome
     * @param referenceID
     * @param sampleCounts
     * @param referenceIndex
     * @param position
     * @param list
     * @param sampleToReaderIdxs
     * @return
     */
    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 Integer[] sampleToReaderIdxs) {

        return toProto(genome,
                referenceID,
                sampleCounts,
                referenceIndex, position,
                list,
                sampleToReaderIdxs, 21);
    }

    /**
     * Returns a serialized record of a given position in protobuf format. Required step before mapping to features.
     * Used by SequenceBaseInformationOutputFormat to generate datasets, and SomaticVariationOutputFormat (via mutPrediction) when
     * generating predictions on new examples.
     *
     * @param genome             genome stored in a DiscoverVariantIterateSortedAlignments iterator
     * @param referenceID        name of chromosome, also acquired from an iterator
     * @param sampleCounts       Array of count information objects
     * @param referenceIndex     index corresponding to chromosome
     * @param position           position value of the record in question to serialize
     * @param list               Additional data about the reads
     * @param sampleToReaderIdxs Array which points a required sample (trio:father,mother,somatic pair:germline,somatic) to its reader index
     *                           this index corresponds to the location of data collected by that reader in the SampleCountInfo array
     * @return
     */
    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 Integer[] sampleToReaderIdxs, int contextLength) {


        int numSamples = sampleToReaderIdxs.length;

        // pgReadWrite.update();
        //if (minCountsFilter(sampleCounts)) return;
        int maxGenotypeIndex = 0;
        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleToReaderIdxs[sampleIndex]];

            maxGenotypeIndex = Math.max(sampleCountInfo.getGenotypeMaxIndex(), maxGenotypeIndex);
        }


        IntArrayList[][][] qualityScores = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][][] readMappingQuality = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][][] readIdxs = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][][] numVariationsInPosition = new IntArrayList[numSamples][maxGenotypeIndex][2];

        IntArrayList[][] numVariationsInReads = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] insertSizes = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] targetAlignedLengths = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] queryAlignedLengths = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] pairFlags = new IntArrayList[numSamples][maxGenotypeIndex];


        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            for (int genotypeIndex = 0; genotypeIndex < maxGenotypeIndex; genotypeIndex++) {
                for (int strandIndex = 0; strandIndex < 2; strandIndex++) {
                    qualityScores[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    readMappingQuality[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    readIdxs[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    numVariationsInPosition[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    numVariationsInReads[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    insertSizes[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    targetAlignedLengths[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    queryAlignedLengths[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    pairFlags[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                }
            }
        }

        for (PositionBaseInfo baseInfo : list) {
            int baseIndex = sampleCounts[sampleToReaderIdxs[baseInfo.readerIndex]].baseIndex(baseInfo.to);
            int sampleIndex = java.util.Arrays.asList((sampleToReaderIdxs)).indexOf(baseInfo.readerIndex);
            // check that we need to focus on the sample from which this base originates (if not, ignore the base)
            if (sampleIndex != -1) {
                int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
                qualityScores[sampleIndex][baseIndex][strandInd].add(baseInfo.qualityScore & 0xFF);
                readMappingQuality[sampleIndex][baseIndex][strandInd].add(baseInfo.readMappingQuality & 0xFF);
                numVariationsInReads[sampleIndex][baseIndex].add(baseInfo.numVariationsInRead);
                insertSizes[sampleIndex][baseIndex].add(baseInfo.insertSize);
                baseInfo.alignmentEntry.getTargetAlignedLength();
                //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
                readIdxs[sampleIndex][baseIndex][strandInd].add(baseInfo.readIndex);
                for (Alignments.SequenceVariation var : baseInfo.alignmentEntry.getSequenceVariationsList()) {
                    int delta = baseInfo.readIndex - var.getReadIndex();
                    numVariationsInPosition[sampleIndex][baseIndex][strandInd].add(delta);
                }
                targetAlignedLengths[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getTargetAlignedLength());
                queryAlignedLengths[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getQueryAlignedLength());
                pairFlags[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getPairFlags());
            }
        }
        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();
        builder.setMutated(false);
        builder.setPosition(position);
        builder.setReferenceId(referenceID);
        transferGenomicContext(contextLength, genome, referenceIndex, position, list, builder);


        builder.setReferenceIndex(referenceIndex);
        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            BaseInformationRecords.SampleInfo.Builder sampleBuilder = BaseInformationRecords.SampleInfo.newBuilder();
            //This simply marks the the last sample (consistent with convention) as a tumor sample.
            //No matter the experimental design, we require a single, final "tumor" sample which will be the one
            //the model makes mutation predictions on. Other somatic samples could be included, but the model
            //will use the other samples to make predictions about the last sample.
            if (sampleIndex == numSamples - 1) {
                sampleBuilder.setIsTumor(true);
            }
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleToReaderIdxs[sampleIndex]];
            String referenceGenotype = null;
            final int genotypeMaxIndex = sampleCountInfo.getGenotypeMaxIndex();

            transfer(qualityScores[sampleIndex], readMappingQuality[sampleIndex], readIdxs[sampleIndex], numVariationsInPosition[sampleIndex],
                    numVariationsInReads[sampleIndex], insertSizes[sampleIndex],
                    targetAlignedLengths[sampleIndex], queryAlignedLengths[sampleIndex],
                    pairFlags[sampleIndex],
                    sampleBuilder, sampleCountInfo,
                    referenceGenotype, maxGenotypeIndex);
            sampleBuilder.setFormattedCounts(sampleCounts[sampleToReaderIdxs[sampleIndex]].toString());
            builder.addSamples(sampleBuilder.build());
        }
        baseProgressLogger.update(list.size());
        list.clear();
        return builder.build();
    }

    private static Random random = new Random();

    private static void transferGenomicContext(int contextLength, RandomAccessSequenceInterface genome, int referenceIndex, int position, DiscoverVariantPositionData list, BaseInformationRecords.BaseInformation.Builder builder) {
        // store 10 bases of genomic context around the site:
        {
            genomicContext.setLength(0);
            int referenceSequenceLength = genome.getLength(referenceIndex);
            if (referenceSequenceLength <= 0) {
                builder.setGenomicSequenceContext(defaultGenomicContext(contextLength));
            } else {
                //derive context length
                int cl = (contextLength - 1) / 2;
                final int genomicStart = Math.max(position - cl, 0);
                final int genomicEnd = Math.min(position + (cl + 1), referenceSequenceLength);
                int index = 0;
                for (int refPos = genomicStart; refPos < genomicEnd; refPos++) {
                    genomicContext.insert(index++, baseConversion.convert(genome.get(referenceIndex, refPos)));
                }
                //pad zeros as needed
                for (int i = genomicStart; i < 0; i++) {
                    genomicContext.insert(0, "N");
                }
                index = genomicContext.length();
                for (int i = genomicEnd; i > referenceSequenceLength; i--) {
                    genomicContext.insert(index++, "N");
                }
                builder.setGenomicSequenceContext(contextLength == genomicContext.length() ? genomicContext.toString() : defaultGenomicContext(contextLength));
            }
            if (list.size() > 0) {

                builder.setReferenceBase(baseConversion.convert(list.getReferenceBase()));
            }
        }
    }

    private static void transfer(IntArrayList[][] qualityScore,
                                 IntArrayList[][] intArrayLists,
                                 IntArrayList[][] readIdx,
                                 IntArrayList[][] numVariationsInPosition,
                                 IntArrayList[] numVariationsInRead,
                                 IntArrayList[] insertSize,
                                 IntArrayList[] targetAlignedLength,
                                 IntArrayList[] queryAlignedLength,
                                 IntArrayList[] pairFlags,
                                 BaseInformationRecords.SampleInfo.Builder sampleBuilder,
                                 SampleCountInfo sampleCountInfo,
                                 String referenceGenotype,
                                 int genotypeMaxIndex) {
        for (int genotypeIndex = 0; genotypeIndex < genotypeMaxIndex; genotypeIndex++) {
            BaseInformationRecords.CountInfo.Builder infoBuilder = BaseInformationRecords.CountInfo.newBuilder();
            if (genotypeIndex == 0) {

                // this method may be expensive, don't call more than needed:
                referenceGenotype = sampleCountInfo.getReferenceGenotype();
            }
            infoBuilder.setFromSequence(referenceGenotype);
            infoBuilder.setToSequence(sampleCountInfo.getGenotypeString(genotypeIndex));
            infoBuilder.setMatchesReference(sampleCountInfo.isReferenceGenotype(genotypeIndex));
            infoBuilder.setGenotypeCountForwardStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, true));
            infoBuilder.setGenotypeCountReverseStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, false));

            infoBuilder.addAllQualityScoresForwardStrand(compressFreq(qualityScore[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllQualityScoresReverseStrand(compressFreq(qualityScore[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllReadIndicesForwardStrand(compressFreq(readIdx[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllReadIndicesReverseStrand(compressFreq(readIdx[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllNumVariationsInPositionForwardStrand(compressFreq(numVariationsInPosition[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllNumVariationsInPositionReverseStrand(compressFreq(numVariationsInPosition[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllReadMappingQualityForwardStrand(compressFreq(intArrayLists[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllReadMappingQualityReverseStrand(compressFreq(intArrayLists[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllNumVariationsInReads(compressFreq(numVariationsInRead[genotypeIndex]));
            infoBuilder.addAllInsertSizes(compressFreq(insertSize[genotypeIndex]));
            infoBuilder.addAllTargetAlignedLengths(compressFreq(targetAlignedLength[genotypeIndex]));
            infoBuilder.addAllQueryAlignedLengths(compressFreq(queryAlignedLength[genotypeIndex]));
            infoBuilder.addAllPairFlags(compressFreq(pairFlags[genotypeIndex]));

            infoBuilder.setIsIndel(sampleCountInfo.isIndel(genotypeIndex));
            sampleBuilder.addCounts(infoBuilder.build());
        }
    }

    static Int2IntMap freqMap = new Int2IntAVLTreeMap();

    public static List<BaseInformationRecords.NumberWithFrequency> compressFreq(List<Integer> numList) {
        //compress into map
        freqMap.clear();
        for (int num : numList) {
            int freq = freqMap.getOrDefault(num, 0);
            freqMap.put(num, freq + 1);
        }
        //iterate map into freqlist
        List<BaseInformationRecords.NumberWithFrequency> freqList = new ObjectArrayList<>(freqMap.size());
        for (Int2IntMap.Entry entry : freqMap.int2IntEntrySet()) {
            BaseInformationRecords.NumberWithFrequency.Builder freqBuilder = BaseInformationRecords.NumberWithFrequency.newBuilder();
            freqBuilder.setFrequency(entry.getIntValue());
            freqBuilder.setNumber(entry.getIntKey());
            freqList.add(freqBuilder.build());
        }
        return freqList;
    }


}
