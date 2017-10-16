import argparse
import json
import sys
import warnings

from goby.SequenceSegmentInformation import SequenceSegmentInformationGenerator

# TODO: might want to pregenerate and cache numpy arrays to use for training; could do this here or in another file
# TODO: check in for proper handling of


def preprocess_ssi(input_files):
    max_base_count = -sys.maxsize
    max_feature_count = None
    max_label_count = None
    for input_file in input_files:
        ssi_record_generator = SequenceSegmentInformationGenerator(input_file)
        for segment_info in ssi_record_generator:
            sample_check = None
            for segment_info_sample in segment_info.sample:
                if sample_check is None:
                    sample_check = segment_info_sample
                else:
                    sample_check = None
            if sample_check is None:
                print("Segment in SSI at {}:{} has more than one sample".format(
                    segment_info.start_position, segment_info.end_position))
            for sample_idx, sample in enumerate(segment_info.sample, 1):
                base_count = 0
                for base_idx, base in enumerate(sample.base, 1):
                    feature_count = 0
                    label_count = 0
                    base_count += 1
                    for _ in base.features:
                        feature_count += 1
                    for _ in base.labels:
                        label_count += 1
                    if max_feature_count is None:
                        max_feature_count = feature_count
                    if feature_count != max_feature_count:
                        if feature_count > max_feature_count:
                            max_feature_count = feature_count
                        warnings.warn("Segment info at {}:{}, sample {}, base {} has mismatched feature count".format(
                            segment_info.start_position,
                            segment_info.end_position,
                            sample_idx,
                            base_idx
                        ))
                    if max_label_count is None:
                        max_label_count = label_count
                    if label_count != max_label_count:
                        if label_count > max_label_count:
                            max_label_count = label_count
                        warnings.warn("Segment info at {}:{}, sample {}, base {} has mismatched label count".format(
                            segment_info.start_position,
                            segment_info.end_position,
                            sample_idx,
                            base_idx
                        ))
                if base_count > max_base_count:
                    max_base_count = base_count

    return {
        "max_base_count": max_base_count,
        "max_feature_count": max_feature_count,
        "max_label_count": max_label_count,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--inputs", type=str, required=True, nargs="+", help="SSI files to preprocess.")
    parser.add_argument("--i-json", type=str, required=True, help="Pr")
    args = parser.parse_args()
    input_ssi_json = preprocess_ssi(args.inputs)
    with open(args.output, "w") as out_f:
        json.dump(input_ssi_json, out_f, indent=2)
