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
    protected final Function<? super BaseInformationRecords.BaseInformation, ? extends Float> statCalculation;
    protected float minimumValue = Float.MAX_VALUE;
    protected float maximumValue = Float.MIN_VALUE;
    protected String propertyName;

    public StatAccumulator(String propertyName, Function<? super BaseInformationRecords.BaseInformation, ? extends Float> statCalculation) {
        this.propertyName = propertyName;
        this.statCalculation = statCalculation;
    }

    void observe(BaseInformationRecords.BaseInformation record) {
        observe(statCalculation.apply(record));
    }

    void observe(float value) {
        minimumValue = Math.min(minimumValue, value);
        maximumValue = Math.max(maximumValue, value);
    }

    protected boolean isDefined(float v) {
        return v != Float.MAX_VALUE && v != Float.MIN_VALUE;
    }

    /**
     * Merge range of the statistics using values in properties.
     *
     * @param properties
     */
    void mergeWith(Properties properties) {
        if (properties.containsKey(propertyName + ".min"))
            minimumValue = Math.min(minimumValue, Float.parseFloat(properties.get(propertyName + ".min").toString()));
        if (properties.containsKey(propertyName + ".max"))
            maximumValue = Math.max(maximumValue, Float.parseFloat(properties.get(propertyName + ".max").toString()));
    }


    void setProperties(Properties properties) {
        if (minimumValue != Float.MAX_VALUE) {
            properties.setProperty(propertyName + ".min", Float.toString(minimumValue));
        }
        if (maximumValue != Float.MIN_VALUE) {
            properties.setProperty(propertyName + ".max", Float.toString(maximumValue));
        }
    }

}
