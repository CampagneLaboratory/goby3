import argparse
import json
import os
import sys
import warnings

from goby.SequenceSegmentInformation import SequenceSegmentInformationGenerator
from goby.pyjavaproperties import Properties

import numpy as np


def get_metadata_value(base):
    # Metadata value 0: ref
    # Metadata value 1: SNP
    # Metadata value 2: indel
    if not base.isVariant and not base.hasTrueIndel:
        return 0
    elif base.isVariant and not base.hasTrueIndel:
        return 1
    elif base.hasTrueIndel:
        return 2
    else:
        raise Exception("Invalid isVariant {} and hasTrueIndel {} combination for a base".format(base.isVariant,
                                                                                                 base.hasTrueIndel))


def vectorize_segment_info(segment_info, max_base_count, max_feature_count, max_label_count, padding="pre"):
    # Only look at first segment in sample for now
    sample = segment_info.sample[0]
    feature_array = []
    label_array = []
    metadata_array = []
    ref_array = []
    location_array = []
    for base in sample.base:
        feature_array.append(np.pad(np.array(list(base.features)), (0, max_feature_count - len(base.features)),
                                    mode='constant'))
        # Reserve label index 0 for padding/masking timesteps
        labels_to_append = [0] + list(base.labels)
        label_array.append(np.pad(np.array(labels_to_append),
                                  (0, max_label_count - len(labels_to_append)),
                                  mode='constant'))
        metadata_array.append(get_metadata_value(base))
        ref_array.append(base.referenceAllele)
        location_array.append(base.location)
    amount_to_pad = max(0, max_base_count - len(sample.base))
    timestep_padding = (amount_to_pad, 0) if padding == "pre" else (0, amount_to_pad)
    padding_shape = (timestep_padding, (0, 0))
    feature_array = np.pad(np.array(feature_array), padding_shape, mode='constant')
    label_array = np.pad(np.array(label_array), padding_shape, mode='constant')
    metadata_array = np.pad(np.array(metadata_array), timestep_padding, mode='constant')
    ref_array = np.pad(np.array(ref_array), timestep_padding, mode='constant')
    location_array = np.pad(np.array(location_array), timestep_padding, mode='constant')
    if max_base_count < len(sample.base):
        if padding == "post":
            feature_array = feature_array[:max_base_count, :]
            label_array = label_array[:max_base_count, :]
            metadata_array = metadata_array[:max_base_count]
            ref_array = ref_array[:max_base_count]
            location_array = location_array[:max_base_count]
        else:
            start_base = feature_array.shape[1] - max_base_count
            feature_array = feature_array[start_base:, :]
            label_array = label_array[start_base:, :]
            metadata_array = metadata_array[start_base:]
            ref_array = ref_array[start_base:]
            location_array = location_array[start_base:]
    return feature_array, label_array, metadata_array, ref_array, location_array


def minimal_vectorize_segment(segment_info, padding="pre"):
    num_bases = len(segment_info.sample[0].base)
    # Add 1 to labels because 0 reserved for padding/masking
    num_features_set, num_labels_set = map(frozenset,
                                           zip(*[(len(base.features), len(base.labels) + 1)
                                                 for base in segment_info.sample[0].base]))
    feature_mismatch = len(num_features_set) > 1
    label_mismatch = len(num_labels_set) > 1
    num_features = max(num_features_set)
    num_labels = max(num_labels_set)
    return vectorize_segment_info(segment_info, num_bases, num_features, num_labels,
                                  padding), feature_mismatch, label_mismatch


