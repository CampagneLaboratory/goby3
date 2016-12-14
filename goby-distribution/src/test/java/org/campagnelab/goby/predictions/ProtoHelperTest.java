package org.campagnelab.goby.predictions;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fac2003 on 11/18/16.
 */
public class ProtoHelperTest {
    @Test
    public void testGenomicSpan() {
        int contextLength = 3;

        int position = 0;
        String genome = "012345Xabcdef";
        int referenceSequenceLength = genome.length();
        assertEquals("345Xabc", buildContext(7, 6, genome, referenceSequenceLength));
        assertEquals("2345Xabcd", buildContext(9, 6, genome, referenceSequenceLength));
        assertEquals("12345Xabcde", buildContext(11, 6, genome, referenceSequenceLength));
        assertEquals("012345Xabcdef", buildContext(13, 6, genome, referenceSequenceLength));
        assertEquals("N012345XabcdefN", buildContext(15, 6, genome, referenceSequenceLength));
        assertEquals("NN012345Xabcdef", buildContext(15, 5, genome, referenceSequenceLength));
        assertEquals("012345XabcdefNN", buildContext(15, 7, genome, referenceSequenceLength));
    }

    private String buildContext(int contextLength, int position, String genome, int referenceSequenceLength) {
        StringBuffer genomicContext = new StringBuffer();
        int cl = (contextLength - 1) / 2;
        final int genomicStart = position - cl;
        final int genomicEnd = position + (cl + 1);
        int index=0;
        for (int refPos = Math.max(genomicStart, 0); refPos < Math.min(genomicEnd, referenceSequenceLength); refPos++) {
            genomicContext.insert(index++,genome.charAt(refPos));
        }
        //pad zeros as needed
        for (int i = genomicStart; i < 0; i++) {
            genomicContext.insert(0, "N");
        }
        index=genomicContext.length();
        for (int i = genomicEnd; i > referenceSequenceLength; i--) {
            genomicContext.insert(index++,'N');
        }
        assertEquals(contextLength,genomicContext.length());
        return genomicContext.toString();
    }
}