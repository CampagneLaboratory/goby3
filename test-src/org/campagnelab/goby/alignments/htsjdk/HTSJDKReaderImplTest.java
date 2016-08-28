package org.campagnelab.goby.alignments.htsjdk;

import org.campagnelab.goby.alignments.Alignments;
import htsjdk.samtools.Defaults;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Exercise HTSJDKReaderImpl to read SAM/BAM/CRAM with the Goby API.
 * Created by fac2003 on 5/8/16.
 */
public class HTSJDKReaderImplTest {
    @Test
    public void testReadingSimple() throws IOException {
        //   String filename = "test-data/bam/Example.bam";
        String filename = "test-data/sam/test.sam";

        HTSJDKReaderImpl reader = new HTSJDKReaderImpl(filename);
        assertFalse(reader.isIndexed());
        assertFalse(reader.isSorted());
        int count = 0;
        while (reader.hasNext()) {
            Alignments.AlignmentEntry next = reader.next();
            System.out.println(next);
            assertNotNull(next);
            count++;
        }

        assertEquals(2, count);

    }
    @Test
    public void testReadingBAM() throws IOException {
        //   String filename = "test-data/bam/Example.bam";
        String filename = "test-data/bam/Example-sorted.bam";

        HTSJDKReaderImpl reader = new HTSJDKReaderImpl(filename);
        assertTrue(reader.isIndexed());
        assertTrue(reader.isSorted());
        int count = 0;
        while (reader.hasNext()) {
            Alignments.AlignmentEntry next = reader.next();

            assertNotNull(next);
            count++;
        }

        assertEquals(1969065, count);

    }
    @Test
    public void testWithPosition() throws IOException {
        //   String filename = "test-data/bam/Example.bam";
        String filename = "test-data/bam/Example-sorted.bam";

        HTSJDKReaderImpl reader = new HTSJDKReaderImpl(filename);
        assertTrue(reader.isIndexed());
        assertTrue(reader.isSorted());
        int count = 0;

        final int gobyPositionZeroBased = 1698 - 1;
        reader.reposition(0, gobyPositionZeroBased);
        int index = 0;
        while (reader.hasNext()) {
            Alignments.AlignmentEntry next = reader.next();
            assertNotNull(next);
            switch (index++) {
                case 0:
                    assertEquals("position must match after reposition", gobyPositionZeroBased, next.getPosition());
                    break;
                case 1:
                    assertEquals("position must match after reposition", 1702 - 1, next.getPosition());
                    break;
            }
            count++;
        }

        assertEquals(1969024, count);

    }

    @Test
    public void testWithSlices() throws IOException {
        //   String filename = "test-data/bam/Example.bam";
        String filename = "test-data/bam/Example-sorted.bam";
        final int gobyPositionZeroBased = 1698 - 1;
        HTSJDKReaderImpl reader = new HTSJDKReaderImpl(filename, 0, gobyPositionZeroBased, 0, gobyPositionZeroBased + 100);
        assertTrue(reader.isIndexed());
        assertTrue(reader.isSorted());
        int count = 0;

        int index = 0;
        while (reader.hasNext()) {
            Alignments.AlignmentEntry next = reader.next();
            assertNotNull(next);
            count++;
        }

        assertEquals("There are only 5 records between positions 1698 and 1798 on chr17 in the file.",5, count);


    }
/*
    @Test
    public void testReadingSimpleCRAM() throws IOException {
        //   String filename = "test-data/bam/Example.bam";
        String filename = "/Users/fac2003/Downloads/HG00102.mapped.ILLUMINA.bwa.GBR.exome.20121211.bam.cram";


        HTSJDKReaderImpl reader = new HTSJDKReaderImpl(filename);
        assertFalse(reader.isIndexed());
        assertFalse(reader.isSorted());
        int count = 0;
        while (reader.hasNext()) {
            Alignments.AlignmentEntry next = reader.next();
            System.out.println(next);
            assertNotNull(next);
            count++;
        }

        assertEquals(2, count);

    }*/
}