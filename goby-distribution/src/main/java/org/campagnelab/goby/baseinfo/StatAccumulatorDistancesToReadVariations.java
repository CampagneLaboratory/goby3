package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;

/**
 * Store the range of distances to read variations from position.
 * Created by rct66 on 12/29/16.
 */
public class StatAccumulatorDistancesToReadVariations extends StatAccumulator {
    public static final String STATS_DISTANCES_FORWARD = "stats.distancesToReadVariations.forward";
    public static final String STATS_DISTANCES_REVERSE = "stats.distancesToReadVariations.reverse";

    protected float minimumValueDistForward = Float.POSITIVE_INFINITY;
    protected float maximumValueDistForward = Float.NEGATIVE_INFINITY;

    protected float minimumValueDistReverse = Float.POSITIVE_INFINITY;
    protected float maximumValueDistReverse = Float.NEGATIVE_INFINITY;

    private String propertyName;

    public StatAccumulatorDistancesToReadVariations() {
        super(null,null);
    }


    @Override
    void setProperties(Properties properties) {
        propertyName = STATS_DISTANCES_FORWARD;
        if (isDefined(minimumValueDistForward))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueDistForward));
        if (isDefined(maximumValueDistForward))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueDistForward));

        propertyName = STATS_DISTANCES_REVERSE;
        if (isDefined(minimumValueDistReverse))
            properties.setProperty(propertyName + ".min", Float.toString(minimumValueDistReverse));
        if (isDefined(maximumValueDistReverse))
            properties.setProperty(propertyName + ".max", Float.toString(maximumValueDistReverse));

    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList()) {
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getDistancesToReadVariationsForwardStrandList()) {
                    minimumValueDistForward = Math.min(minimumValueDistForward, freqvalue.getNumber());
                    maximumValueDistForward = Math.max(maximumValueDistForward, freqvalue.getNumber());
                }
                for (BaseInformationRecords.NumberWithFrequency freqvalue : count.getDistancesToReadVariationsReverseStrandList()) {
                    minimumValueDistReverse = Math.min(minimumValueDistReverse, freqvalue.getNumber());
                    maximumValueDistReverse = Math.max(maximumValueDistReverse, freqvalue.getNumber());
                }
            }
        }
    }

    @Override
    void mergeWith(Properties properties) {
        if (properties.get(STATS_DISTANCES_FORWARD + ".min") != null)
            minimumValueDistForward = Math.min(minimumValueDistForward, Float.parseFloat(properties.get(STATS_DISTANCES_FORWARD + ".min").toString()));
        if (properties.get(STATS_DISTANCES_REVERSE + ".min") != null)
            minimumValueDistReverse = Math.min(minimumValueDistReverse, Float.parseFloat(properties.get(STATS_DISTANCES_REVERSE + ".min").toString()));
        if (properties.get(STATS_DISTANCES_FORWARD + ".max") != null)
            maximumValueDistForward = Math.max(maximumValueDistForward, Float.parseFloat(properties.get(STATS_DISTANCES_FORWARD + ".max").toString()));
        if (properties.get(STATS_DISTANCES_REVERSE + ".max") != null)
            maximumValueDistReverse = Math.max(maximumValueDistReverse, Float.parseFloat(properties.get(STATS_DISTANCES_REVERSE + ".max").toString()));

    }
}
