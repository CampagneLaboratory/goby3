package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.function.Function;

/**
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorNumVariationsInRead extends StatAccumulator {
    public StatAccumulatorNumVariationsInRead() {
        super("stats.numVariationsInRead", null);
    }

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {
        for (BaseInformationRecords.SampleInfo sample : record.getSamplesList())
            for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                observe(count.getNumVariationsInReadsCount());
            }
    }
}
