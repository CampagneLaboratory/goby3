package org.campagnelab.goby.alignments;

import java.io.IOException;

/**
 * Created by fac2003 on 5/10/16.
 */
public abstract class AlignmentReaderFactoryBase implements AlignmentReaderFactory {


    public AlignmentReader[] createReaderArray(int numElements) throws IOException {
        return new AlignmentReaderImpl[numElements];
    }

    public AlignmentReader createReader(String basename, int startReferenceIndex,
                                        int startPosition, int endReferenceIndex, int endPosition) throws IOException {
        return new AlignmentReaderImpl(basename, startReferenceIndex, startPosition, endReferenceIndex, endPosition, false);
    }

    @Override
    public AlignmentReader createReader(String basename, GenomicRange range) throws IOException {
        if (range == null) {
            return createReader(basename);
        } else {
            return createReader(basename, range.startReferenceIndex, range.startPosition,
                    range.endReferenceIndex, range.endPosition);
        }
    }

    @Override
    public FileSlice getSlice(String basename, GenomicRange range) throws IOException {
        return FileSlice.getSlice(this, basename, range);
    }

    @Override
    public AlignmentReader[] createReader(String[] basenames, boolean upgrade) throws IOException {
        AlignmentReader[] array = createReaderArray(basenames.length);
        int i = 0;
        for (String basename : basenames) {
            array[i++] = createReader(basename);
        }
        return array;
    }

    /**
     * Base implementation follows the Goby alignment conventions for basenames. Override this method
     * if you need something else.
     *
     * @param inputFilenames filenames of input alignments, which may include extensions.
     * @return the basenames reduced after removing extensions.
     */

    @Override
    public String[] getBasenames(String[] inputFilenames) {
        return AlignmentReaderImpl.getBasenames(inputFilenames);
    }
}
