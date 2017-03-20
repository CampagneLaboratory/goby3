package org.campagnelab.goby.predictions;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.reads.RandomAccessSequenceInterface;

import java.util.Properties;

/**
 * Created by rct66 on 3/20/17.
 */
public class DummyTrueGenotypeHelper implements AddTrueGenotypeHelperI {


    @Override
    public void configure(String mapFilename, RandomAccessSequenceInterface genome, int sampleIndex, boolean considerIndels, float referenceSamplingRate) {
    }

    @Override
    public void configure(String mapFilename, RandomAccessSequenceInterface genome, int sampleIndex, boolean considerIndels, boolean indelsAsRef, float referenceSamplingRate) {
    }

    @Override
    public boolean addTrueGenotype(BaseInformationRecords.BaseInformation record) {
        return false;
    }

    @Override
    public WillKeepI willKeep(int position, String referenceId, String referenceBase) {
        return null;
    }

    @Override
    public boolean addTrueGenotype(WillKeepI willKeep, BaseInformationRecords.BaseInformation record) {
        return false;
    }

    @Override
    public BaseInformationRecords.BaseInformation labeledEntry() {
        return null;
    }

    @Override
    public Properties getStatProperties() {
        Properties result = new Properties();
        result.put("addTrueGenotypes.numIndelsIgnored", Integer.toString(0));
        result.put("addTrueGenotypes.numVariantsAdded", Integer.toString(0));
        result.put("addTrueGenotypes.input.numRecords", Integer.toString(0));
        result.put("addTrueGenotypes.referenceSamplingRate", Float.toString(-1));
        result.put("addTrueGenotypes.considerIndels", Boolean.toString(false));
        result.put("addTrueGenotypes.mapFilename", "dummy_map_path");
        return result;
    }

    @Override
    public void printStats() {
        System.out.println("No stats to print for dummy genotype helper.");

    }
}
