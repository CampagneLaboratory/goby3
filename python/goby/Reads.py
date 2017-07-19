#
# Copyright (C) 2010-2017 Institute for Computational Biomedicine,
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

""" Contains classes that can parse binary sequence data
stored in the Goby "compact" format.
"""
import struct
from collections import ByteString

import MessageChunks
import goby.Reads_pb2


def CompactReads(basename):
    if not basename.endswith(".compact-reads"):
        basename += ".compact-reads"

    collection = goby.Reads_pb2.ReadCollection()
    for reads in MessageChunks.MessageChunksGenerator(
            basename, collectionContainer=collection):
        for read in reads.reads:
            yield read


def decodeSequence(sequence):

    return str(sequence, "utf-8")
