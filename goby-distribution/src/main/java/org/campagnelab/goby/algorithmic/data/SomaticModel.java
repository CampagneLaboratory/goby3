package org.campagnelab.goby.algorithmic.data;

import it.unimi.dsi.lang.MutableString;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.modes.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.modes.dsv.SampleCountInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.model.utils.mappers.FeatureMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by rct66 on 6/23/16.
 */
public class SomaticModel {

    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;


    private ProtoPredictor predictor;
    private boolean isTrio;

    public SomaticModel(MultiLayerNetwork model, FeatureMapper mapper){
        this.predictor = new ProtoPredictor(model,mapper);
        this.isTrio = mapper.getClass().getCanonicalName().contains("Trio");

    }


    //readerIdxs convention: [father, mother, somatic, germline]. some of these fields will be -1 when the model only uses some of the samples
    public ProtoPredictor.Prediction mutPrediction(RandomAccessSequenceInterface genome, String referenceID,
                                                   SampleCountInfo sampleCounts[],
                                                   int referenceIndex, int position,
                                                   DiscoverVariantPositionData list,
                                                   int[] readerIdxs){
        int[] sampleToReaderIdxs;
        sampleToReaderIdxs = isTrio? (new int[]{readerIdxs[0],readerIdxs[1],readerIdxs[2]}) : (new int[]{readerIdxs[3],readerIdxs[2]});

        //in the past, predictions on 0 reads have been bypassed and given prediction value 0. leaving this out for now.
        BaseInformationRecords.BaseInformation proto = toProto(genome, referenceID, sampleCounts, referenceIndex,position,list, sampleToReaderIdxs );
        return predictor.mutPrediction(proto);
    }


    static MutableString genomicContext = new MutableString();

    public static BaseInformationRecords.BaseInformation toProto(RandomAccessSequenceInterface genome,
                                                                 String referenceID,
                                                                 SampleCountInfo sampleCounts[],
                                                                 int referenceIndex, int position,
                                                                 DiscoverVariantPositionData list,
                                                                 int[] sampleToReaderIdxs) {
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
            int baseInd = sampleCounts[0].baseIndex(baseInfo.to);
            //TODO Consider: binarySearch requires the first argument to be sorted. Does this hold?
            //TODO Consider: binary search only useful when searching a large array. For small arrays
            //TODO a linear search is faster.
            int sampleInd = Arrays.binarySearch(sampleToReaderIdxs,baseInfo.readerIndex);
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
        builder.setReferenceId(referenceID);
        // store 10 bases of genomic context around the site:
        genomicContext.setLength(0);
        int referenceSequenceLength = genome.getLength(referenceIndex);
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
        return builder.build();
    }



}
