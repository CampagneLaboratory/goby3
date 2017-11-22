import argparse
import csv
import os

from keras.models import load_model

from dl.SegmentGenotypingClassesFunctions import get_properties_json, BatchNumpyFileSequence, Metadata

import numpy as np

vcf_header = ("##fileformat=VCFv4.1\n"
              "##GobyPython={}\n"
              "##modelPath={}\n"
              "##modelPrefix={}\n"
              "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
              "##FORMAT=<ID=MC,Number=1,Type=String,Description=\"Model Calls.\">\n"
              "##FORMAT=<ID=P,Number=1,Type=Float,Description=\"Model proability.\">\n")


def _get_basename(path):
    return os.path.splitext(os.path.basename(path))[0]


def main(args):
    properties_json = get_properties_json(args.testing)
    model = load_model(args.model)
    max_base_count = model.input_shape[1]
    test_data = BatchNumpyFileSequence(args.testing, max_base_count, properties_json, array_type='vcf')
    dataset_field = _get_basename(args.testing)
    vcf_fields = ["CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT", dataset_field]
    bed_fields = ["chrom", "start", "end"]
    prefix = os.path.splitext(args.prefix)[0]
    with open("{}.vcf".format(prefix), "w") as vcf_file, open("{}.bed".format(prefix), "w") as bed_file:
        vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model)))
        vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
        vcf_writer = csv.DictWriter(vcf_file, fieldnames=vcf_fields, delimiter="\t")
        bed_writer = csv.DictWriter(bed_file, fieldnames=bed_fields, delimiter="\t")
        for data_idx in range(len(test_data)):
            (batch_input_dict, batch_label_dict), batch_ref, batch_location, batch_chromosome = test_data[data_idx]
            batch_input = batch_input_dict["model_input"]
            batch_label = batch_label_dict["main_output"]
            batch_predictions = model.predict_on_batch(batch_input)
            for segment_in_batch_idx in range(batch_predictions.shape[0]):
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
                segment_ref = "".join(np.extract(
                    segment_label_non_padding_positions, segment_ref_with_padding).tolist())
                segment_true_genotype_prediction = [
                    properties_json["genotype.segment.label_plus_one.{}".format(label)]
                    for label in segment_prediction
                ]
                segment_predicted_alleles = ["".join(bases) for bases in
                                             list(zip(*map(list, segment_true_genotype_prediction)))]
                segment_alts = set(filter(lambda x: x != segment_ref, segment_predicted_alleles))
                segment_possible_alleles = [segment_ref] + list(segment_alts)
                segment_unique_predicted_alleles = []
                for allele in segment_predicted_alleles:
                    if allele not in segment_unique_predicted_alleles:
                        segment_unique_predicted_alleles.append(allele)
                segment_gt = [segment_possible_alleles.index(segment_allele)
                              for segment_allele in segment_unique_predicted_alleles]
                segment_mc = [segment_possible_alleles[allele_idx] for allele_idx in segment_gt]
                segment_chromosome = batch_chromosome[segment_in_batch_idx]
                segment_location = batch_location[segment_in_batch_idx]
                segment_model_probability = np.mean(segment_model_probabilities)
                if len(segment_ref) == 1:
                    segment_vcf_entry = {
                        "CHROM": segment_chromosome[0],
                        "POS": segment_location[0] + 1,
                        "ID": ".",
                        "REF": segment_ref,
                        "ALT": ",".join(segment_alts) if len(segment_alts) > 0 else ".",
                        "QUAL": ".",
                        "FILTER": ".",
                        "INFO": ".",
                        "FORMAT": "GT:MC:P",
                        dataset_field: "{}:{}:{}".format("/".join(map(str, segment_gt)),
                                                         "/".join(segment_mc),
                                                         segment_model_probability),
                    }
                    vcf_writer.writerow(segment_vcf_entry)
                    segment_bed_entry = {
                        "chrom": segment_chromosome[0],
                        "start": segment_location[0],
                        "end": segment_location[0] + 1,
                    }
                    bed_writer.writerow(segment_bed_entry)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Path to model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser.add_argument("-p", "--prefix", type=str, required=True,
                        help="Prefix for generated VCF and BED files.")
    parser.add_argument("--version", type=str, help="Version of goby being used", default="1.4.1-SNAPSHOT")
    parser_args = parser.parse_args()
    main(parser_args)
