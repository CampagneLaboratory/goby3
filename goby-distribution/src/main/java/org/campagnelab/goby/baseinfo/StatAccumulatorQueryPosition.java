package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

/**
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorQueryPosition extends StatAccumulator {
    public StatAccumulatorQueryPosition() {
        super("stats.queryPosition", null);
    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList())
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                for (BaseInformationRecords.NumberWithFrequency v : count.getQueryPositionsList()) {
                    observe(v.getNumber());
                }

            }
    }
}
