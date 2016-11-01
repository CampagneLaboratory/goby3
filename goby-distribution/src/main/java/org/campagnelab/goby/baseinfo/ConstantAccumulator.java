package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * A class to hold a (comparable) value observed over an entire sbi file. The value is written to the .sbip file
 * using the property name: propertyName . Enforces that the value remains constant for every record.
 */
public class ConstantAccumulator extends StatAccumulator {
    public ConstantAccumulator(String propertyName, Function<? super BaseInformationRecords.BaseInformation, ? extends Float> valueGetter) {
        super(propertyName, valueGetter);

    }

    void observe(float value) {
        super.observe(value);
        if (Float.compare(minimumValue, maximumValue) != 0) {
            throw new RuntimeException("Invalid value encountered. The value is not a constant: minimum and maximum differ");
        }
    }

}
