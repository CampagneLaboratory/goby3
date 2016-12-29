package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;
import java.util.function.Function;

/**
 * Store the range of basequality forward and reverse strand.
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorBaseQuality extends StatAccumulator {
    public static final String STATS_BASEQUALITY_FORWARD = "stats.baseQuality.forward";
    public static final String STATS_BASEQUALITY_REVERSE = "stats.baseQuality.reverse";

    protected float minimumValueQualForward = Float.MAX_VALUE;
    protected float maximumValueQualForward = Float.MIN_VALUE;

    protected float minimumValueQualReverse = Float.MAX_VALUE;
    protected float maximumValueQualReverse = Float.MIN_VALUE;

    private String propertyName;

    public StatAccumulatorBaseQuality() {
        super(null,null);
    }


    @Override
    void setProperties(Properties properties) {
        propertyName = STATS_BASEQUALITY_FORWARD;
        if (isDefined(minimumValueQualForward))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualForward));
        if (isDefined(maximumValueQualForward))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueQualForward));

        propertyName = STATS_BASEQUALITY_REVERSE;
        if (isDefined(minimumValueQualReverse))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualReverse));
        if (isDefined(maximumValueQualReverse))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueQualReverse));

    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList()) {
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getQualityScoresForwardStrandList()) {
                    minimumValueQualForward = Math.min(minimumValueQualForward, freqvalue.getNumber());
                    maximumValueQualForward = Math.max(maximumValueQualForward, freqvalue.getNumber());
                }
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getQualityScoresReverseStrandList()) {
                    minimumValueQualReverse = Math.min(minimumValueQualReverse, freqvalue.getNumber());
                    maximumValueQualReverse = Math.max(maximumValueQualReverse, freqvalue.getNumber());
                }
            }
        }
    }

    @Override
    void mergeWith(Properties properties) {
        if (properties.get(STATS_BASEQUALITY_FORWARD + ".min") != null)
            minimumValueQualForward = Math.min(minimumValueQualForward, Float.parseFloat(properties.get(STATS_BASEQUALITY_FORWARD + ".min").toString()));
        if (properties.get(STATS_BASEQUALITY_REVERSE + ".min") != null)
            minimumValueQualReverse = Math.min(minimumValueQualReverse, Float.parseFloat(properties.get(STATS_BASEQUALITY_REVERSE + ".min").toString()));
        if (properties.get(STATS_BASEQUALITY_FORWARD + ".max") != null)
            maximumValueQualForward = Math.max(maximumValueQualForward, Float.parseFloat(properties.get(STATS_BASEQUALITY_FORWARD + ".max").toString()));
        if (properties.get(STATS_BASEQUALITY_REVERSE + ".max") != null)
            maximumValueQualReverse = Math.max(maximumValueQualReverse, Float.parseFloat(properties.get(STATS_BASEQUALITY_REVERSE + ".max").toString()));

    }
}