def vectorize_by_mini_batch(segment_info_generator, mini_batch_size, num_segments, max_base_count, padding="pre",
                            limit=None):
    segments_processed_in_batch = 0
    segments_processed_total = 0
    feature_mismatch = False
    label_mismatch = False
    max_bases_mini_batch = -sys.maxsize
    max_features_mini_batch = -sys.maxsize
    max_labels_mini_batch = -sys.maxsize
    mini_batch_segment_data = []
    for segment_info in segment_info_generator:
        if limit is None or segments_processed_total < limit:
            segments_processed_in_batch += 1
            segments_processed_total += 1
            for base_idx in range(len(segment_info.sample[0].base)):
                if len(segment_info.sample[0].base[base_idx].features) == 0:
                    continue
            if len(segment_info.sample) == 0:
                continue
            if len(segment_info.sample[0].base) == 0:
                continue
            segment_info_data, segment_feature_mismatch, segment_label_mismatch = minimal_vectorize_segment(
                segment_info, padding)
            segment_info_chromosome = [str(segment_info.start_position.reference_id)]
            segment_input, segment_label, _, _, _ = segment_info_data
            feature_mismatch |= segment_feature_mismatch
            label_mismatch |= segment_label_mismatch
            segment_num_bases, segment_num_features = segment_input.shape
            _, segment_num_labels = segment_label.shape
            max_bases_mini_batch = max(max_bases_mini_batch, segment_num_bases)
            max_features_mini_batch = max(max_features_mini_batch, segment_num_features)
            max_labels_mini_batch = max(max_labels_mini_batch, segment_num_labels)
            mini_batch_segment_data.append((segment_info_data, segment_info_chromosome))
        if ((segments_processed_in_batch == mini_batch_size)
                or (segments_processed_total == num_segments)
                or (limit is not None and segments_processed_total == limit)):
            mini_batch_input_ndarray = []
            mini_batch_label_ndarray = []
            mini_batch_metadata_ndarray = []
            mini_batch_ref_ndarray = []
            mini_batch_location_ndarray = []
            mini_batch_chromosome_ndarray = []
            for segment_data_batch, segment_chromosome_batch in mini_batch_segment_data:
                (segment_input_batch, segment_label_batch, segment_metadata_batch,
                 segment_ref_batch, segment_location_batch) = segment_data_batch
                segment_num_bases_batch, segment_num_features_batch = segment_input_batch.shape
                _, segment_num_labels_batch = segment_label_batch.shape
                # Prepad timesteps, postpad features/labels (if there's a shape mismatch)
                num_base_diff = max_bases_mini_batch - segment_num_bases_batch
                timestep_padding = (num_base_diff, 0) if padding == "pre" else (0, num_base_diff)
                mini_batch_input_ndarray.append(np.pad(
                    segment_input_batch,
                    pad_width=(timestep_padding, (0, max_features_mini_batch - segment_num_features_batch)),
                    mode='constant'))
                segment_label_ndarray = np.pad(
                    segment_label_batch,
                    pad_width=(timestep_padding, (0, max_labels_mini_batch - segment_num_labels_batch)),
                    mode='constant')
                if padding == "post":
                    segment_label_ndarray[segment_num_bases_batch:, 0] = 1.
                else:
                    segment_label_ndarray[:segment_num_bases_batch, 0] = 1.
                mini_batch_label_ndarray.append(segment_label_ndarray)
                mini_batch_metadata_ndarray.append(np.pad(
                    segment_metadata_batch,
                    pad_width=timestep_padding,
                    mode='constant'))
                mini_batch_ref_ndarray.append(np.pad(
                    segment_ref_batch,
                    pad_width=timestep_padding,
                    mode='constant'))
                mini_batch_location_ndarray.append(np.pad(
                    segment_location_batch,
                    pad_width=timestep_padding,
                    mode='constant'))
                mini_batch_chromosome_ndarray.append(segment_chromosome_batch)
            mini_batch_input_ndarray = np.array(mini_batch_input_ndarray)
            mini_batch_label_ndarray = np.array(mini_batch_label_ndarray)
            mini_batch_metadata_ndarray = np.array(mini_batch_metadata_ndarray)
            mini_batch_ref_ndarray = np.array(mini_batch_ref_ndarray)
            mini_batch_chromosome_ndarray = np.array(mini_batch_chromosome_ndarray)
            mini_batch_location_ndarray = np.array(mini_batch_location_ndarray)
            if max_base_count < mini_batch_input_ndarray.shape[1]:
                if padding == "post":
                    mini_batch_input_ndarray = mini_batch_input_ndarray[:, :max_base_count, :]
                    mini_batch_label_ndarray = mini_batch_label_ndarray[:, :max_base_count, :]
                    mini_batch_metadata_ndarray = mini_batch_metadata_ndarray[:, :max_base_count]
                    mini_batch_ref_ndarray = mini_batch_ref_ndarray[:, :max_base_count]
                    mini_batch_location_ndarray = mini_batch_location_ndarray[:, :max_base_count]
                else:
                    start_base = mini_batch_input_ndarray.shape[1] - max_base_count
                    mini_batch_input_ndarray = mini_batch_input_ndarray[:, start_base:, :]
                    mini_batch_label_ndarray = mini_batch_label_ndarray[:, start_base:, :]
                    mini_batch_metadata_ndarray = mini_batch_metadata_ndarray[:, start_base:]
                    mini_batch_ref_ndarray = mini_batch_ref_ndarray[:, start_base:]
                    mini_batch_location_ndarray = mini_batch_location_ndarray[:, start_base:]
            mini_batch_data = (mini_batch_input_ndarray, mini_batch_label_ndarray,
                               mini_batch_metadata_ndarray, mini_batch_ref_ndarray,
                               mini_batch_chromosome_ndarray, mini_batch_location_ndarray)
            yield mini_batch_data, feature_mismatch, label_mismatch
            segments_processed_in_batch = 0
            mini_batch_segment_data = []
            max_bases_mini_batch = -sys.maxsize
            max_features_mini_batch = -sys.maxsize
            max_labels_mini_batch = -sys.maxsize
        if segments_processed_total == limit:
            break


