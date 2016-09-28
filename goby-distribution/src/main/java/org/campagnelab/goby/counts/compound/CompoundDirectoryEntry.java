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

/**
 * Describe class here.
 *
 * @author Kevin Dorff
 */
public class CompoundDirectoryEntry {
    /** The name of the file. */
    private final String name;

    /** The position where the file starts. */
    private final long startPosition;

    /** The position where file's data starts. */
    private final long dataPosition;

    /** The size of the file. */
    private long fileSize;

    /**
     * Create a CompoundDirectoryEntry. This is created by CompoundFileReader.
     * @param nameVal The name of the file
     * @param startPositionVal The position where the file starts
     * @param dataPositionVal The position where file's data starts
     * @param fileSizeVal The size of the file
     */
    CompoundDirectoryEntry(
            final String nameVal, final long startPositionVal,
            final long dataPositionVal, final long fileSizeVal) {
        this.name = nameVal;
        this.startPosition = startPositionVal;
        this.dataPosition = dataPositionVal;
        this.fileSize = fileSizeVal;
    }

    /**
     * Create a CompoundDirectoryEntry. This is created by CompoundFileWriter.
     * @param nameVal The name of the file
     * @param startPositionVal The position where the file starts
     * @param dataPositionVal The position where file's data starts
     */
    CompoundDirectoryEntry(
            final String nameVal, final long startPositionVal,
            final long dataPositionVal) {
        this.name = nameVal;
        this.startPosition = startPositionVal;
        this.dataPosition = dataPositionVal;
        this.fileSize = -1;
    }

    /**
     * Get the name of the file.
     * @return The name of the file
     */
    public String getName() {
        return name;
    }

    /**
     * Get the position where the file starts.
     * @return The position where the file starts
     */
    public long getStartPosition() {
        return startPosition;
    }

    /**
     * Get the position where file's data starts.
     * @return the position where the file's data starts
     */
    public long getDataPosition() {
        return dataPosition;
    }

    /**
     * Get the size of the file.
     * @return the size of the file.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Set the size of the file.
     * @param fileSize the size of the file.
     */
    public void setFileSize(final long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * String representation of this object.
     * @return String representation of this object
     */
    @Override
    public String toString() {
        return String.format("Name:%s, startPosition=%d, dataPosition=%d, fileSize=%d",
                name, startPosition, dataPosition, fileSize);
    }
}
