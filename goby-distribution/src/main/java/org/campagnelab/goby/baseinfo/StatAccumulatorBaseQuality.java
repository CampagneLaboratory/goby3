package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;

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
        properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualForward));
        properties.setProperty(propertyName + ".max", Float.toString(maximumValueQualForward));

        propertyName = STATS_BASEQUALITY_REVERSE;
        properties.setProperty(propertyName + ".min", Float.toString(minimumValueQualReverse));
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
        minimumValueQualForward = Math.max(minimumValueQualForward, Float.parseFloat(properties.get(STATS_BASEQUALITY_FORWARD + ".min").toString()));
        minimumValueQualReverse = Math.max(minimumValueQualReverse, Float.parseFloat(properties.get(STATS_BASEQUALITY_REVERSE + ".min").toString()));

        maximumValueQualForward = Math.min(maximumValueQualForward, Float.parseFloat(properties.get(STATS_BASEQUALITY_FORWARD + ".max").toString()));
        maximumValueQualReverse = Math.min(maximumValueQualReverse, Float.parseFloat(properties.get(STATS_BASEQUALITY_REVERSE + ".max").toString()));

    }
}
