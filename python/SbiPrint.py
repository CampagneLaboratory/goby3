#!/usr/bin/env python3

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

import getopt
import os
import stat
import sys

from google.protobuf import text_format
from tensorflow.python.platform.tf_logging import flush

from goby.SequenceBaseInformation import SequenceBaseInformationGenerator

def usage():
    print("usage:", sys.argv[0], "[-h|--help] [-v|--verbose] <basename.sbi>")


def main():
    verbose = False

    try:
        opts, args = getopt.getopt(sys.argv[1:], "hv", ["help", "verbose"])
    except getopt.GetoptError as err:
        print(str(err),file=sys.stderr)
        usage()
        sys.exit(1)

    # Collect options
    for opt, arg in opts:
        if opt in ("-h", "--help"):
            usage()
            sys.exit()
        elif opt in ("-v", "--verbose"):
            verbose = True

    if len(args) != 1:
        usage()
        sys.exit(2)

    filename = args[0]
    print("sbi filename = %s" % filename)
    print()
    number_of_entries = 0

    records = SequenceBaseInformationGenerator(filename)
    for record in records:
        number_of_entries += 1
        print(record)



if __name__ == "__main__":
    main()
