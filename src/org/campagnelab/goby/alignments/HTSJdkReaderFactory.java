package org.campagnelab.goby.alignments;

import org.campagnelab.goby.alignments.htsjdk.HTSJDKReaderImpl;

import java.io.IOException;

/**
 * A factory that reads alignments with the HTS JDK implementation.
 * Created by fac2003 on 5/10/16.
 */
public class HTSJdkReaderFactory  extends AlignmentReaderFactoryBase  implements AlignmentReaderFactory {
    @Override
    public AlignmentReader createReader(String basename) throws IOException {
        return new HTSJDKReaderImpl(basename);
    }

    @Override
    public AlignmentReader createReader(String basename, boolean upgrade) throws IOException {
        return new HTSJDKReaderImpl(basename);
    }

    @Override
    public AlignmentReader[] createReaderArray(int numElements) throws IOException {
        return new HTSJDKReaderImpl[numElements];
    }

    @Override
    public AlignmentReader createReader(String basename, int startReferenceIndex, int startPosition, int endReferenceIndex, int endPosition) throws IOException {
        return new HTSJDKReaderImpl(basename, startReferenceIndex, startPosition, endReferenceIndex, endPosition);
    }

    @Override
    public String[] getBasenames(String[] inputFilenames) {
        return HTSJDKReaderImpl.getBasenames(inputFilenames);
    }

    @Override
    public AlignmentReader createReader(String basename, long startOffset, long endOffset) throws IOException {
        return new HTSJDKReaderImpl(basename, startOffset, endOffset);
    }

}
