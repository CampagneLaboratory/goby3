import argparse
import csv
import os
from collections import namedtuple

from keras.models import load_model

from dl.SegmentGenotypingClassesFunctions import get_properties_json, BatchNumpyFileSequence

import numpy as np

vcf_header = ("##fileformat=VCFv4.1\n"
              "##GobyPython={}\n"
              "##modelPath={}\n"
              "##modelPrefix={}\n"
              "##datasetPath={}\n"
              "##datasetPrefix={}\n"
              "##indelsTrimmed={}\n"
              "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
              "##FORMAT=<ID=MC,Number=1,Type=String,Description=\"Model Calls.\">\n"
              "##FORMAT=<ID=P,Number=1,Type=Float,Description=\"Model proability.\">\n")

VcfOutputWriter = namedtuple("VcfOutputWriter", ["vcf_writer", "bed_writer"])

VcfOutputLine = namedtuple("VcfLine", ["vcf_ref", "vcf_alts", "vcf_gt", "vcf_mc", "vcf_model_probability",
                                       "vcf_max_len"])


class VcfLine:
    # TODO: Avoid duplication b/w __init__ and clear- call __init__ from clear, call clear from __init__, other way
    def __init__(self):
        self.is_indel = False
        self.last_base_location = 0
        self.last_gap_location = 0
        self.vcf_location = None
        self.vcf_ref_bases = []
        self.vcf_predictions = []
        self.vcf_probabilities = []
        self.vcf_chromosome = None

    def clear(self):
        self.is_indel = False
        self.last_base_location = 0
        self.last_gap_location = 0
        self.vcf_location = None
        self.vcf_ref_bases = []
        self.vcf_predictions = []
        self.vcf_probabilities = []
        self.vcf_chromosome = None

    def add_base(self, segment_ref_base, segment_prediction_base, segment_probability_base, segment_location_base,
                 segment_chromosome):
        if self.vcf_location is None:
            self.vcf_location = segment_location_base
        if self.vcf_chromosome is None:
            self.vcf_chromosome = segment_chromosome
        else:
            if self.vcf_chromosome != segment_chromosome:
                raise ValueError("VCF lines should have same chromosome")
        if "-" in segment_prediction_base:
            self.is_indel = True
            self.last_gap_location = segment_location_base
        self.last_base_location = segment_location_base
        self.vcf_ref_bases.append(segment_ref_base)
        self.vcf_predictions.append(segment_prediction_base)
        self.vcf_probabilities.append(segment_probability_base)

    def is_empty(self):
        return self.vcf_location is None

    def need_to_flush(self, segment_next_location):
        if self.is_empty():
            return False
        if not self.is_indel:
            return self.last_base_location != segment_next_location
        else:
            if self.last_base_location == self.last_gap_location:
                return False
            else:
                return self.last_base_location != segment_next_location


def _get_basename(path):
    return os.path.splitext(os.path.basename(path))[0]