def write_mini_batch_data(batch_input_to_write, batch_label_to_write, batch_metadata_to_write, batch_ref_to_write,
                          batch_chromosome_to_write, batch_location_to_write, output_path, compress):
    save_fn = np.savez_compressed if compress else np.savez
    save_fn(output_path, input=batch_input_to_write, label=batch_label_to_write, metadata=batch_metadata_to_write,
            ref=batch_ref_to_write, chromosome=batch_chromosome_to_write, location=batch_location_to_write)


def main(args):
    os.makedirs(args.output_dir, exist_ok=True)
    with open("{}p".format(args.input), "r") as input_ssip:
        input_properties = Properties()
        input_properties.load(input_ssip)
    max_base_count = int(input_properties.getProperty("maxNumOfBases"))
    max_feature_count = int(input_properties.getProperty("maxNumOfFeatures"))
    max_label_count = int(input_properties.getProperty("maxNumOfLabels")) + 1
    num_segments = int(input_properties.getProperty("numSegments"))
    output_path_and_prefix = os.path.join(args.output_dir, args.prefix)
    feature_mismatch = False
    label_mismatch = False
    num_segments_in_last_data_set = 0
    batches_written = 0
    num_segments_written = 0
    for batch_idx, (batch_data_set, batch_feature_mismatch, batch_label_mismatch) in enumerate(vectorize_by_mini_batch(
            SequenceSegmentInformationGenerator(args.input), args.mini_batch_size, num_segments, max_base_count,
            args.padding, args.limit)):
        batch_input, batch_label, batch_metadata, batch_ref, batch_chromosome, batch_location = batch_data_set
        num_segments_written_in_batch = batch_input.shape[0]
        num_segments_written += num_segments_written_in_batch
        num_segments_in_last_data_set = num_segments_written_in_batch
        feature_mismatch |= batch_feature_mismatch
        label_mismatch |= batch_label_mismatch
        output_full_path = "{}_{}.npz".format(output_path_and_prefix, batch_idx)
        write_mini_batch_data(batch_input, batch_label, batch_metadata, batch_ref, batch_chromosome,
                              batch_location, output_full_path, args.compress)
        batches_written += 1
    output_json_path = os.path.join(args.output_dir, "properties.json")
    properties = {
        "max_base_count": max_base_count,
        "max_feature_count": max_feature_count,
        "max_label_count": max_label_count,
        "num_segments_in_last_data_set": num_segments_in_last_data_set,
        "mini_batch_size": args.mini_batch_size,
        "num_segments_written": num_segments_written,
        "total_batches_written": batches_written,
        "batch_prefix": args.prefix,
        "padding": args.padding,
        "genotype.segment.label_plus_one.0": "<PAD>",
    }
    for label in range(1, max_label_count):
        label_name_in = "genotype.segment.label.{}".format(label - 1)
        label_name_out = "genotype.segment.label_plus_one.{}".format(label)
        properties[label_name_out] = input_properties.getProperty(label_name_in)
    with open(output_json_path, "w") as output_json_file:
        json.dump(properties, output_json_file, indent=2)
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
    parser.add_argument("--padding", type=str, choices=["pre", "post"], default="post",
                        help="Whether to pad timesteps before or after sequences.")
    parser.add_argument("--limit", type=int, help="If present, only generate --limit segments.")
    parser_args = parser.parse_args()
    main(parser_args)
