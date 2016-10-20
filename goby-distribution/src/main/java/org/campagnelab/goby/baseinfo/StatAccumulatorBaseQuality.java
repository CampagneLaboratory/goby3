package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;

/**
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorBaseQuality extends StatAccumulator {
    protected float minimumValueQualForward;
    protected float maximumValueQualForward;

    protected float minimumValueQualReverse;
    protected float maximumValueQualReverse;
    private String propertyName;

    public StatAccumulatorBaseQuality() {
        super(null);
    }

    @Override
    void setProperties(Properties properties) {
        propertyName = "stats.basequality.forward";
        properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualForward));
        properties.setProperty(propertyName + ".max", Float.toString(maximumValueQualForward));

        propertyName = "stats.basequality.reverse";
        properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualReverse));
        properties.setProperty(propertyName + ".max", Float.toString(maximumValueQualReverse));

    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList()) {
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getQualityScoresForwardStrandList()) {
                    minimumValueQualForward = Math.min(minimumValueQualForward, freqvalue.getNumber());
                }
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getQualityScoresReverseStrandList()) {
                    minimumValueQualReverse = Math.min(minimumValueQualReverse, freqvalue.getNumber());
                }
            }

        }
    }
}
