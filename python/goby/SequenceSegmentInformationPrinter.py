import argparse

from goby.SequenceSegmentInformation import SequenceSegmentInformationGenerator


def print_segment_info(ssi, bases_to_print, print_all_bases):
    print("Start position: reference index {}, reference id {}, location {}".format(ssi.start_position.reference_index,
                                                                                    ssi.start_position.reference_id,
                                                                                    ssi.start_position.location))
    print("End position: reference index {}, reference id {}, location {}".format(ssi.end_position.reference_index,
                                                                                  ssi.end_position.reference_id,
                                                                                  ssi.end_position.location))
    print("Length: {}".format(ssi.length))
    print("\n")
    bases_printed = 0
    for sample in ssi.sample:
        for base_idx, base in enumerate(sample.base, 1):
            if bases_printed < bases_to_print or print_all_bases:
                print("Base number: {}".format(base_idx))
                print("Features: {}".format(", ".join(map(str, base.features))))
                print("Labels: {}".format(", ".join(map(str, base.labels))))
                print("Color: {}".format(", ".join(map(str, base.color))))
                print("TrueLabel: {}".format(", ".join(map(str, base.trueLabel))))
                print("HasCandidateIndel: {}".format(base.hasCandidateIndel))
                print("HasTrueIndel: {}".format(base.hasTrueIndel))
                print("\n")
            bases_printed += 1
    print("\n\n")


def main(args):
    ssi_generator = SequenceSegmentInformationGenerator(args.input)
    segments_printed = 0
    for segment_info in ssi_generator:
        if segments_printed < args.segments_to_print or args.print_all_segments:
            print_segment_info(segment_info, args.bases_to_print, args.print_all_bases)
        segments_printed += 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--input", type=str, required=True, help="SSI input file.")
    parser.add_argument("-b", "--bases-to-print", type=int, default=1, help="Number of bases to print for each SSI.")
    parser.add_argument("--print-all-bases", dest="print_all_bases", help="If set, prints all bases.",
                        action="store_true")
    parser.add_argument("-s", "--segments-to-print", type=int, default=1, help="Number of segments to print.")
    parser.add_argument("--print-all-segments", dest="print_all_segments", help="If set, prints all segments.",
                        action="store_true")
    parser_args = parser.parse_args()
    main(parser_args)