def _write_vcf_files(model, properties_json, test_data, **vcf_output_writers):
    vcf_line = VcfLine()
    for data_idx in range(len(test_data)):
        if data_idx % 20 == 0:
            print("Evaluating batch {} of {}...".format(data_idx, len(test_data)))
        (batch_input_dict, batch_label_dict), batch_ref, batch_location, batch_chromosome = test_data[data_idx]
        batch_input = batch_input_dict["model_input"]
        batch_label = batch_label_dict["main_output"]
        batch_predictions = model.predict_on_batch(batch_input)
        for segment_in_batch_idx in range(batch_predictions.shape[0]):
            segment_chromosome = batch_chromosome[segment_in_batch_idx][0]
            segment_label_categorical = batch_label[segment_in_batch_idx]
            segment_prediction_categorical = batch_predictions[segment_in_batch_idx]
            segment_ref_with_padding = batch_ref[segment_in_batch_idx]
            segment_label_with_padding = np.argmax(segment_label_categorical, axis=1)
            segment_prediction_with_padding = np.argmax(segment_prediction_categorical, axis=1)
            segment_model_probabilities_with_padding = np.max(segment_prediction_categorical, axis=1)
            # Only use positions where label != 0, as label 0 reserved for padding
            segment_label_non_padding_positions = segment_label_with_padding != 0
            segment_prediction = np.extract(segment_label_non_padding_positions, segment_prediction_with_padding)
            segment_model_probabilities = np.extract(segment_label_non_padding_positions,
                                                     segment_model_probabilities_with_padding)
            segment_ref = np.extract(segment_label_non_padding_positions, segment_ref_with_padding)
            segment_true_genotype_prediction = [
                properties_json["genotype.segment.label_plus_one.{}".format(label)]
                for label in segment_prediction
            ]
            segment_locations = np.extract(segment_label_non_padding_positions,
                                           batch_location[segment_in_batch_idx])
            for base_idx in range(len(segment_ref)):
                base_location = segment_locations[base_idx]
                base_ref = segment_ref[base_idx]
                base_prediction = segment_true_genotype_prediction[base_idx]
                base_probability = segment_model_probabilities[base_idx]
                if vcf_line.need_to_flush(base_location):
                    _write_vcf_line(vcf_line=vcf_line,
                                    dataset_field=properties_json["batch_prefix"],
                                    **vcf_output_writers)
                    vcf_line.clear()
                vcf_line.add_base(segment_ref_base=base_ref,
                                  segment_prediction_base=base_prediction,
                                  segment_probability_base=base_probability,
                                  segment_location_base=base_location,
                                  segment_chromosome=segment_chromosome)
            if not vcf_line.is_empty():
                _write_vcf_line(vcf_line=vcf_line,
                                dataset_field=properties_json["batch_prefix"],
                                **vcf_output_writers)
                vcf_line.clear()


def _generate_vcf_output_line(vcf_ref, vcf_predicted_alleles, vcf_line, trim_indels):
    if trim_indels:
        vcf_ref, vcf_predicted_alleles = _trim_indels(vcf_ref, vcf_predicted_alleles)
    vcf_alts = list(set(filter(lambda x: x != vcf_ref, vcf_predicted_alleles)))
    vcf_max_len = max(map(len, vcf_alts)) if vcf_alts else 0
    vcf_max_len = max(vcf_max_len, len(vcf_ref))
    vcf_possible_alleles = [vcf_ref] + vcf_alts
    vcf_unique_predicted_alleles = []
    for allele in vcf_predicted_alleles:
        if allele not in vcf_unique_predicted_alleles:
            vcf_unique_predicted_alleles.append(allele)
    vcf_gt = [vcf_possible_alleles.index(allele) for allele in vcf_unique_predicted_alleles]
    vcf_mc = [vcf_possible_alleles[allele_idx] for allele_idx in vcf_gt]
    vcf_model_probability = np.mean(vcf_line.vcf_probabilities)
    return VcfOutputLine(vcf_ref=vcf_ref, vcf_alts=vcf_alts, vcf_gt=vcf_gt, vcf_mc=vcf_mc,
                         vcf_model_probability=vcf_model_probability, vcf_max_len=vcf_max_len)


def _invalid_entry(formatted_ref, formatted_alts, gt):
    invalid_ref = formatted_ref == "." and 0 in gt
    invalid_alts = formatted_alts == "." and (1 in gt or 2 in gt)
    return invalid_ref or invalid_alts


