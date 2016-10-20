package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;

/**
 * Store the range of read mapping quality for forward and reverse strands.
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorReadMappingQuality extends StatAccumulator {
    public static final String STATS_READMAPPINGQUALITY_FORWARD = "stats.readMappingQuality.forward";
    public static final String STATS_READMAPPINGQUALITY_REVERSE = "stats.readMappingQuality.forward";

    protected float minimumValueForward = Float.MAX_VALUE;
    protected float maximumValueForward = Float.MIN_VALUE;

    protected float minimumValueReverse = Float.MAX_VALUE;
    protected float maximumValueReverse = Float.MIN_VALUE;

    private String propertyName;

    public StatAccumulatorReadMappingQuality() {
        super(null, null);
    }

    @Override
    void setProperties(Properties properties) {
        propertyName = STATS_READMAPPINGQUALITY_FORWARD;
        if (isDefined(minimumValueForward))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueForward));
        if (isDefined(maximumValueForward))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueForward));

        propertyName = STATS_READMAPPINGQUALITY_REVERSE;
        if (isDefined(minimumValueReverse))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueReverse));
        if (isDefined(maximumValueReverse))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueReverse));

    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList()) {
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getReadMappingQualityForwardStrandList()) {
                    minimumValueForward = Math.min(minimumValueForward, freqvalue.getNumber());
                    maximumValueForward = Math.max(maximumValueForward, freqvalue.getNumber());
                }
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getReadMappingQualityReverseStrandList()) {
                    minimumValueReverse = Math.min(minimumValueReverse, freqvalue.getNumber());
                    maximumValueReverse = Math.max(maximumValueReverse, freqvalue.getNumber());
                }
            }
        }
    }

    @Override
    void mergeWith(Properties properties) {
        if (properties.containsKey(STATS_READMAPPINGQUALITY_FORWARD + ".min"))
            minimumValueForward = Math.max(minimumValueForward, Float.parseFloat(properties.get(STATS_READMAPPINGQUALITY_FORWARD + ".min").toString()));
        if (properties.containsKey(STATS_READMAPPINGQUALITY_REVERSE + ".min"))
            minimumValueReverse = Math.max(minimumValueReverse, Float.parseFloat(properties.get(STATS_READMAPPINGQUALITY_REVERSE + ".min").toString()));

        if (properties.containsKey(STATS_READMAPPINGQUALITY_FORWARD + ".max"))
            maximumValueForward = Math.min(maximumValueForward, Float.parseFloat(properties.get(STATS_READMAPPINGQUALITY_FORWARD + ".max").toString()));
        if (properties.containsKey(STATS_READMAPPINGQUALITY_REVERSE + ".max"))
            maximumValueReverse = Math.min(maximumValueReverse, Float.parseFloat(properties.get(STATS_READMAPPINGQUALITY_REVERSE + ".max").toString()));

    }
}
