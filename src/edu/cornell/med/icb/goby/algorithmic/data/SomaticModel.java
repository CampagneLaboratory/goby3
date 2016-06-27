package edu.cornell.med.icb.goby.algorithmic.data;

import edu.cornell.med.icb.goby.alignments.DiscoverVariantPositionData;
import edu.cornell.med.icb.goby.alignments.PositionBaseInfo;
import edu.cornell.med.icb.goby.alignments.SampleCountInfo;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.campagnelab.dl.model.utils.mappers.FeatureMapper;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;


import java.io.PrintWriter;
import java.util.List;

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


    public boolean mutPrediction(SampleCountInfo sampleCounts[], int referenceIndex, int position, DiscoverVariantPositionData list, int sampleIdx1, int sampleIdx2){
        BaseInformationRecords.BaseInformation proto = toProto(sampleCounts, referenceIndex,position,list,sampleIdx1,sampleIdx2);
        return predictor.mutPrediction(proto).clas;
    }

    private BaseInformationRecords.BaseInformation toProto(SampleCountInfo sampleCounts[], int referenceIndex, int position, DiscoverVariantPositionData list, int sampleIdx1, int sampleIdx2){
        int maxGenotypeIndex=0;
        for (int sampleIndex = 0; sampleIndex < 2; sampleIndex++) {
            maxGenotypeIndex=Math.max(sampleCounts[sampleIndex].getGenotypeMaxIndex(), maxGenotypeIndex);
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
        return builder.build();

    }

}
