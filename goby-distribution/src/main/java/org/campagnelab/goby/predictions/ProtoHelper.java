package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.Alignments;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.BaseToStringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
     * @param genome                 genome stored in a DiscoverVariantIterateSortedAlignments iterator
     * @param referenceID            name of chromosome, also acquired from an iterator
     * @param sampleCounts           Array of count information objects
     * @param referenceIndex         index corresponding to chromosome
     * @param position               position value of the record in question to serialize
     * @param list                   Additional data about the reads
     * @param sampleIndicesToProcess Array which points a required sample (trio:father,mother,somatic pair:germline,somatic) to its reader index
     *                               this index corresponds to the location of data collected by that reader in the SampleCountInfo array
     * @return
     */
    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 Integer[] sampleIndicesToProcess, int contextLength) {
        //focus on the samples we need to process out of the full sampleCounts array:
        SampleCountInfo[] reducedSampleCountArray = new SampleCountInfo[sampleIndicesToProcess.length];

        final IntArrayList sampleIndicesToProcessList = new IntArrayList();
        sampleIndicesToProcessList.addAll(Arrays.asList(sampleIndicesToProcess));

        for (int originalSampleIndex : sampleIndicesToProcess) {
            int reducedSampleIndex = sampleIndicesToProcessList.indexOf(originalSampleIndex);
            reducedSampleCountArray[reducedSampleIndex] = sampleCounts[originalSampleIndex];
        }
        DiscoverVariantPositionData newList = new DiscoverVariantPositionData(list.getZeroBasedPosition(),
                list.getReferenceBase());

        for (PositionBaseInfo baseInfo : list) {
            PositionBaseInfo copy = new PositionBaseInfo(baseInfo);
            final int originalSampleIndex = baseInfo.readerIndex;
            if (sampleIndicesToProcessList.contains(originalSampleIndex)) {
                int reducedSampleIndex = sampleIndicesToProcessList.indexOf(originalSampleIndex);
                copy.readerIndex = reducedSampleIndex;
                newList.add(copy);
            }
        }
        return toProto(genome,
                referenceID,
                reducedSampleCountArray,
                referenceIndex, position,
                newList,
                contextLength);
    }

    /**
     * Returns a serialized record of a given position in protobuf format. Required step before mapping to features.
     * Used by SequenceBaseInformationOutputFormat to generate datasets, and SomaticVariationOutputFormat (via mutPrediction) when
     * generating predictions on new examples.
     *
     * @param genome         genome stored in a DiscoverVariantIterateSortedAlignments iterator
     * @param referenceID    name of chromosome, also acquired from an iterator
     * @param sampleCounts   Array of count information objects
     * @param referenceIndex index corresponding to chromosome
     * @param position       position value of the record in question to serialize
     * @param list           Additional data about the reads
     * @return
     */
    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 int contextLength) {


        int numSamples = sampleCounts.length;

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
        IntArrayList[][][] distancesToReadVariations = new IntArrayList[numSamples][maxGenotypeIndex][2];
        IntArrayList[][] distancesToStartOfRead = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] distancesToEndOfRead = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] numVariationsInReads = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] insertSizes = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] targetAlignedLengths = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] queryAlignedLengths = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] queryPositions = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] pairFlags = new IntArrayList[numSamples][maxGenotypeIndex];


        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            for (int genotypeIndex = 0; genotypeIndex < maxGenotypeIndex; genotypeIndex++) {
                for (int strandIndex = 0; strandIndex < 2; strandIndex++) {
                    qualityScores[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    readMappingQuality[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    readIdxs[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    distancesToReadVariations[sampleIndex][genotypeIndex][strandIndex] = new IntArrayList(1024);
                    distancesToStartOfRead[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    distancesToEndOfRead[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    numVariationsInReads[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    insertSizes[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    targetAlignedLengths[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    queryAlignedLengths[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    queryPositions[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                    pairFlags[sampleIndex][genotypeIndex] = new IntArrayList(1024);
                }
            }
        }

        for (PositionBaseInfo baseInfo : list) {
            int baseIndex = sampleCounts[baseInfo.readerIndex].baseIndex(baseInfo.to);
            int sampleIndex = baseInfo.readerIndex;
            // check that we need to focus on the sample from which this base originates (if not, ignore the base)
            if (sampleIndex != -1) {
                int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
                qualityScores[sampleIndex][baseIndex][strandInd].add(baseInfo.qualityScore & 0xFF);
                readMappingQuality[sampleIndex][baseIndex][strandInd].add(baseInfo.readMappingQuality & 0xFF);
                numVariationsInReads[sampleIndex][baseIndex].add(baseInfo.numVariationsInRead);
                final int queryLength = baseInfo.alignmentEntry.getQueryLength();
                int distanceToStartOfRead = baseInfo.matchesForwardStrand ? baseInfo.readIndex : queryLength - baseInfo.readIndex;
                int distanceToEndOfRead = baseInfo.matchesForwardStrand ? queryLength - baseInfo.readIndex : baseInfo.readIndex;
                distancesToStartOfRead[sampleIndex][baseIndex].add(distanceToStartOfRead);
                distancesToEndOfRead[sampleIndex][baseIndex].add(distanceToEndOfRead);
                insertSizes[sampleIndex][baseIndex].add(baseInfo.insertSize);
                targetAlignedLengths[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getTargetAlignedLength());
                //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
                readIdxs[sampleIndex][baseIndex][strandInd].add(baseInfo.readIndex);
                for (Alignments.SequenceVariation var : baseInfo.alignmentEntry.getSequenceVariationsList()) {
                    int varIndex = var.getReadIndex();
                    int delta = baseInfo.readIndex - varIndex;
                    distancesToReadVariations[sampleIndex][baseIndex][strandInd].add(delta);


                }
                targetAlignedLengths[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getTargetAlignedLength());
                queryAlignedLengths[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getQueryAlignedLength());
                queryPositions[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getQueryPosition());
                pairFlags[sampleIndex][baseIndex].add(baseInfo.alignmentEntry.getPairFlags());
            }
        }

        //iterate over indels and populate data for those too
        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            //make a map from EIR -> genotypeIndex
            Object2IntArrayMap<EquivalentIndelRegion> indelIndices = new Object2IntArrayMap<>();
            for (int i = SampleCountInfo.BASE_MAX_INDEX; i < maxGenotypeIndex; i++) {
                // indels are aligned across samples, so we take the index/indel association from the first sample:
                indelIndices.put(sampleCounts[0].getIndelGenotype(i), i);
            }
            if (sampleCounts[sampleIndex].getEquivalentIndelRegions() == null) {
                continue;
            }
            for (EquivalentIndelRegion eqr : sampleCounts[sampleIndex].getEquivalentIndelRegions()) {
                int baseIndex = indelIndices.getInt(eqr);
                readIdxs[sampleIndex][baseIndex][POSITIVE_STRAND].addAll(eqr.forwardReadIndices);
                readIdxs[sampleIndex][baseIndex][NEGATIVE_STRAND].addAll(eqr.reverseReadIndices);
                for (Alignments.AlignmentEntry entry : eqr.supportingEntries) {
                    int strandInd = entry.getMatchingReverseStrand() ? NEGATIVE_STRAND : POSITIVE_STRAND;

                    readMappingQuality[sampleIndex][baseIndex][strandInd].add(entry.getMappingQuality() & 0xFF);
                    numVariationsInReads[sampleIndex][baseIndex].add(entry.getSequenceVariationsCount());
                    insertSizes[sampleIndex][baseIndex].add(entry.getInsertSize());
                    targetAlignedLengths[sampleIndex][baseIndex].add(entry.getTargetAlignedLength());
                    //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
                    Integer indelReadIndex = null;
                    //get this read index of this indel in this read by finding it in the variant list
                    for (Alignments.SequenceVariation var : entry.getSequenceVariationsList()) {
                        if (var.getTo().equals(eqr.to) && var.getFrom().equals(eqr.from) && (eqr.forwardReadIndices.contains(var.getReadIndex()) || eqr.forwardReadIndices.contains(var.getReadIndex()))) {
                            indelReadIndex = var.getReadIndex();
                            continue;
                        }
                    }
                    //now iterate over the other variants and add their read idx deltas.
                    for (Alignments.SequenceVariation var : entry.getSequenceVariationsList()) {
                        if ((var.getTo().equals(eqr.to) && var.getFrom().equals(eqr.from)) || indelReadIndex == null /* couldn't find indel variant */) {
                            continue;
                        }
                        int varIndex = var.getReadIndex();
                        int delta = indelReadIndex - varIndex;
                        distancesToReadVariations[sampleIndex][baseIndex][strandInd].add(delta);
                        if (var.hasToQuality()) {
                            for (byte quality : var.getToQuality()) {
                                qualityScores[sampleIndex][baseIndex][strandInd].add(quality & 0xFF);
                            }
                        }
                    }
                    targetAlignedLengths[sampleIndex][baseIndex].add(entry.getTargetAlignedLength());
                    queryAlignedLengths[sampleIndex][baseIndex].add(entry.getQueryAlignedLength());
                    queryPositions[sampleIndex][baseIndex].add(entry.getQueryPosition());
                    pairFlags[sampleIndex][baseIndex].add(entry.getPairFlags());
                }
            }
        }

        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();
        builder.setMutated(false);
        builder.setPosition(position);
        builder.setReferenceId(referenceID);
        int genomeReferenceIndex = genome.getReferenceIndex(referenceID);
        transferGenomicContext(contextLength, genome, genomeReferenceIndex, position, list, builder, sampleCounts);


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
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIndex];
            String referenceGenotype = null;
            final int genotypeMaxIndex = sampleCountInfo.getGenotypeMaxIndex();

            transfer(qualityScores[sampleIndex], readMappingQuality[sampleIndex], readIdxs[sampleIndex],
                    distancesToReadVariations[sampleIndex],
                    distancesToStartOfRead[sampleIndex],
                    distancesToEndOfRead[sampleIndex],
                    numVariationsInReads[sampleIndex], insertSizes[sampleIndex],
                    targetAlignedLengths[sampleIndex], queryAlignedLengths[sampleIndex], queryPositions[sampleIndex],
                    pairFlags[sampleIndex],
                    sampleBuilder, sampleCountInfo,
                    referenceGenotype, maxGenotypeIndex);
            sampleBuilder.setFormattedCounts(sampleCounts[sampleIndex].toString());
            builder.addSamples(sampleBuilder.build());
        }
        baseProgressLogger.update(list.size());
        list.clear();
        return builder.build();
    }

    private static Random random = new Random();

    private static void transferGenomicContext(int contextLength, RandomAccessSequenceInterface genome, int genomeReferenceIndex,
                                               int position, DiscoverVariantPositionData list, BaseInformationRecords.BaseInformation.Builder builder, SampleCountInfo[] sampleCounts) {
        // store 10 bases of genomic context around the site:
        genomicContext.setLength(0);
        int referenceSequenceLength = genome.getLength(genomeReferenceIndex);
        if (referenceSequenceLength <= 0) {
            builder.setGenomicSequenceContext(defaultGenomicContext(contextLength));
        } else {
            //derive context length
            int cl = (contextLength - 1) / 2;
            final int genomicStart = Math.max(position - cl, 0);
            final int genomicEnd = Math.min(position + (cl + 1), referenceSequenceLength);
            int index = 0;
            for (int refPos = genomicStart; refPos < genomicEnd; refPos++) {
                genomicContext.insert(index++, baseConversion.convert(genome.get(genomeReferenceIndex, refPos)));
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
        String refBase = baseConversion.convert(list.getReferenceBase());
        if (refBase == null) {
            String referenceGenotype = sampleCounts[0].getReferenceGenotype();
            if (referenceGenotype.length() >= 1) {
                refBase = sampleCounts[0].getReferenceGenotype().substring(0, 1);
            } else {
                refBase = Character.toString(genome.get(genomeReferenceIndex, position));
            }
        }
        builder.setReferenceBase(refBase);
    }

    private static void transfer(IntArrayList[][] qualityScore,
                                 IntArrayList[][] intArrayLists,
                                 IntArrayList[][] readIdx,
                                 IntArrayList[][] distancesToReadVariations,
                                 IntArrayList[] distancesToStartOfRead,
                                 IntArrayList[] distancesToEndOfRead,
                                 IntArrayList[] numVariationsInRead,
                                 IntArrayList[] insertSize,
                                 IntArrayList[] targetAlignedLength,
                                 IntArrayList[] queryAlignedLength,
                                 IntArrayList[] queryPositions,
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
            if (genotypeIndex < sampleCountInfo.BASE_MAX_INDEX) {
                // if not indel
                try {
                    infoBuilder.setFromSequence(referenceGenotype.substring(0, 1));
                } catch (NullPointerException e) {
                    infoBuilder.setFromSequence("");
                }
            } else {
                infoBuilder.setFromSequence(sampleCountInfo.getIndelGenotype(genotypeIndex).fromInContext());
            }
            infoBuilder.setToSequence(sampleCountInfo.getGenotypeString(genotypeIndex));
            infoBuilder.setMatchesReference(sampleCountInfo.isReferenceGenotype(genotypeIndex));
            infoBuilder.setGenotypeCountForwardStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, true));
            infoBuilder.setGenotypeCountReverseStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, false));

            infoBuilder.addAllQualityScoresForwardStrand(compressFreq(qualityScore[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllQualityScoresReverseStrand(compressFreq(qualityScore[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllReadIndicesForwardStrand(compressFreq(readIdx[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllReadIndicesReverseStrand(compressFreq(readIdx[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllDistancesToReadVariationsForwardStrand(compressFreq(distancesToReadVariations[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllDistancesToReadVariationsReverseStrand(compressFreq(distancesToReadVariations[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllReadMappingQualityForwardStrand(compressFreq(intArrayLists[genotypeIndex][POSITIVE_STRAND]));
            infoBuilder.addAllReadMappingQualityReverseStrand(compressFreq(intArrayLists[genotypeIndex][NEGATIVE_STRAND]));

            infoBuilder.addAllNumVariationsInReads(compressFreq(numVariationsInRead[genotypeIndex]));
            infoBuilder.addAllInsertSizes(compressFreq(insertSize[genotypeIndex]));
            infoBuilder.addAllTargetAlignedLengths(compressFreq(targetAlignedLength[genotypeIndex]));
            infoBuilder.addAllQueryAlignedLengths(compressFreq(queryAlignedLength[genotypeIndex]));
            infoBuilder.addAllQueryPositions(compressFreq(queryPositions[genotypeIndex]));
            infoBuilder.addAllPairFlags(compressFreq(pairFlags[genotypeIndex]));
            infoBuilder.addAllDistanceToStartOfRead(compressFreq(distancesToStartOfRead[genotypeIndex]));
            infoBuilder.addAllDistanceToEndOfRead(compressFreq(distancesToEndOfRead[genotypeIndex]));
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
