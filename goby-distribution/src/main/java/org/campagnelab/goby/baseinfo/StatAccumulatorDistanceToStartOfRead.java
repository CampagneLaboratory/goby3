package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

/**
 * Tally statistics to start of read. by fac2003 on 5/16/17.
 */
public class StatAccumulatorDistanceToStartOfRead extends StatAccumulator {
    public StatAccumulatorDistanceToStartOfRead() {
        super("stats.distanceToStartOfRead", null);
    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList())
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency v : count.getDistanceToStartOfReadList()) {
                    observe(v.getNumber());
                }

            }
    }
}
