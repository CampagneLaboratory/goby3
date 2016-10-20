package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;

/**
 * A class to hold the range of a statistics  observed over an entire sbi file. The range is written to the .sbip file
 * using the property name: propertyName.min=minimumValue and propertyName.max=maximumValue
 * Created by fac2003 on 10/20/16.
 */
public abstract class StatAccumulator {
    protected float minimumValue;
    protected float maximumValue;
    private String propertyName;

    public StatAccumulator(String propertyName) {
        this.propertyName = propertyName;
    }

    abstract void observe(BaseInformationRecords.BaseInformation record);

    void setProperties(Properties properties) {
        properties.setProperty(propertyName + ".min", Float.toString(minimumValue));
        properties.setProperty(propertyName + ".max", Float.toString(maximumValue));
    }
}
