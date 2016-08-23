package edu.cornell.med.icb.goby.alignments;

import edu.cornell.med.icb.goby.alignments.htsjdk.HTSJDKReaderImpl;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.Set;

/**
 * The DefaultReaderFactory will delegate to the Goby or HTSJdkReaderFactory according to the extension of the file being read.
 * Created by fac2003 on 5/10/16.
 */
public class DefaultAlignmentReaderFactory extends AlignmentReaderFactoryBase implements AlignmentReaderFactory {
    GobyAlignmentReaderFactory gobyFactory = new GobyAlignmentReaderFactory();
    HTSJdkReaderFactory samBamCramFactory = new HTSJdkReaderFactory();

    @Override
    public AlignmentReader createReader(String basename) throws IOException {
        switch (determineFormat(basename)) {
            case GOBY:
                return gobyFactory.createReader(basename);
            case SAM_BAM_CRAM:
                return samBamCramFactory.createReader(basename);
            default:
                throw new UnsupportedOperationException("None of the factories could read the input file: " + basename);
        }
    }

    @Override
    public AlignmentReader createReader(String basename, boolean upgrade) throws IOException {
        switch (determineFormat(basename)) {
            case GOBY:
                return gobyFactory.createReader(basename, upgrade);
            case SAM_BAM_CRAM:
                return samBamCramFactory.createReader(basename, upgrade);
            default:
                throw new UnsupportedOperationException("None of the factories could read the input file: " + basename);
        }
    }

    @Override
    public AlignmentReader[] createReaderArray(int numElements) throws IOException {
        return new AlignmentReader[numElements];
    }

    @Override
    public AlignmentReader createReader(String basename, int startReferenceIndex, int startPosition, int endReferenceIndex, int endPosition) throws IOException {
        switch (determineFormat(basename)) {
            case GOBY:
                return gobyFactory.createReader(basename, startReferenceIndex, startPosition, endReferenceIndex, endPosition);
            case SAM_BAM_CRAM:
                return samBamCramFactory.createReader(basename, startReferenceIndex, startPosition, endReferenceIndex, endPosition);
            default:
                throw new UnsupportedOperationException("None of the factories could read the input file: " + basename);
        }
    }

    @Override
    public AlignmentReader createReader(String basename, GenomicRange range) throws IOException {
        switch (determineFormat(basename)) {
            case GOBY:
                return gobyFactory.createReader(basename, range);
            case SAM_BAM_CRAM:
                return samBamCramFactory.createReader(basename, range);
            default:
                throw new UnsupportedOperationException("None of the factories could read the input file: " + basename);
        }
    }

    @Override
    public String[] getBasenames(String[] inputFilenames) {
        Set<String> basenames = new ObjectArraySet<>();

        for (String inputFilename : inputFilenames) {
            String[] names = new String[1];
            names[0] = inputFilename;
            switch (determineFormat(inputFilename)) {
                case GOBY:
                    basenames.add(gobyFactory.getBasenames(names)[0]);
                    break;
                case SAM_BAM_CRAM:
                    basenames.add(samBamCramFactory.getBasenames(names)[0]);
                    break;
                default:
          //          throw new UnsupportedOperationException("None of the factories could read the input file: " + inputFilename);
            }
        }
        return basenames.toArray(new String[basenames.size()]);
    }

    @Override
    public AlignmentReader createReader(String basename, long startOffset, long endOffset) throws IOException {
        switch (determineFormat(basename)) {
            case GOBY:
                return gobyFactory.createReader(basename, startOffset, endOffset);
            case SAM_BAM_CRAM:
                return samBamCramFactory.createReader(basename, startOffset, endOffset);
            default:
                throw new UnsupportedOperationException("None of the factories could read the input file: " + basename);
        }
    }

    private enum AlignmentFormat {
        GOBY,
        SAM_BAM_CRAM, OTHER
    }

    ;


    public AlignmentFormat determineFormat(String basename) {
        if (AlignmentReaderImpl.canRead(basename)) {
            return AlignmentFormat.GOBY;
        } else {
            if (HTSJDKReaderImpl.canRead(basename)) {
                return AlignmentFormat.SAM_BAM_CRAM;
            }
        }
        return AlignmentFormat.OTHER;
    }
}
