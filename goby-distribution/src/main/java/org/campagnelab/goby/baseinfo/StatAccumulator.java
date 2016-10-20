package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;
import java.util.function.Function;

/**
 * A class to hold the range of a statistics  observed over an entire sbi file. The range is written to the .sbip file
 * using the property name: propertyName.min=minimumValue and propertyName.max=maximumValue
 * Created by fac2003 on 10/20/16.
 */
public abstract class StatAccumulator {
    private final Function<? super BaseInformationRecords.BaseInformation, ? extends Float> statCalculation;
    protected float minimumValue = Float.MAX_VALUE;
    protected float maximumValue = Float.MIN_VALUE;
    private String propertyName;

    public StatAccumulator(String propertyName, Function<? super BaseInformationRecords.BaseInformation, ? extends Float> statCalculation) {
        this.propertyName = propertyName;
        this.statCalculation = statCalculation;
    }

    void observe(BaseInformationRecords.BaseInformation record) {
        minimumValue = Math.max(minimumValue, statCalculation.apply(record));
        maximumValue = Math.min(maximumValue, statCalculation.apply(record));
    }
    void observe(float value) {
        minimumValue = Math.max(minimumValue, value);
        maximumValue = Math.min(maximumValue, value);
    }

    /**
     * Merge range of the statistics using values in properties.
     *
     * @param properties
     */
    void mergeWith(Properties properties) {
        minimumValue = Math.max(minimumValue, Float.parseFloat(properties.get(propertyName + ".min").toString()));
        maximumValue = Math.min(maximumValue, Float.parseFloat(properties.get(propertyName + ".max").toString()));
    }


    void setProperties(Properties properties) {
        properties.setProperty(propertyName + ".min", Float.toString(minimumValue));
        properties.setProperty(propertyName + ".max", Float.toString(maximumValue));
    }
}
