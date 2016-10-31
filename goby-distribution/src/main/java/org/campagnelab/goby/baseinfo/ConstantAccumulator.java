package org.campagnelab.goby.baseinfo;

import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * A class to hold a (comparable) value observed over an entire sbi file. The value is written to the .sbip file
 * using the property name: propertyName . Enforces that the value remains constant for every record.
 * Created by fac2003 on 10/20/16.
 */
public class ConstantAccumulator {
    private final Function<? super BaseInformationRecords.BaseInformation, Comparable> valueGetter;
    protected Comparable field;
    private String propertyName;

    public ConstantAccumulator(String propertyName, Function<? super BaseInformationRecords.BaseInformation, Comparable> valueGetter) {
        this.propertyName = propertyName;
        this.valueGetter = valueGetter;
    }

    void observe(BaseInformationRecords.BaseInformation record) throws IOException {
        observe(valueGetter.apply(record));
    }

    void observe(Comparable value) throws IOException {
        if (field == null){
            field = value;
            return;
        }
        if (field != value) {
            throw new IOException("Invalid sequence context size encountered. Did not match previous values");
        }
    }

    protected boolean isDefined(Comparable v) {
        return v != null;
    }

    /**
     * Merge values in properties. Enforces equality.
     *
     * @param properties
     */
    void mergeWith(Properties properties, Function<String, Comparable> valueFromString) throws IOException {
        if (properties.containsKey(propertyName)){
            Comparable otherValue = valueFromString.apply(properties.get(propertyName).toString());
            if (field != null && otherValue != field){
                throw new IOException("Properties files cannot be merged, values: '" + propertyName + "' are not equal");
            }
            field = otherValue;
        }
    }


    void setProperties(Properties properties, Function<Comparable ,String> valueToString) {
        if (field != null) {
            properties.setProperty(propertyName, valueToString.apply(field));
        }

    }
}
