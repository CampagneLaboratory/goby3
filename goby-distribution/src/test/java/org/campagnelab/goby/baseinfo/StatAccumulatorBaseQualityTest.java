package org.campagnelab.goby.baseinfo;

import com.google.protobuf.TextFormat;
import org.apache.commons.collections.functors.TruePredicate;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/**
 * Created by fac2003 on 10/20/16.
 */
public class StatAccumulatorBaseQualityTest {

    @Test
    public void testObserve() throws Exception {
        StatAccumulatorNumVariationsInRead bq = new StatAccumulatorNumVariationsInRead();
        Properties p = new Properties();
        bq.observe(12);
        bq.observe(15);
        bq.setProperties(p);
        assertEquals("{stats.numVariationsInRead.max=15.0, stats.numVariationsInRead.min=12.0}", p.toString());

    }
}