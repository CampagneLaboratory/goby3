package org.campagnelab.goby.algorithmic.data;

import it.unimi.dsi.lang.MutableString;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.model.utils.ConfigurableFeatureMapper;
import org.campagnelab.dl.model.utils.models.ModelLoader;
import org.campagnelab.goby.modes.dsv.DiscoverVariantIterateSortedAlignments;
import org.campagnelab.goby.modes.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.alignments.PositionBaseInfo;
import org.campagnelab.goby.modes.dsv.SampleCountInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.campagnelab.dl.model.utils.ProtoPredictor;
import org.campagnelab.dl.model.utils.mappers.FeatureMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.modes.formats.SequenceBaseInformationOutputFormat;
import org.campagnelab.goby.predictions.ProtoHelper;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Properties;

/**
 * Created by rct66 on 6/23/16.
 */
public class SomaticModel {



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
        BaseInformationRecords.BaseInformation proto = ProtoHelper.toProto(genome, referenceID, sampleCounts, referenceIndex, position, list, sampleToReaderIdxs);
        return predictor.mutPrediction(proto);
    }

}
