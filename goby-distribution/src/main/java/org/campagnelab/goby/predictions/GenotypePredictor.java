package org.campagnelab.goby.predictions;

import org.campagnelab.goby.algorithmic.dsv.DiscoverVariantPositionData;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.io.IOException;

/**
 * This interface defines the contract of the genotype predictor implemented in the variationanalysis project.
 * Created by fac2003 on 12/18/16.
 */
public interface GenotypePredictor extends Predictor {
    /**
     * Returns the probability that a genotype is called.
     * @param genotypeIndex index of the genotype using the Goby conventions (bases first, then indels, see SampleCountInfo).
     * @return Probability that the genotype is called present by the model.
     */
    double probabilityGenotypeIsCalled(int genotypeIndex);
    /**
     * Returns the probability that a genotype is not called.
     * @param genotypeIndex index of the genotype using the Goby conventions (bases first, then indels, see SampleCountInfo).
     * @return Probability that the genotype is not called present by the model.
     */
    double probabilityGenotypeIsNotCalled(int genotypeIndex);

    /**
     * Returns the genotype called by the model. Strings are returned like A/C, or ACT/A--. Delimiters are either / for
     * unphase genotype, or | for phase genotype.
     * @return A string which represent the called genotype.
     */
    String getCalledGenotype();

    /**
     * Indicate if this genotype model was trained with indels. Can be checked at runtime to make sure a model
     * is used that can handle indels.
     * @return True or false.
     */
    boolean trainedForIndels();
}
