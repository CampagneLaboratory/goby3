/*
 * Copyright (C) 2009-2011 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.campagnelab.goby.algorithmic.algorithm;

//import EquivalentIndelRegion;

import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.campagnelab.goby.reads.RandomAccessSequenceTestSupport;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

/**
 * @author Fabien Campagne
 *         Date: 6/7/11
 *         Time: 6:11 PM
 */
public class TestEquivalentIndelRegionCalculator {
    RandomAccessSequenceTestSupport genome;
    private String[] sequences = {
            "ACTCAAAGACT",  // will insert one A in the three consecutive As
            "AAACAGATCCCACA",  // will insert AG between C and AG
            "GGGGATATATATATACGAGGG",  // will remove AT somewhere between GGGA and CGA
            "AAAACTTGGGG",  // will insert a T in the T's
            "ACACACACACACACACAGAGAGACACACAC",  //
            "AATTGTTTTTTTGTTTGTTTGTTTTTTGA"
    };
    private EquivalentIndelRegionCalculator equivalentIndelRegionCalculator;

    @Test
    public void emptyTest() {
    }

    @Before
    public void setUp() throws Exception {

        genome = new RandomAccessSequenceTestSupport(sequences);
        equivalentIndelRegionCalculator = new EquivalentIndelRegionCalculator(genome);
    }

    @Test
    public void tesSequence0() throws Exception {
        ObservedIndel indel = new ObservedIndel(4,5, "-", "A");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(0, indel);
        // INSERTION in the read:
        assertEquals(0, result.referenceIndex);
        assertEquals(3, result.startPosition);
        assertEquals(7, result.endPosition);
        assertEquals("-AAA", result.from);
        assertEquals("AAAA", result.to);
        assertEquals("ACTC", result.flankLeft);
        assertEquals("GACT", result.flankRight);
        // "ACTCAAAGACT",  // will insert one A in the three consecutive As
    }

    @Test
    public void testSequence1() throws Exception {
        // INSERTION in the read:
        ObservedIndel indel = new ObservedIndel(3, 4, "--", "AG");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(1, indel);
        assertEquals(1, result.referenceIndex);
        assertEquals(3, result.startPosition);
        assertEquals(7, result.endPosition);
        assertEquals("--AGA", result.from);
        assertEquals("AGAGA", result.to);
        assertEquals("AAAC", result.flankLeft);
        assertEquals("TCCC", result.flankRight);
        assertEquals("AAAC--AGATCCC", result.fromInContext());
        assertEquals("AAACAGAGATCCC", result.toInContext());
        // "AAAC  AGATCCC"
        // "AAACAGAGATCCC"
    }

    @Test
    public void testSequence2() throws Exception {
        // DELETION in the read:
        ObservedIndel indel = new ObservedIndel(6, 7, "TA", "--");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(2, indel);
        assertEquals(2, result.referenceIndex);
        assertEquals(3, result.startPosition);
        assertEquals(15, result.endPosition);
        assertEquals("ATATATATATA", result.from);
        assertEquals("--ATATATATA", result.to);
        assertEquals("GGGG", result.flankLeft);
        assertEquals("CGAG", result.flankRight);
        assertEquals("GGGGATATATATATACGAG", result.fromInContext());
        assertEquals("GGGG--ATATATATACGAG", result.toInContext());
        // "GGGGA  TATATATACGAGGG"
        // "GGGGATATATATATACGAGGG" from
        // "GGGGATA--TATATATACGAGGG    to
        //  0123456  78
        //"GGGGATATATATATACGAGGG"


    }


    @Test
    public void testSequence3() throws Exception {
        ObservedIndel indel = new ObservedIndel(4, 5, "-", "T");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(3, indel);
        assertEquals(3, result.referenceIndex);
        assertEquals(4, result.startPosition);
        assertEquals(7, result.endPosition);
        assertEquals("-TT", result.from);
        assertEquals("TTT", result.to);
        assertEquals("AAAC", result.flankLeft);
        assertEquals("GGGG", result.flankRight);
        assertEquals("AAAC-TTGGGG", result.fromInContext());
        assertEquals("AAACTTTGGGG", result.toInContext());


        /**
         * Construct an indel observation.
         *
         * @param startPosition The position where the indel starts, zero-based, position of the base at the left of the first gap.
         * @param endPosition   The position where the indel ends, zero-based, position of the base at the right of the first gap.
         * @param from          Bases in the reference
         * @param to            Bases in the read
         * @param readIndex     Index of the base in the read at the left of where the indel is observed.
         *                      "AAAACTTGGGG"
         */
    }

