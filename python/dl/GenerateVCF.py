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

Vcf = namedtuple("Vcf", ["vcf_writer", "bed_writer", "trim_indels"])


def _get_basename(path):
    return os.path.splitext(os.path.basename(path))[0]


def _write_vcf_files(model, properties_json, test_data, *vcfs):
    vcf_location = None
    vcf_chromosome = None
    vcf_ref_bases = []
    vcf_predictions = []
    vcf_probabilities = []
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
            if vcf_location is None:
                vcf_location = segment_locations[0]
                vcf_chromosome = segment_chromosome
            for base_idx in range(len(segment_ref)):
                base_location = segment_locations[base_idx]
                if base_location > vcf_location + 1:
                    for vcf in vcfs:
                        _write_vcf_line(vcf_location=vcf_location,
                                        vcf_chromosome=vcf_chromosome,
                                        vcf_ref_bases=vcf_ref_bases,
                                        vcf_predictions=vcf_predictions,
                                        vcf_probabilities=vcf_probabilities,
                                        vcf_writer=vcf.vcf_writer,
                                        bed_writer=vcf.bed_writer,
                                        dataset_field=properties_json["batch_prefix"],
                                        trim_indels=vcf.trim_indels)
                    vcf_location = base_location
                    vcf_chromosome = segment_chromosome
                    vcf_ref_bases = [segment_ref[base_idx]]
                    vcf_predictions = [segment_true_genotype_prediction[base_idx]]
                    vcf_probabilities = [segment_model_probabilities[base_idx]]
                else:
                    vcf_ref_bases.append(segment_ref[base_idx])
                    vcf_predictions.append(segment_true_genotype_prediction[base_idx])
                    vcf_probabilities.append(segment_model_probabilities[base_idx])
        if len(vcf_ref_bases) > 0:
            for vcf in vcfs:
                _write_vcf_line(vcf_location=vcf_location,
                                vcf_chromosome=vcf_chromosome,
                                vcf_ref_bases=vcf_ref_bases,
                                vcf_predictions=vcf_predictions,
                                vcf_probabilities=vcf_probabilities,
                                vcf_writer=vcf.vcf_writer,
                                bed_writer=vcf.bed_writer,
                                dataset_field=properties_json["batch_prefix"],
                                trim_indels=vcf.trim_indels)


def _write_vcf_line(vcf_location, vcf_chromosome, vcf_ref_bases, vcf_predictions, vcf_probabilities, vcf_writer,
                    bed_writer, dataset_field, trim_indels=True):
    vcf_ref = "".join(vcf_ref_bases)
    vcf_predicted_alleles = ["".join(bases) for bases in list(zip(*map(list, vcf_predictions)))]
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
    vcf_model_probability = np.mean(vcf_probabilities)
    vcf_entry = {
        "CHROM": vcf_chromosome,
        "POS": vcf_location + 1,
        "ID": ".",
        "REF": vcf_ref,
        "ALT": _format_alts(vcf_alts),
        "QUAL": ".",
        "FILTER": ".",
        "INFO": ".",
        "FORMAT": "GT:MC:P",
        dataset_field: "{}:{}:{}".format("/".join(map(str, vcf_gt)),
                                         "/".join(vcf_mc),
                                         vcf_model_probability),
    }
    vcf_writer.writerow(vcf_entry)
    bed_entry = {
        "chrom": vcf_chromosome,
        "start": vcf_location,
        "end": vcf_location + vcf_max_len,
    }
    bed_writer.writerow(bed_entry)


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


def _format_alts(alts):
    # Only select non-empty alts
    valid_alts = list(filter(lambda alt: alt, alts))
    if len(valid_alts) > 0:
        return ",".join(valid_alts)
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
    trimmed_vcf_file = open("{}.vcf".format(prefix), "w")
    trimmed_bed_file = open("{}.bed".format(prefix), "w")
    trimmed_vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model), args.testing,
                                             properties_json_to_use["batch_prefix"], True))
    trimmed_vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
    trimmed_vcf_writer = csv.DictWriter(trimmed_vcf_file, fieldnames=vcf_fields, delimiter="\t", lineterminator="\n")
    trimmed_bed_writer = csv.DictWriter(trimmed_bed_file, fieldnames=bed_fields, delimiter="\t", lineterminator="\n")
    vcfs_to_write = [Vcf(vcf_writer=trimmed_vcf_writer, bed_writer=trimmed_bed_writer, trim_indels=True)]
    untrimmed_vcf_file = None
    untrimmed_bed_file = None
    if args.generate_original_vcf:
        untrimmed_vcf_file = open("{}_untrimmed.vcf".format(prefix), "w")
        untrimmed_bed_file = open("{}_untrimmed.bed".format(prefix), "w")
        untrimmed_vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model), args.testing,
                                                   properties_json_to_use["batch_prefix"], False))
        untrimmed_vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
        untrimmed_vcf_writer = csv.DictWriter(untrimmed_vcf_file, fieldnames=vcf_fields, delimiter="\t",
                                              lineterminator="\n")
        untrimmed_bed_writer = csv.DictWriter(untrimmed_bed_file, fieldnames=bed_fields, delimiter="\t",
                                              lineterminator="\n")
        vcfs_to_write.append(Vcf(vcf_writer=untrimmed_vcf_writer, bed_writer=untrimmed_bed_writer, trim_indels=False))
    _write_vcf_files(model_to_use, properties_json_to_use, test_data_to_use, *vcfs_to_write)
    trimmed_vcf_file.close()
    trimmed_bed_file.close()
    if args.generate_original_vcf:
        untrimmed_vcf_file.close()
        untrimmed_bed_file.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Path to model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser.add_argument("-p", "--prefix", type=str, required=True,
                        help="Prefix for generated VCF and BED files.")
    parser.add_argument("--version", type=str, help="Version of goby being used", default="1.4.1-SNAPSHOT")
    parser.add_argument("--generate-original-vcf", action="store_true", dest="generate_original_vcf",
                        help="If present, generate separate file at <prefix>-original.{vcf|bed} representing the"
                             "original calls made by the model, before any reformatting to handle indels")
    parser_args = parser.parse_args()
    main(parser_args)
