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

""" Contains classes that can parse sequence base information data
stored in the .sbi format. See the Goby3 and variation analysis projects
for details about this file format.
"""

import gzip

import goby.SegmentInformationRecords_pb2
import goby.MessageChunks



def SequenceSegmentInformationGenerator(basename, verbose=False):
    """ Generator to parse .ssi files.
    """

    if not basename.endswith(".ssi"):
        basename+=".ssi"

    collection = goby.SegmentInformationRecords_pb2.SegmentInformationCollection()
    for ssi_records in goby.MessageChunks.MessageChunksGenerator(
            basename, collectionContainer=collection):
        for ssi_record in ssi_records.records:
            yield ssi_record
