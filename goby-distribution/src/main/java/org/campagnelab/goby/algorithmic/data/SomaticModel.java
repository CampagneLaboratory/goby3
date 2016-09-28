package org.campagnelab.goby.algorithmic.data;

import org.campagnelab.goby.modes.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.modes.dsv.SampleCountInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.model.utils.mappers.FeatureMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

/**
 * Created by rct66 on 6/23/16.
 */
public class SomaticModel {

    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;

    private ProtoPredictor predictor;

    public SomaticModel(MultiLayerNetwork model, FeatureMapper mapper){
        this.predictor = new ProtoPredictor(model,mapper);
    }


    public ProtoPredictor.Prediction mutPrediction(SampleCountInfo sampleCounts[], int referenceIndex, int position, DiscoverVariantPositionData list, int germSampleId, int somSampleId){

        BaseInformationRecords.BaseInformation proto = null;
        try {
            proto = toProto(sampleCounts, referenceIndex,position,list,germSampleId,somSampleId);
        } catch (TooFewCountsException e) {
            return predictor.getNullPrediction();
        }
        return predictor.mutPrediction(proto);
    }

    private BaseInformationRecords.BaseInformation toProto(SampleCountInfo sampleCounts[], int referenceIndex, int position, DiscoverVariantPositionData list, int germSampleId, int somSampleId) throws TooFewCountsException {
        int[] sampleIds = new int[]{germSampleId,somSampleId};
        int maxGenotypeIndex=0;
        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            maxGenotypeIndex=Math.max(sampleCounts[sampleIndex].getGenotypeMaxIndex(), maxGenotypeIndex);
        }

        IntArrayList[][][] qualityScores = new IntArrayList[2][maxGenotypeIndex][2];
        IntArrayList[][][] readIdxs = new IntArrayList[2][maxGenotypeIndex][2];

        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIds[sampleIndex]];
            final int genotypeMaxIndex = sampleCountInfo.getGenotypeMaxIndex();

            for (int genotypeIndex = 0; genotypeIndex < genotypeMaxIndex; genotypeIndex++) {
                for (int k = 0; k < 2; k++) {
                    qualityScores[sampleIndex][genotypeIndex][k] = new IntArrayList();
                    readIdxs[sampleIndex][genotypeIndex][k] = new IntArrayList();
                }
            }
        }
        for (PositionBaseInfo baseInfo : list) {
            int sampleId = baseInfo.readerIndex;
            int sampleIdx;
            if (sampleId == germSampleId){
                sampleIdx = 0;
            } else if (sampleId == somSampleId) {
                sampleIdx = 1;
            } else {
                continue;
            }
            int baseInd = sampleCounts[0].baseIndex(baseInfo.to);
            int strandInd = baseInfo.matchesForwardStrand ? POSITIVE_STRAND : NEGATIVE_STRAND;
            qualityScores[sampleIdx][baseInd][strandInd].add(baseInfo.qualityScore & 0xFF);
            //System.out.printf("%d%n",baseInfo.qualityScore & 0xFF);
            readIdxs[sampleIdx][baseInd][strandInd].add(baseInfo.readIndex);
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
            final SampleCountInfo sampleCountInfo = sampleCounts[sampleIds[sampleIndex]];
            if (sampleIndex == 1) {
                sampleBuilder.setIsTumor(true);
            } else {
                sampleBuilder.setIsTumor(false);
            }
            int counts = 0;
            for (int genotypeIndex = 0; genotypeIndex < sampleCountInfo.getGenotypeMaxIndex(); genotypeIndex++) {
                BaseInformationRecords.CountInfo.Builder infoBuilder = BaseInformationRecords.CountInfo.newBuilder();
                infoBuilder.setFromSequence(sampleCountInfo.getReferenceGenotype());
                infoBuilder.setToSequence(sampleCountInfo.getGenotypeString(genotypeIndex));
                infoBuilder.setMatchesReference(sampleCountInfo.isReferenceGenotype(genotypeIndex));

                int forCount = sampleCountInfo.getGenotypeCount(genotypeIndex, true);
                int revCount = sampleCountInfo.getGenotypeCount(genotypeIndex, false);
                counts += (forCount + revCount);
                infoBuilder.setGenotypeCountForwardStrand(forCount);
                infoBuilder.setGenotypeCountReverseStrand(revCount);

                infoBuilder.addAllQualityScoresForwardStrand(ProtoPredictor.compressFreq(qualityScores[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllQualityScoresReverseStrand(ProtoPredictor.compressFreq(qualityScores[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));
                infoBuilder.addAllReadIndicesForwardStrand(ProtoPredictor.compressFreq(readIdxs[sampleIndex][genotypeIndex][POSITIVE_STRAND]));
                infoBuilder.addAllReadIndicesReverseStrand(ProtoPredictor.compressFreq(readIdxs[sampleIndex][genotypeIndex][NEGATIVE_STRAND]));
                infoBuilder.setIsIndel(sampleCountInfo.isIndel(genotypeIndex));
                sampleBuilder.addCounts(infoBuilder.build());
            }
            if (counts < 1) {
                throw new TooFewCountsException();
            }
            sampleBuilder.setFormattedCounts(sampleCountInfo.toString());
            builder.addSamples(sampleBuilder.build());
        }
        return builder.build();

    }

    private class TooFewCountsException extends Exception {}



}
