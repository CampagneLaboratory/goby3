import argparse
import csv
import os

from keras.models import load_model

from dl.SegmentGenotypingClassesFunctions import get_properties_json, BatchNumpyFileSequence

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
    prefix_dir = os.path.dirname(args.prefix)
    if prefix_dir:
        os.makedirs(prefix_dir, exist_ok=True)
    prefix = os.path.splitext(os.path.basename(args.prefix))[0]
    prefix = os.path.join(prefix_dir, prefix)
    with open("{}.vcf".format(prefix), "w") as vcf_file, open("{}.bed".format(prefix), "w") as bed_file:
        vcf_file.write(vcf_header.format(args.version, args.model, _get_basename(args.model)))
        vcf_file.write("#{}\n".format("\t".join(vcf_fields)))
        vcf_writer = csv.DictWriter(vcf_file, fieldnames=vcf_fields, delimiter="\t")
        bed_writer = csv.DictWriter(bed_file, fieldnames=bed_fields, delimiter="\t")
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
                    if base_location == vcf_location:
                        vcf_ref_bases.append(segment_ref[base_idx])
                        vcf_predictions.append(segment_true_genotype_prediction[base_idx])
                        vcf_probabilities.append(segment_model_probabilities[base_idx])
                    else:
                        write_vcf_line(vcf_location=vcf_location,
                                       vcf_chromosome=vcf_chromosome,
                                       vcf_ref_bases=vcf_ref_bases,
                                       vcf_predictions=vcf_predictions,
                                       vcf_probabilities=vcf_probabilities,
                                       vcf_writer=vcf_writer,
                                       bed_writer=bed_writer,
                                       dataset_field=dataset_field)
                        vcf_location = base_location
                        vcf_chromosome = segment_chromosome
                        vcf_ref_bases = [segment_ref[base_idx]]
                        vcf_predictions = [segment_true_genotype_prediction[base_idx]]
                        vcf_probabilities = [segment_model_probabilities[base_idx]]
            if len(vcf_ref_bases) > 0:
                write_vcf_line(vcf_location=vcf_location,
                               vcf_chromosome=vcf_chromosome,
                               vcf_ref_bases=vcf_ref_bases,
                               vcf_predictions=vcf_predictions,
                               vcf_probabilities=vcf_probabilities,
                               vcf_writer=vcf_writer,
                               bed_writer=bed_writer,
                               dataset_field=dataset_field)


def write_vcf_line(vcf_location, vcf_chromosome, vcf_ref_bases, vcf_predictions, vcf_probabilities, vcf_writer,
                   bed_writer, dataset_field):
    vcf_ref = "".join(vcf_ref_bases)
    vcf_predicted_alleles = ["".join(bases) for bases in list(zip(*map(list, vcf_predictions)))]
    vcf_alts = set(filter(lambda x: x != vcf_ref, vcf_predicted_alleles))
    vcf_possible_alleles = [vcf_ref] + list(vcf_alts)
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
        "ALT": ",".join(vcf_alts) if len(vcf_alts) > 0 else ".",
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
        "end": vcf_location + 1,
    }
    bed_writer.writerow(bed_entry)


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
