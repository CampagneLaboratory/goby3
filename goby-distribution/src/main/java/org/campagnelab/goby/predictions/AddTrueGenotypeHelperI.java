package org.campagnelab.goby.predictions;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;
import org.campagnelab.goby.util.Variant;

import java.util.Properties;
import java.util.Set;

/**
 * Encapsulates the AddTrueGenotype logic. Moved the class from variationanalysis to use in generating .sbi files.
 * Created by fac2003 on 12/27/16.
 */
public interface AddTrueGenotypeHelperI {
    /**
     * Configure the helper.
     *
     * @param mapFilename
     * @param genome
     * @param sampleIndex
     * @param considerIndels
     * @param referenceSamplingRate
     */
    void configure(String mapFilename, RandomAccessSequenceInterface genome,
                   int sampleIndex, boolean considerIndels, float referenceSamplingRate);

    /**
     * Configure the helper.
     *
     * @param mapFilename
     * @param genome
     * @param sampleIndex
     * @param considerIndels
     * @param indelsAsRef denotes whether to include the ref base of indels if they are not considered.
     * @param referenceSamplingRate
     */
    void configure(String mapFilename, RandomAccessSequenceInterface genome,
                   int sampleIndex, boolean considerIndels, boolean indelsAsRef, float referenceSamplingRate);

    /**
     * Label a record with true genotype. Return true when the record should  be written to the output.
     * The result considers whether the site contains a true variant (always kept), or whether the site
     * has been sampled from reference matching sites (using referenceSamplingRate).
     *
     * @param record The .sbi record to annotate.
     * @return True if the record should be kept, i.e., written to the output, false otherwise.
     */
    boolean addTrueGenotype(BaseInformationRecords.BaseInformation record);

    /**
     * Determine if a record will be kept.
     */
    WillKeepI willKeep(int position, String referenceId, String referenceBase);

    /**
     * Label a record with true genotype. Return true when the record should  be written to the output.
     * The result considers whether the site contains a true variant (always kept), or whether the site
     * has been sampled from reference matching sites (using referenceSamplingRate).
     *
     * @param willKeep Previously determined willKeep information.
     * @param record   The .sbi record to annotate.
     * @return True if the record should be kept, i.e., written to the output, false otherwise.
     */
    boolean addTrueGenotype(WillKeepI willKeep, BaseInformationRecords.BaseInformation record);

    /**
     * Return the labeled entry. Call this method after addTrueGenotype() when it returned keep=true.
     *
     * @return The labeled entry.
     */
    BaseInformationRecords.BaseInformation labeledEntry();

    /**
     * Returns statistics about the addition of true genotypes.
     * @return
     */
     Properties getStatProperties() ;

    /**
     * Print statistics to the console.
     */
     void printStats();

    interface WillKeepI {

        boolean isKeep();

        Set<Variant.FromTo> getTrueAlleles();


        boolean isVariant();

    }
}