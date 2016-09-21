package org.campagnelab.goby.modes;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Created by fac2003 on 5/10/16.
 */
public class ConcatenateAlignmentModeTest {
    @Test
    public void execute() throws Exception {
        ConcatenateAlignmentMode concatMode = new ConcatenateAlignmentMode();
        concatMode.configure("--mode ca test-data/bam/Example.bam test-data/bam/Example2.bam -o test-results/dup".split(" "));
        concatMode.execute();
        assertTrue(new File("test-results/dup.entries").exists());
    }

}