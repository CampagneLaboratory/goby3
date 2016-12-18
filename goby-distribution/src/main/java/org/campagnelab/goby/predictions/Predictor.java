package org.campagnelab.goby.predictions;

import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.io.IOException;

/**
 * Created by fac2003 on 12/18/16.
 */
public interface Predictor {
    /**
     * Convenience method to extract the path where the model is located given a specific model file.
     * For instance, trims /a/b/models/1482078101500/bestAUC-ComputationGraph.bin to /a/b/models/1482078101500/.
     * Supports both traditional neural nets and computation graphs.
     *
     * @param fullModelPath Points to a .bin model file.
     * @return the directory that contains the model files.
     */
    String getModelPath(String fullModelPath);

    /**
     * Returns the model prefix/label. Assume you provide /a/b/models/1482078101500/bestAUC-ComputationGraph.bin.
     * This method will return bestAUC.
     *
     * @param fullModelPath Points to a .bin model file.
     * @return the model prefix/label
     */
    String getModelPrefix(String fullModelPath);

    /**
     * Load a model.
     *
     * @param modelPath   the path of the model.
     * @param modelPrefix the model prefix (e.g., bestscore, bestAUC, latest)
     * @throws IOException
     */
    void loadModel(String modelPath, String modelPrefix) throws IOException;

    /**
     * Returns true when the model has been successfully loaded. False otherwise.
     */
    boolean modelIsLoaded();

    /**
     * Predict if a site has a somatic variation.
     * Note about readerIdxs that sampleCounts may contain more samples than needed by the
     * predictor (because more samples were provided on the goby command line. The
     * predictor consults the readerIdxs array to determine the index inside sampleCounts.
     * Each predictor uses a number of elements of readerIdxs and expects samples to be
     * in a specific order.
     *
     * @param genome         Genome associated with the alignment.
     * @param referenceId    Chromosome name.
     * @param sampleCounts   Goby sampleCounts.
     * @param referenceIndex Chromosome index.
     * @param pos            position on the reference/chromosome.
     * @param list           list of individual bases observed at the site.
     * @param readerIdxs     indices to use for prediction inside the sampleCounts array.
     */
    void predict(RandomAccessSequenceInterface genome,
                 String referenceId,
                 SampleCountInfo[] sampleCounts,
                 int referenceIndex, int pos,
                 DiscoverVariantPositionData list,
                 int[] readerIdxs);
}
