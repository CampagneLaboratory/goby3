package org.campagnelab.goby.algorithmic.data;

import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.model.utils.ConfigurableFeatureMapper;
import org.campagnelab.dl.model.utils.models.ModelLoader;
import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.model.utils.mappers.FeatureMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * Created by rct66 on 6/23/16.
 */
public class SomaticModel {

    public static final int POSITIVE_STRAND = 0;
    public static final int NEGATIVE_STRAND = 1;

    static private Logger LOG = LoggerFactory.getLogger(SomaticModel.class);

    private ProtoPredictor predictor;
    private boolean isTrio;

    public SomaticModel(Model model, FeatureMapper mapper) {
        this.predictor = new ProtoPredictor(model, mapper);
        this.isTrio = mapper.getClass().getCanonicalName().contains("Trio");

    }

    //prefix specifies whether to use best or latest model in directory
    public SomaticModel(String modelPath, String prefix) throws IOException {

        //get MAPPER
        FeatureMapper featureMapper = null;
        Properties prop = new Properties();
        InputStream input = null;
        try {
            final String modelPropertiesFilename = modelPath + "/config.properties";
            input = new FileInputStream(modelPropertiesFilename);
            // load a properties file
            prop.load(input);
            // get the property value and print it out
            String mapperName = prop.getProperty("mapper");

            ClassLoader classLoader = this.getClass().getClassLoader();
            // Load the target class using its binary name
            java.lang.Class loadedMyClass = classLoader.loadClass(mapperName);
            System.out.println("Loaded class name: " + loadedMyClass.getName());
            // Create a new instance from the loaded class
            Constructor constructor = loadedMyClass.getConstructor();
            featureMapper = (FeatureMapper) constructor.newInstance();
            if (featureMapper instanceof ConfigurableFeatureMapper) {
                ConfigurableFeatureMapper confMapper= (ConfigurableFeatureMapper) featureMapper;
                System.out.println("Configuring feature mapper with model properties at "+modelPropertiesFilename);
                confMapper.configure(prop);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to create feature mapper",e);
        } finally {
            IOUtils.closeQuietly(input);
        }


        ModelLoader modelLoader = new ModelLoader(modelPath);
        MultiLayerNetwork model = modelLoader.loadMultiLayerNetwork(prefix);

        this.predictor = new ProtoPredictor(model, featureMapper);
        this.isTrio = featureMapper.getClass().getCanonicalName().contains("Trio");
    }

    /**
     * Returns a prediction by applying a serialized version of the arguments (via toProto) to the stored model.
     * @param genome genome stored in a DiscoverVariantIterateSortedAlignments iterator
     * @param referenceID name of chromosome, also acquired from an iterator
     * @param sampleCounts Array of count information objects
     * @param referenceIndex index corresponding to chromosome
     * @param position position value of the record in question to serialize
     * @param list Additional data about the reads
     * @param readerIdxs Array which points a required sample (always father,mother,somatic,germline to its reader index
     *                     positions corresponding to readers which do not exist (ie father in a pair scenario)
     *                     will contain value -1
     * @return
     */
    //readerIdxs convention: [father, mother, somatic, germline]. some of these fields will be -1 when the model only uses some of the samples
    public ProtoPredictor.Prediction mutPrediction(RandomAccessSequenceInterface genome, String referenceID,
                                                   SampleCountInfo sampleCounts[],
                                                   int referenceIndex, int position,
                                                   DiscoverVariantPositionData list,
                                                   int[] readerIdxs) {
        Integer[] sampleToReaderIdxs;
        sampleToReaderIdxs = isTrio ? (new Integer[]{readerIdxs[0], readerIdxs[1], readerIdxs[2]}) : (new Integer[]{readerIdxs[3], readerIdxs[2]});

        //in the past, predictions on 0 reads have been bypassed and given prediction value 0. leaving this out for now.
        BaseInformationRecords.BaseInformation proto = toProto(genome, referenceID, sampleCounts, referenceIndex, position, list, sampleToReaderIdxs);
        return predictor.mutPrediction(proto);
    }


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
            int baseIndex = sampleCounts[0].baseIndex(baseInfo.to);
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
            //This simply marks the the last sample (consistent with convention) as a tumor sample.
            //No matter the experimental design, we require a single, final "tumor" sample which will be the one
            //the model makes mutation predictions on. Other somatic samples could be included, but the model
            //will use the other samples to make predictions about the last sample.
            if (sampleIndex == numSamples-1) {
                sampleBuilder.setIsTumor(true);
            }
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
