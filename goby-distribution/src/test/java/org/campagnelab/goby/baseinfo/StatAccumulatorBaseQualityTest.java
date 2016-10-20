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


    public void testObserve() throws Exception {
        StatAccumulatorBaseQuality bq = new StatAccumulatorBaseQuality();
        Properties p = new Properties();
        bq.observe(construct(new int[]{15, 12}, new int[]{1, 100}));
        bq.setProperties(p);

    }

    private BaseInformationRecords.BaseInformation construct(int[] forwardQual, int[] reverseQual) throws TextFormat.ParseException {
        BaseInformationRecords.BaseInformation.Builder builder = BaseInformationRecords.BaseInformation.newBuilder();

        BaseInformationRecords.CountInfo.Builder count = BaseInformationRecords.CountInfo.newBuilder();
        count.setMatchesReference(true);

        for (int i : reverseQual) {
            BaseInformationRecords.NumberWithFrequency.Builder nf = BaseInformationRecords.NumberWithFrequency.newBuilder();
            nf.setFrequency(1);
            nf.setNumber(i);
            count.addQualityScoresReverseStrand(nf);
        }


         TextFormat.getParser().merge(record, builder);
        for (int i : forwardQual) {
            BaseInformationRecords.NumberWithFrequency.Builder nf = BaseInformationRecords.NumberWithFrequency.newBuilder();
            nf.setFrequency(1);
            nf.setNumber(i);
            builder.getSamples(0).getCounts(0).toBuilder().addQualityScoresForwardStrand(nf).build();
        }
        for (int i : reverseQual) {
            BaseInformationRecords.NumberWithFrequency.Builder nf = BaseInformationRecords.NumberWithFrequency.newBuilder();
            nf.setFrequency(1);
            nf.setNumber(i);
          //  builder.getSamples(0).getCountsOrBuilder(0).bui.addQualityScoresReverseStrand(nf).build();
        }
        return builder.build();
    }

    String record = "reference_index: 18\n" +
            "position: 17214616\n" +
            "mutated: false\n" +
            "referenceBase: \"A\"\n" +
            "samples {\n" +
            "  counts {\n" +
            "    matchesReference: true\n" +
            "    fromSequence: \"A\"\n" +
            "    toSequence: \"A\"\n" +
            "    genotypeCountForwardStrand: 5\n" +
            "    genotypeCountReverseStrand: 0\n" +
            "  }\n" +
            "  counts {\n" +
            "    matchesReference: false\n" +
            "    fromSequence: \"A\"\n" +
            "    toSequence: \"T\"\n" +
            "    genotypeCountForwardStrand: 5\n" +
            "    genotypeCountReverseStrand: 0\n" +
            "  }\n" +
            "}\n" +
            "samples {\n" +
            "  counts {\n" +
            "    matchesReference: true\n" +
            "    fromSequence: \"A\"\n" +
            "    toSequence: \"A\"\n" +
            "    genotypeCountForwardStrand: 5\n" +
            "    genotypeCountReverseStrand: 0\n" +
            "  }\n" +
            "  counts {\n" +
            "    matchesReference: false\n" +
            "    fromSequence: \"A\"\n" +
            "    toSequence: \"T\"\n" +
            "    genotypeCountForwardStrand: 5\n" +
            "    genotypeCountReverseStrand: 0\n" +
            "  }\n" +

            "} ";

    @Test
    public void testMergeWith() throws Exception {

    }

}