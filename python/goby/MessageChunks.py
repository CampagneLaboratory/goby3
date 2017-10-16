#
# Copyright (C) 2010 Institute for Computational Biomedicine,
#                    Weill Medical College of Cornell University
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

""" Support for reading Goby chunked "compact" files. """

import io as io
import os
import struct
from gzip import GzipFile
from io import BytesIO
import sys

import progressbar

def MessageChunksGenerator(filename, collectionContainer=None, fileobject=None, currentIndex=0, file_size=0):
    """
    Return a generator for bytes decompressed from chunks in a gzipped Goby protobuf.
    If you supply a collectionContaine, the bytes are parsed with the protobuf collection
    and the collection is returned instead.
    :param filename: name/path of the file
    :return: bytes or parsed collection, when collectionContainer is provided.
    """
    progress_bar = progressbar.ProgressBar()
    if fileobject is None:
        fileobject = open(filename, "rb")
        file_size = os.path.getsize(filename)
        progress_bar = progressbar.ProgressBar(maxval=file_size)
    progress_bar.start()
    while True:

            DELIMITER_LENGTH = 8
            #  position to point just after the next delimiter
            fileobject.read(DELIMITER_LENGTH)
            # get the number of bytes expected in the next chunk
            num_bytes = read_int(fileobject)
            if num_bytes != 0:
                print('.', end='', flush=True)
                # each chunk is compressed
                buf = fileobject.read(num_bytes)
                currentIndex = fileobject.tell()
                progress_bar.update(currentIndex)
                #  print("file position: ", currentIndex)
                buf = GzipFile("", "rb", 0, fileobj=BytesIO(buf)).read()
                if collectionContainer is not None:
                    collectionContainer.ParseFromString(buf)
                    yield collectionContainer
                else:
                    yield buf
            else:
                fileobject.close()
                break


def read_int(fd):
    """ Python implementation of Java's DataInputStream.readInt method.
    """
    buf = fd.read(4)
    if len(buf) == 4:
        length = struct.unpack('>I', buf)[0]
    else:
        length = 0
    return length
