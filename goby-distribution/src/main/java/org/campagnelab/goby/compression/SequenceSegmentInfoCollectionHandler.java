package org.campagnelab.goby.compression;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import org.apache.commons.io.IOUtils;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A collection handler to parse sequence segment information.
 * @author manuele
 */
public class SequenceSegmentInfoCollectionHandler implements ProtobuffCollectionHandler {
    /**
     * Returns the type of the collection elements.
     *
     * @return One of the pre-defined types, TYPE_READS or TYPE_ALIGNMENTS.
     */
    @Override
    public int getType() {
        return TYPE_SEQUENCE_SEGMENT_INFO;
    }

    @Override
    public GeneratedMessage parse(InputStream uncompressedStream) throws IOException {
        final byte[] bytes = IOUtils.toByteArray(uncompressedStream);
        final CodedInputStream codedInput = CodedInputStream.newInstance(bytes);
        codedInput.setSizeLimit(Integer.MAX_VALUE);
        SegmentInformationRecords.SegmentInformationCollection.Builder builder =
                SegmentInformationRecords.SegmentInformationCollection.newBuilder();
        return builder.mergeFrom(codedInput).build();
    }

    /**
     * Transform a collection to a stream of compressed bits, and return the left-over collection.
     *
     * @param readCollection collection to compress
     * @param compressedBits stream of compressed bits.
     * @return left over collection.
     * @throws IOException
     */
    @Override
    public Message compressCollection(Message readCollection, ByteArrayOutputStream compressedBits) throws IOException {
        return readCollection;
    }

    /**
     * Take a left-over collection and a stream of compressed bits and reconstitute the original collection.
     *
     * @param reducedProtoBuff
     * @param compressedBytes
     * @return
     * @throws IOException
     */
    @Override
    public Message decompressCollection(Message reducedProtoBuff, byte[] compressedBytes) throws IOException {
        return reducedProtoBuff;
    }

    @Override
    public void setUseTemplateCompression(boolean useTemplateCompression) {

    }
}
