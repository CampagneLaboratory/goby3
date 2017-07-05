package org.campagnelab.goby.predictions;

import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

/**
 * This interface defines the contract of the somatic predictor implemented in the variationanalysis project.
 * Created by fac2003 on 11/14/16.
 */
public interface SomaticPredictor extends Predictor {

    double probabilityIsMutated();

    double probabilityIsNotMutated();

    float getSomaticFrequency();

    boolean hasSomaticFrequency();

    boolean hasSomaticAllele();

    String getSomaticAllele();
}