def _generate_vcf_entries(vcf_output_line, vcf_line, dataset_field):
    formatted_ref = _format_alleles(vcf_output_line.vcf_ref)
    formatted_alts = _format_alleles(*vcf_output_line.vcf_alts)
    invalid_entry = _invalid_entry(formatted_ref, formatted_alts, vcf_output_line.vcf_gt)
    vcf_entry = {
        "CHROM": vcf_line.vcf_chromosome,
        "POS": vcf_line.vcf_location + 1,
        "ID": ".",
        "REF": vcf_output_line.vcf_ref,
        "ALT": formatted_alts,
        "QUAL": ".",
        "FILTER": ".",
        "INFO": ".",
        "FORMAT": "GT:MC:P",
        dataset_field: "{}:{}:{}".format("/".join(map(str, vcf_output_line.vcf_gt)),
                                         "/".join(vcf_output_line.vcf_mc),
                                         vcf_output_line.vcf_model_probability),
    }
    bed_entry = {
        "chrom": vcf_line.vcf_chromosome,
        "start": vcf_line.vcf_location,
        "end": vcf_line.vcf_location + vcf_output_line.vcf_max_len,
    }
    return vcf_entry, bed_entry, invalid_entry


def _write_vcf_line(vcf_line, dataset_field, regular_vcf_output_writer, original_vcf_output_writer=None,
                    error_vcf_output_writer=None):
    vcf_ref = "".join(vcf_line.vcf_ref_bases)
    vcf_predicted_alleles = ["".join(bases) for bases in list(zip(*map(list, vcf_line.vcf_predictions)))]
    regular_vcf_output_line = _generate_vcf_output_line(vcf_ref, vcf_predicted_alleles, vcf_line, trim_indels=True)
    regular_entry = _generate_vcf_entries(regular_vcf_output_line, vcf_line, dataset_field)
    regular_vcf_entry, regular_bed_entry, invalid_entry = regular_entry
    if not invalid_entry:
        regular_vcf_output_writer.vcf_writer.writerow(regular_vcf_entry)
        regular_vcf_output_writer.bed_writer.writerow(regular_bed_entry)
    if original_vcf_output_writer is not None or error_vcf_output_writer is not None:
        original_vcf_output_line = _generate_vcf_output_line(vcf_ref, vcf_predicted_alleles, vcf_line,
                                                             trim_indels=False)
        original_entry = _generate_vcf_entries(original_vcf_output_line, vcf_line, dataset_field)
        original_vcf_entry, original_bed_entry, _ = original_entry
        if original_vcf_output_writer is not None:
            if not invalid_entry:
                original_vcf_output_writer.vcf_writer.writerow(original_vcf_entry)
                original_vcf_output_writer.bed_writer.writerow(original_bed_entry)
            else:
                if error_vcf_output_writer is not None:
                    error_vcf_output_writer.vcf_writer.writerow(original_vcf_entry)
                    error_vcf_output_writer.bed_writer.writerow(original_bed_entry)


def _trim_indels(ref, predicted_alleles):
    """
    Properly format indels for inclusion in VCF files
    1. trim all alleles to index of last dash any allele,
    IE: from: GTAC to: G--C,G-AC -> from: GTA to: G--,G-A
    2. delete dashes
    IE: from: GTA to: G--,G-A -> from: GTA to: G,GA
    Based on FormatIndelVCF from VariationAnalysis project
    :param ref: reference allele
    :param predicted_alleles: list of predicted alleles
    :return: ref, alts
    """
    last_del_index = ref.rfind("-") + 1
    for predicted_allele in predicted_alleles:
        allele_del_index = predicted_allele.rfind("-") + 1
        if allele_del_index > last_del_index:
            last_del_index = allele_del_index
    if last_del_index == 0:
        return ref, predicted_alleles
    ref = ref[:last_del_index].replace("-", "")
    predicted_alleles = [predicted_allele[:last_del_index].replace("-", "") for predicted_allele in predicted_alleles]
    return ref, predicted_alleles


def _format_alleles(*alleles):
    # Only select non-empty alts
    valid_alleles = list(filter(lambda allele: allele, alleles))
    if len(valid_alleles) > 0:
        return ",".join(valid_alleles)
    else:
        return "."


