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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;

/**
 * A DataOutput object that also supports writeObject.
 * TODO: Work on reverting back to thread safe version!
 * TODO: If an EOF is reached while reading mutiple bytes in one operation from the delegate inputstream, this class
 * may just throw EOFException without reading any bytes at all. This is not the behavior expected from DataInputStream
 *
 * @author Kevin Dorff
 */
public class CompoundDataInput implements DataInput {

    /**
     * Used to log debug and informational messages.
     */
    private static final Log LOG = LogFactory.getLog(CompoundDataInput.class);

    /**
     * The delegate DataInput object.
     */
    private final RandomAccessFile dataInput;
    private long fileSize;

    /**
     * Create a CompoundDataInput. This is created by CompoundFileReader.
     *
     * @param input the current reader stream
     */
    CompoundDataInput(final RandomAccessFile input, final long fileSize) {
        this.dataInput = input;
        this.fileSize = fileSize;
    }

    /**
     * {@inheritDoc}
     */
    public void readFully(final byte[] b) throws IOException {
        fileSize -= b.length;
        if (fileSize < 0) {
            throw new EOFException();
        }
        dataInput.readFully(b);
    }

    /**
     * {@inheritDoc}
     */
    public void readFully(final byte[] b, final int off, final int len) throws IOException {
        fileSize -= len;
        if (fileSize < 0) {
            throw new EOFException();
        }

        dataInput.readFully(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    public int skipBytes(final int n) throws IOException {
        fileSize -= n;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.skipBytes(n);
    }

    /**
     * {@inheritDoc}
     */
    public boolean readBoolean() throws IOException {
        fileSize -= 1;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readBoolean();
    }

    /**
     * {@inheritDoc}
     */
    public byte readByte() throws IOException {
        --fileSize;
        if (fileSize < 0) {
            throw new EOFException();
        }
        else {
            return dataInput.readByte();
        }
    }

    /**
     * {@inheritDoc}
     */
    public int readUnsignedByte() throws IOException {
        --fileSize;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readUnsignedByte();
    }

    /**
     * {@inheritDoc}
     */
    public short readShort() throws IOException {
        fileSize -= 2;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readShort();
    }

    /**
     * {@inheritDoc}
     */
    public int readUnsignedShort() throws IOException {
        fileSize -= Short.SIZE;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readUnsignedShort();
    }

    /**
     * {@inheritDoc}
     */
    public char readChar() throws IOException {
        fileSize -= 1;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readChar();
    }

    /**
     * {@inheritDoc}
     */
    public int readInt() throws IOException {

        fileSize -= 4;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readInt();
    }

    /**
     * {@inheritDoc}
     */
    public long readLong() throws IOException {
        fileSize -= 8;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readLong();
    }

    /**
     * {@inheritDoc}
     */
    public float readFloat() throws IOException {
        fileSize -= 4;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readFloat();
    }

    /**
     * {@inheritDoc}
     */
    public double readDouble() throws IOException {
        fileSize -= 8;
        if (fileSize < 0) {
            throw new EOFException();
        }
        return dataInput.readDouble();
    }

    /**
     * {@inheritDoc}
     */

    public String readLine() throws IOException {
        final StringBuffer line = new StringBuffer();
        byte b = -1;
        while (b != '\n') {
            b = readByte();
            line.append((char) b);
        }
        return line.toString();

    }

    /**
     * {@inheritDoc}
     */
    public String readUTF() throws IOException {
        final String token;

        // peek ahead to determine the length of the String:

        final long position=dataInput.getChannel().position();
        final int stringLength = readShort();
        dataInput.seek(position);
        fileSize -= stringLength;
        if (fileSize < 0) {
            throw new EOFException();
        }

        token = dataInput.readUTF();
        return token;

    }

    public long length() throws IOException {
        return Math.min(fileSize, dataInput.length());
    }

    /**
     * Read an object from the current stream position.
     *
     * @return the object
     * @throws IOException            error reading the object
     * @throws ClassNotFoundException error de-serializing the object
     */
    public Object readObject() throws IOException, ClassNotFoundException {
        final int size = readInt();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Reading an object that should be " + (size + 4) + " bytes long");
        }
        final byte[] buf = new byte[size];
        readFully(buf);
        final ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        final ObjectInputStream ois = new ObjectInputStream(bis);
        final Object deserializedObject = ois.readObject();
        ois.close();
        return deserializedObject;
    }
}
