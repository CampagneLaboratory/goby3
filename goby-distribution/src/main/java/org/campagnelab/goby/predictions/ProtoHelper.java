package org.campagnelab.goby.predictions;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.lang.MutableString;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.alignments.PositionBaseInfo;

import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.util.List;

/**
 * Created by mas2182 on 11/15/16.
 */
public class ProtoHelper {
    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;
    static final int contextLength = 21;

    static MutableString genomicContext = new MutableString();


    /**
     * Returns a serialized record of a given position in protobuf format. Required step before mapping to features.
     * Used by SequenceBaseInformationOutputFormat to generate datasets, and SomaticVariationOutputFormat (via mutPrediction) when
     * generating predictions on new examples.
     * @param genome genome stored in a DiscoverVariantIterateSortedAlignments iterator
     * @param referenceID name of chromosome, also acquired from an iterator
     * @param sampleCounts Array of count information objects
     * @param referenceIndex index corresponding to chromosome
     * @param position position value of the record in question to serialize
     * @param list Additional data about the reads
     * @param sampleToReaderIdxs Array which points a required sample (trio:father,mother,somatic pair:germline,somatic) to its reader index
     *                           this index corresponds to the location of data collected by that reader in the SampleCountInfo array
     * @return
     */
    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 Integer[] sampleToReaderIdxs) {
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
        IntArrayList[][] numVariationsInReads = new IntArrayList[numSamples][maxGenotypeIndex];
        IntArrayList[][] insertSizes = new IntArrayList[numSamples][maxGenotypeIndex];

        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleToReaderIdxs[sampleIndex]];
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
            int baseIndex = sampleCounts[sampleToReaderIdxs[baseInfo.readerIndex]].baseIndex(baseInfo.to);
            int sampleIndex = java.util.Arrays.asList((sampleToReaderIdxs)).indexOf(baseInfo.readerIndex);
            // check that we need to focus on the sample from which this base originates (if not, ignore the base)
            if (sampleIndex != -1) {
                int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
                qualityScores[sampleIndex][baseIndex][strandInd].add(baseInfo.qualityScore & 0xFF);
                readMappingQuality[sampleIndex][baseIndex][strandInd].add(baseInfo.readMappingQuality & 0xFF);
                numVariationsInReads[sampleIndex][baseIndex].add(baseInfo.numVariationsInRead);
                insertSizes[sampleIndex][baseIndex].add(baseInfo.insertSize);
                //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
                readIdxs[sampleIndex][baseIndex][strandInd].add(baseInfo.readIndex);
            }
        }
        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();
        builder.setMutated(false);
        builder.setPosition(position);
        builder.setReferenceId(referenceID);
        // store 10 bases of genomic context around the site:
        genomicContext.setLength(0);
        int referenceSequenceLength = genome.getLength(referenceIndex);

        //derive context length
        int cl = (contextLength-1)/2;
        for (int refPos = Math.max(position - cl, 0); refPos < Math.min(position + (cl+1), referenceSequenceLength); refPos++) {
            genomicContext.append(genome.get(referenceIndex, refPos));
        }
        //pad zeros as needed
        for (int i = (position - cl); i < 0; i++){
            genomicContext.insert(0,"N");
        }
        for (int i = (position + (cl+1)); i >= referenceSequenceLength; i--){
            genomicContext.append("N");
        }
        builder.setGenomicSequenceContext(genomicContext.toString());

        if (list.size() > 0) {
            builder.setReferenceBase(Character.toString(list.getReferenceBase()));
        }
        builder.setReferenceIndex(referenceIndex);

        for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
            BaseInformationRecords.SampleInfo.Builder sampleBuilder = BaseInformationRecords.SampleInfo.newBuilder();
            //This simply marks the the last sample (consistent with convention) as a tumor sample.
            //No matter the experimental design, we require a single, final "tumor" sample which will be the one
            //the model makes mutation predictions on. Other somatic samples could be included, but the model
            //will use the other samples to make predictions about the last sample.
            if (sampleIndex == numSamples-1) {
                sampleBuilder.setIsTumor(true);
            }
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleToReaderIdxs[sampleIndex]];

            for (int genotypeIndex = 0; genotypeIndex < sampleCountInfo.getGenotypeMaxIndex(); genotypeIndex++) {
                BaseInformationRecords.CountInfo.Builder infoBuilder = BaseInformationRecords.CountInfo.newBuilder();

                infoBuilder.setFromSequence(sampleCountInfo.getReferenceGenotype());
                infoBuilder.setToSequence(sampleCountInfo.getGenotypeString(genotypeIndex));
                infoBuilder.setMatchesReference(sampleCountInfo.isReferenceGenotype(genotypeIndex));
                infoBuilder.setGenotypeCountForwardStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, true));
                infoBuilder.setGenotypeCountReverseStrand(sampleCountInfo.getGenotypeCount(genotypeIndex, false));

                infoBuilder.addAllQualityScoresForwardStrand(compressFreq(qualityScores[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllQualityScoresReverseStrand(compressFreq(qualityScores[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));

                infoBuilder.addAllReadIndicesForwardStrand(compressFreq(readIdxs[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllReadIndicesReverseStrand(compressFreq(readIdxs[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));

                infoBuilder.addAllReadMappingQualityForwardStrand(compressFreq(readMappingQuality[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllReadMappingQualityReverseStrand(compressFreq(readMappingQuality[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));

                infoBuilder.addAllNumVariationsInReads(compressFreq(numVariationsInReads[sampleIndex][genotypeIndex]));
                infoBuilder.addAllInsertSizes(compressFreq(insertSizes[sampleIndex][genotypeIndex]));

                infoBuilder.setIsIndel(sampleCountInfo.isIndel(genotypeIndex));
                sampleBuilder.addCounts(infoBuilder.build());
            }
            sampleBuilder.setFormattedCounts(sampleCounts[sampleToReaderIdxs[sampleIndex]].toString());
            builder.addSamples(sampleBuilder.build());
        }
        return builder.build();
    }

    public static List<BaseInformationRecords.NumberWithFrequency> compressFreq(List<Integer> numList) {
        //compress into map
        Int2IntArrayMap freqMap = new Int2IntArrayMap(100);
        for (int num : numList) {
            Integer freq = freqMap.putIfAbsent(num, 1);
            if (freq != null) {
                freqMap.put(num, freq + 1);
            }
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
