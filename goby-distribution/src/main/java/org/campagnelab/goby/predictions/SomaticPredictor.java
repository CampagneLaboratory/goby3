package org.campagnelab.goby.predictions;

import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.io.IOException;

/**
 * This interface defines the contract of the somatic predictor implemented in the variationanalysis project.
 * Created by fac2003 on 11/14/16.
 */
public interface SomaticPredictor {
    void loadModel(String modelPath, String modelPrefix) throws IOException;

    void predict(RandomAccessSequenceInterface genome,
                 String s,
                 SampleCountInfo[] sampleCounts,
                 int referenceIndex, int pos,
                 DiscoverVariantPositionData list,
                 int[] readerIdxs);

    double probabilityIsMutated();

    double probabilityIsNotMutated();

    float getSomaticFrequency();

    boolean modelIsLoaded();

    boolean hasSomaticFrequency();
}
