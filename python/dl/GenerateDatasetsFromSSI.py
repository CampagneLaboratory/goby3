import argparse
import json
import os
import sys
import warnings

from goby.SequenceSegmentInformation import SequenceSegmentInformationGenerator
from goby.pyjavaproperties import Properties

import numpy as np


def vectorize_segment_info(segment_info, max_base_count, max_feature_count, max_label_count, padding="pre"):
    # Only look at first segment in sample for now
    sample = segment_info.sample[0]
    feature_array = []
    label_array = []
    for base in sample.base:
        feature_array.append(np.pad(np.array(list(base.features)), (0, max_feature_count - len(base.features)),
                                    mode='constant'))
        label_array.append(np.pad(np.array(list(base.labels)), (0, max_label_count - len(base.labels)),
                                  mode='constant'))
    amount_to_pad = max_base_count - len(feature_array)
    padding_shape = ((amount_to_pad, 0), (0, 0)) if padding == "pre" else ((0, amount_to_pad), (0, 0))
    feature_array = np.pad(np.array(feature_array), padding_shape, mode='constant')
    label_array = np.pad(np.array(label_array), padding_shape, mode='constant')
    return feature_array, label_array


def minimal_vectorize_segment(segment_info, padding="pre"):
    num_bases = len(segment_info.sample[0].base)
    num_features_set, num_labels_set = map(frozenset,
                                           zip(*[(len(base.features), len(base.labels))
                                                 for base in segment_info.sample[0].base]))
    feature_mismatch = len(num_features_set) > 1
    label_mismatch = len(num_labels_set) > 1
    num_features = max(num_features_set)
    num_labels = max(num_labels_set)
    return vectorize_segment_info(segment_info, num_bases, num_features, num_labels,
                                  padding), feature_mismatch, label_mismatch


def vectorize_by_mini_batch(segment_info_generator, mini_batch_size, num_segments, padding="pre"):
    segments_processed_in_batch = 0
    segments_processed_total = 0
    feature_mismatch = False
    label_mismatch = False
    max_bases_mini_batch = -sys.maxsize
    max_features_mini_batch = -sys.maxsize
    max_labels_mini_batch = -sys.maxsize
    mini_batch_segment_data = []
    for segment_info in segment_info_generator:
        segments_processed_in_batch += 1
        segments_processed_total += 1
        (segment_input, segment_label), segment_feature_mismatch, segment_label_mismatch = minimal_vectorize_segment(
            segment_info, padding
        )
        feature_mismatch |= segment_feature_mismatch
        label_mismatch |= segment_label_mismatch
        segment_num_bases, segment_num_features = segment_input.shape
        _, segment_num_labels = segment_label.shape
        max_bases_mini_batch = max(max_bases_mini_batch, segment_num_bases)
        max_features_mini_batch = max(max_features_mini_batch, segment_num_features)
        max_labels_mini_batch = max(max_labels_mini_batch, segment_num_labels)
        mini_batch_segment_data.append((segment_input, segment_label))
        if segments_processed_in_batch == mini_batch_size or segments_processed_total == num_segments:
            mini_batch_input_ndarray = []
            mini_batch_label_ndarray = []
            for segment_input_batch, segment_label_batch in mini_batch_segment_data:
                segment_num_bases_batch, segment_num_features_batch = segment_input_batch.shape
                _, segment_num_labels_batch = segment_label_batch.shape
                # Prepad timesteps, postpad features/labels (if there's a shape mismatch)
                num_base_diff = max_bases_mini_batch - segment_num_bases_batch
                timestep_padding = (num_base_diff, 0) if padding == "pre" else (0, num_base_diff)
                mini_batch_input_ndarray.append(np.pad(
                    segment_input_batch,
                    pad_width=(timestep_padding, (0, max_features_mini_batch - segment_num_features_batch)),
                    mode='constant'
                ))
                mini_batch_label_ndarray.append(np.pad(
                    segment_label_batch,
                    pad_width=(timestep_padding, (0, max_labels_mini_batch - segment_num_labels_batch)),
                    mode='constant'
                ))
            yield (np.array(mini_batch_input_ndarray),
                   np.array(mini_batch_label_ndarray)), feature_mismatch, label_mismatch
            segments_processed_in_batch = 0
            mini_batch_segment_data = []
            max_bases_mini_batch = -sys.maxsize
            max_features_mini_batch = -sys.maxsize
            max_labels_mini_batch = -sys.maxsize


def write_mini_batch_data(batch_input_to_write, batch_label_to_write, output_path, compress):
    save_fn = np.savez_compressed if compress else np.savez
    save_fn(output_path, input=batch_input_to_write, label=batch_label_to_write)


def main(args):
    os.makedirs(args.output_dir, exist_ok=True)
    with open("{}p".format(args.input), "r") as input_ssip:
        input_properties = Properties()
        input_properties.load(input_ssip)
    max_base_count = int(input_properties.getProperty("maxNumOfBases"))
    max_feature_count = int(input_properties.getProperty("maxNumOfFeatures"))
    max_label_count = int(input_properties.getProperty("maxNumOfLabels"))
    num_segments = int(input_properties.getProperty("numSegments"))
    output_path_and_prefix = os.path.join(args.output_dir, args.prefix)
    feature_mismatch = False
    label_mismatch = False
    num_segments_in_last_data_set = 0
    batches_written = 0
    for batch_idx, (batch_data_set, batch_feature_mismatch, batch_label_mismatch) in enumerate(vectorize_by_mini_batch(
            SequenceSegmentInformationGenerator(args.input), args.mini_batch_size, num_segments, args.padding)):
        batch_input, batch_label = batch_data_set
        num_segments_in_last_data_set = batch_input.shape[0]
        feature_mismatch |= batch_feature_mismatch
        label_mismatch |= batch_label_mismatch
        output_full_path = "{}_{}.npz".format(output_path_and_prefix, batch_idx)
        write_mini_batch_data(batch_input, batch_label, output_full_path, args.compress)
        batches_written += 1
    output_json_path = os.path.join(args.output_dir, "properties.json")
    with open(output_json_path, "w") as output_json_file:
        json.dump({
            "max_base_count": max_base_count,
            "max_feature_count": max_feature_count,
            "max_label_count": max_label_count,
            "num_segments_in_last_data_set": num_segments_in_last_data_set,
            "mini_batch_size": args.mini_batch_size,
            "num_segments": num_segments,
            "total_batches_written": batches_written,
            "batch_prefix": args.prefix,
            "padding": args.padding,
        }, output_json_file, indent=2)
    if feature_mismatch:
        warnings.warn("Mismatched number of features in each base; training behavior will be undefined")
    if label_mismatch:
        warnings.warn("Mismatched number of labels in each base; training behavior will be undefined")


if __name__ == "__main__":
    parser = argparse.ArgumentParser("Generate datasets from SSI files")
    parser.add_argument("-i", "--input", type=str, required=True, help="SSI file with input segments.")
    parser.add_argument("-o", "--output-dir", type=str, required=True,
                        help="Parent directory to put generated numpy files.")
    parser.add_argument("-p", "--prefix", type=str, required=True,
                        help="Prefix to prepend all generated files with.")
    parser.add_argument("-m", "--mini-batch-size", type=int, default=1,
                        help="Number of segments to put in each numpy array.")
    parser.add_argument("--compress", dest="compress", action="store_true",
                        help="When set, compress npz files that are generated.")
    parser.add_argument("--padding", type=str, choices=["pre", "post"], default="pre",
                        help="Whether to pad timesteps before or after sequences.")
    parser_args = parser.parse_args()
    main(parser_args)
