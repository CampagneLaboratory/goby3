package org.campagnelab.goby.compression;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.campagnelab.dl.varanalysis.protobuf.*;
/**
 * A collection handler to parse sequence base information.
 * @author Fabien Campagne
 * Created by fac2003 on 8/27/16.
 */
public class SequenceBaseInfoCollectionHandler implements ProtobuffCollectionHandler {
    @Override
    public int getType() {
        return TYPE_SEQUENCE_BASE_INFO;
    }

    @Override
    public GeneratedMessage parse(final InputStream compressedBytes) throws IOException {
        final byte[] bytes = IOUtils.toByteArray(compressedBytes);
        final CodedInputStream codedInput = CodedInputStream.newInstance(bytes);
        codedInput.setSizeLimit(Integer.MAX_VALUE);
        BaseInformationRecords.BaseInformationCollection.Builder builder = BaseInformationRecords.BaseInformationCollection.newBuilder();
        return builder.mergeFrom(codedInput).build();

    }

    @Override
    public Message compressCollection(Message readCollection, ByteArrayOutputStream compressedBits) {
        return readCollection;
    }

    @Override
    public Message decompressCollection(Message reducedProtoBuff, byte[] compressedBytes) {
        return reducedProtoBuff;
    }

    @Override
    public void setUseTemplateCompression(boolean useTemplateCompression) {

    }
}
