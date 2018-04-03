package org.campagnelab.goby.baseinfo;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;

import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Collect the names of chromosomes/references ids to show this in the .sbip file.
 */
public class ReferenceIdStatAccumulator extends StatAccumulator {
    public ReferenceIdStatAccumulator() {
        super(null, null);
    }

    Set<String> referenceIds = new ObjectArraySet<>();

    @Override
    void observe(BaseInformationRecords.BaseInformation record) {

        referenceIds.add(record.getReferenceId());
    }

    /**
     * Merge range of the statistics using values in properties.
     *
     * @param properties
     */
    void mergeWith(Properties properties) {
        if (properties.containsKey("referenceIds")) {
            String otherIds = properties.get("referenceIds").toString();
            for (String id : otherIds.split(" ")) {
                referenceIds.add(id);
            }

        }

    }

    void setProperties(Properties properties) {
        if (minimumValue != Float.POSITIVE_INFINITY) {
            properties.setProperty("referenceIds", String.join(" ", referenceIds));
        }
    }
}