def main(args):
    properties_json_to_use = get_properties_json(args.testing)
    model_to_use = load_model(args.model)
    max_base_count = model_to_use.input_shape[1]
    test_data_to_use = BatchNumpyFileSequence(args.testing, max_base_count, properties_json_to_use, array_type='vcf')
    vcf_fields = ["CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT",
                  properties_json_to_use["batch_prefix"]]
    bed_fields = ["chrom", "start", "end"]
    prefix_dir = os.path.dirname(args.prefix)
    if prefix_dir:
        os.makedirs(prefix_dir, exist_ok=True)
    prefix = os.path.splitext(os.path.basename(args.prefix))[0]
    prefix = os.path.join(prefix_dir, prefix)
    regular_vcf_file = open("{}.vcf".format(prefix), "w")
    regular_bed_file = open("{}.bed".format(prefix), "w")
    regular_vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model), args.testing,
                                             properties_json_to_use["batch_prefix"], True))
    regular_vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
    regular_vcf_writer = csv.DictWriter(regular_vcf_file, fieldnames=vcf_fields, delimiter="\t", lineterminator="\n")
    regular_bed_writer = csv.DictWriter(regular_bed_file, fieldnames=bed_fields, delimiter="\t", lineterminator="\n")
    regular_vcf_output_writer = VcfOutputWriter(vcf_writer=regular_vcf_writer, bed_writer=regular_bed_writer)
    original_vcf_file = None
    original_bed_file = None
    original_vcf_output_writer = None
    if args.generate_original_vcf:
        original_vcf_file = open("{}_original.vcf".format(prefix), "w")
        original_bed_file = open("{}_original.bed".format(prefix), "w")
        original_vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model), args.testing,
                                                  properties_json_to_use["batch_prefix"], False))
        original_vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
        original_vcf_writer = csv.DictWriter(original_vcf_file, fieldnames=vcf_fields, delimiter="\t",
                                             lineterminator="\n")
        original_bed_writer = csv.DictWriter(original_bed_file, fieldnames=bed_fields, delimiter="\t",
                                             lineterminator="\n")
        original_vcf_output_writer = VcfOutputWriter(vcf_writer=original_vcf_writer, bed_writer=original_bed_writer)
    error_vcf_file = None
    error_bed_file = None
    error_vcf_output_writer = None
    if args.generate_error_vcf:
        error_vcf_file = open("{}_error.vcf".format(prefix), "w")
        error_bed_file = open("{}_error.bed".format(prefix), "w")
        error_vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model), args.testing,
                                               properties_json_to_use["batch_prefix"], False))
        error_vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
        error_vcf_writer = csv.DictWriter(error_vcf_file, fieldnames=vcf_fields, delimiter="\t",
                                          lineterminator="\n")
        error_bed_writer = csv.DictWriter(error_bed_file, fieldnames=bed_fields, delimiter="\t",
                                          lineterminator="\n")
        error_vcf_output_writer = VcfOutputWriter(vcf_writer=error_vcf_writer, bed_writer=error_bed_writer)
    _write_vcf_files(model_to_use, properties_json_to_use, test_data_to_use,
                     regular_vcf_output_writer=regular_vcf_output_writer,
                     original_vcf_output_writer=original_vcf_output_writer,
                     error_vcf_output_writer=error_vcf_output_writer)
    regular_vcf_file.close()
    regular_bed_file.close()
    if args.generate_original_vcf:
        original_vcf_file.close()
        original_bed_file.close()
    if args.generate_error_vcf:
        error_vcf_file.close()
        error_bed_file.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Path to model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser.add_argument("-p", "--prefix", type=str, required=True,
                        help="Prefix for generated VCF and BED files.")
    parser.add_argument("--version", type=str, help="Version of goby being used", default="1.4.1-SNAPSHOT")
    parser.add_argument("--generate-original-vcf", action="store_true", dest="generate_original_vcf",
                        help="If present, generate separate file at <prefix>_original.{vcf|bed} representing the "
                             "original calls made by the model, before any reformatting to handle indels")
    parser.add_argument("--generate-error-vcf", action="store_true", dest="generate_error_vcf",
                        help="If present, generate seprate file at <prefix>_error.{vcf|bed} with any calls that are "
                             "malformed for the VCF specification.")
    parser_args = parser.parse_args()
    main(parser_args)
