/*
 * Copyright (C) 2009-2010 Institute for Computational Biomedicine,
 *                    Weill Medical College of Cornell University
 *
 *  This file is part of the Goby IO API.
 *
 *     The Goby IO API is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     The Goby IO API is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with the Goby IO API.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.campagnelab.goby.counts.compound;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;

/**
 * A DataOutput object that also supports writeObject.
 * TODO: Work on reverting back to thread safe version!
 * @author Kevin Dorff
 */
public class CompoundDataOutput implements Closeable, DataOutput {

    /** The delegate DataOutput object. */
    private RandomAccessFile dataOutput;

    /** The related compound file writer, so we can close this compound file. */
    private CompoundFileWriter compoundFileWriter;

    /**
     * Create a CompoundDataOutput. This is created by CompoundFileWriter.
     * @param stream the current reader stream
     * @param writer the CompoundFileWriter that created this
     */
    CompoundDataOutput(final RandomAccessFile stream, final CompoundFileWriter writer) {
        this.dataOutput = stream;
        this.compoundFileWriter = writer;
    }

    /**
     * {@inheritDoc}
     */
    public void write(final int b) throws IOException {
        dataOutput.write(b);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b) throws IOException {
        dataOutput.write(b);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b, final int off, final int len) throws IOException {
        dataOutput.write(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    public void writeBoolean(final boolean v) throws IOException {
        dataOutput.writeBoolean(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeByte(final int v) throws IOException {
        dataOutput.writeByte(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeShort(final int v) throws IOException {
        dataOutput.writeShort(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeChar(final int v) throws IOException {
        dataOutput.writeChar(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeInt(final int v) throws IOException {
        dataOutput.writeInt(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeLong(final long v) throws IOException {
        dataOutput.writeLong(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeFloat(final float v) throws IOException {
        dataOutput.writeFloat(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeDouble(final double v) throws IOException {
        dataOutput.writeDouble(v);
    }

    /**
     * {@inheritDoc}
     */
    public void writeBytes(final String s) throws IOException {
        dataOutput.writeBytes(s);
    }

    /**
     * {@inheritDoc}
     */
    public void writeChars(final String s) throws IOException {
        dataOutput.writeChars(s);
    }

    /**
     * {@inheritDoc}
     */
    public void writeUTF(final String s) throws IOException {
        dataOutput.writeUTF(s);
    }

    /**
     * Write an object to the current stream position.
     * @param objToWrite the object to write
     * @throws IOException error reading the object
     */
    public void writeObject(final Object objToWrite) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(objToWrite);
        out.close();
        // Get the bytes of the serialized object
        final byte[] buf = bos.toByteArray();
        // save the position and length of the serialized object
        dataOutput.writeInt(buf.length);
        dataOutput.write(buf);
    }

    /**
     * Call when finished writing a CompoundFile.
     * @throws IOException problem finishing addFile. The CompoundFile
     * is probably un-usable.
     */
    public void close() throws IOException {
        compoundFileWriter.finishAddFile();
        dataOutput = null;
        compoundFileWriter = null;
    }
}