    @Test
    //alignment test
    public void testSequence4() throws Exception {
        equivalentIndelRegionCalculator.setFlankLeftSize(1);
        equivalentIndelRegionCalculator.setFlankRightSize(0);
        ObservedIndel indel = new ObservedIndel(15,  "----------", "ACACACACAG",4);
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(4, indel);
        assertEquals(4, result.referenceIndex);
        assertEquals(15, result.startPosition);
        assertEquals(25, result.endPosition);
        assertEquals("----------AGAGAGACA", result.from);
        assertEquals("ACACACACAGAGAGAGACA", result.to);
        assertEquals("C", result.flankLeft);
        assertEquals("", result.flankRight);

    }
//
//    @Test
//    //alignment test
//    public void testSequence5() throws Exception {
//        equivalentIndelRegionCalculator.setFlankLeftSize(1);
//        equivalentIndelRegionCalculator.setFlankRightSize(0);
//        ObservedIndel indel = new ObservedIndel(15,  "------------", "ACACACACACAG",4);
//        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(4, indel);
//        assertEquals(4, result.referenceIndex);
//        assertEquals(15, result.startPosition);
//        assertEquals(27, result.endPosition);
//        assertEquals("------------AGAGAGACACA", result.from);
//        assertEquals("ACACACACACAGAGAGAGACACA", result.to);
//        assertEquals("C", result.flankLeft);
//        assertEquals("", result.flankRight);
//
//    }

//    @Test
//    //vcf test
//    public void testSequence5() throws Exception {
//        equivalentIndelRegionCalculator.setFlankLeftSize(1);
//        equivalentIndelRegionCalculator.setFlankRightSize(0);
//        ObservedIndel indel = new ObservedIndel(7,  "-", "ACACACACAG", 4);
//        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(4, indel);
//        assertEquals(4, result.referenceIndex);
//        assertEquals(7, result.startPosition);
//        assertEquals(7, result.endPosition);
//        assertEquals("-TT", result.from);
//        assertEquals("TTT", result.to);
//        assertEquals("AAAC", result.flankLeft);
//        assertEquals("GGGG", result.flankRight);
//        assertEquals("AAAC-TTGGGG", result.fromInContext());
//        assertEquals("AAACTTTGGGG", result.toInContext());
//
//
//        /**
//         * Construct an indel observation.
//         *
//         * @param startPosition The position where the indel starts, zero-based, position of the base at the left of the first gap.
//         * @param endPosition   The position where the indel ends, zero-based, position of the base at the right of the first gap.
//         * @param from          Bases in the reference
//         * @param to            Bases in the read
//         * @param readIndex     Index of the base in the read at the left of where the indel is observed.
//         *                      ""ACACACACAGAGAGACACACAC"  //"
//         */
//    }


    @Test
    //alignment test
    public void testSequence5() throws Exception {
        equivalentIndelRegionCalculator.setFlankLeftSize(1);
        equivalentIndelRegionCalculator.setFlankRightSize(0);
        ObservedIndel indel = new ObservedIndel(9,  "TTTG", "----",5);
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(5, indel);
        assertEquals(5, result.referenceIndex);
        assertEquals(9, result.startPosition);
        assertEquals("TTGTTTGTTTGTTT", result.from);
        assertEquals("----TTGTTTGTTT", result.to);
        assertEquals("T", result.flankLeft);
        assertEquals("", result.flankRight);

    }

    /**
     //         * Construct an indel observation.
     //         *
     //         * @param startPosition The position where the indel starts, zero-based, position of the base at the left of the first gap.
     //         * @param endPosition   The position where the indel ends, zero-based, position of the base at the right of the first gap.
     //         * @param from          Bases in the reference
     //         * @param to            Bases in the read
     //         * @param readIndex     Index of the base in the read at the left of where the indel is observed.
     //         *                      ""AATTGTTTTTTTGTTTGTTTGTTTTTTGA"  //"
     //         */

    @Test
    public void testIndexLargerThanSize() throws Exception {
        // DELETION in the read:
        ObservedIndel indel = new ObservedIndel(600000000, 600000001, "TA", "--");
        EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(2, indel);
        assertNull("indel outside the genome should return null", result);
    }
}
