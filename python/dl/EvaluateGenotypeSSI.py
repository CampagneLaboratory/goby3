import argparse
from enum import Enum

from keras.models import load_model

from dl.TrainGenotypeSSIDataset import BatchNumpyFileSequence, get_properties_json

import numpy as np


class Metadata(Enum):
    REF = 0
    SNP = 1
    INDEL = 2


def eval_model(model_path, test_set_path):
    model = load_model(model_path)
    properties_json = get_properties_json(test_set_path)
    test_data = BatchNumpyFileSequence(test_set_path, properties_json["max_base_count"], properties_json)
    # Not using predict_generator here because difficult to reconstruct order of inputs,
    # see https://github.com/fchollet/keras/issues/5048
    # Using batch_prediction rather than predicting on each sample to potentially speed up predictions
    count_true_or_predicted_ref = 0
    count_true_or_predicted_snp = 0
    count_true_or_predicted_indel = 0
    count_predicted_ref = 0
    count_predicted_snp = 0
    count_predicted_indel = 0
    count_true_ref = 0
    count_true_snp = 0
    count_true_indel = 0
    correct_overall = 0
    correct_ref = 0
    correct_snp = 0
    correct_indel = 0
    count_overall = 0
    tp_ref = 0
    tp_snp = 0
    tp_indel = 0
    fp_ref = 0
    fp_snp = 0
    fp_indel = 0
    fn_ref = 0
    fn_snp = 0
    fn_indel = 0
    for data_idx in range(len(test_data)):
        batch_input, batch_label_dict = test_data[data_idx]
        batch_label = batch_label_dict["main_output"]
        batch_metadata = batch_label_dict["metadata_output"]
        batch_predictions, batch_predicted_metadata = model.predict_on_batch(batch_input)
        for segment_in_batch_idx in range(batch_predictions.shape[0]):
            segment_label_categorical = batch_label[segment_in_batch_idx]
            segment_prediction_categorical = batch_predictions[segment_in_batch_idx]
            segment_metadata_categorical = batch_metadata[segment_in_batch_idx]
            segment_predicted_metadata_categorical = batch_metadata[segment_in_batch_idx]
            segment_label_with_padding = np.argmax(segment_label_categorical, axis=1)
            # Get true genotypes from these segment labels
            # Keep track of tp, fp, tn, and fn based on metadata ref, snp, indel, and calculate acc, prec, rec, and F1
            segment_prediction_with_padding = np.argmax(segment_prediction_categorical, axis=1)
            segment_metadata_with_padding = np.argmax(segment_metadata_categorical, axis=1)
            segment_predicted_metadata_with_padding = np.argmax(segment_predicted_metadata_categorical, axis=1)
            # Only use positions where label != 0, as label 0 reserved for padding
            segment_label_non_padding_positions = segment_label_with_padding != 0
            segment_label = np.extract(segment_label_non_padding_positions, segment_label_with_padding)
            segment_prediction = np.extract(segment_label_non_padding_positions, segment_prediction_with_padding)
            segment_metadata = np.extract(segment_label_non_padding_positions, segment_metadata_with_padding)
            segment_predicted_metadata = np.extract(segment_label_non_padding_positions,
                                                    segment_predicted_metadata_with_padding)

            # TODO: decide how to set base_metadata and base_predicted_metadata for both true labels and predictions
            # Currently using true and predicted metadata value, but predicted metadata probably doesn't have any
            # predictive value. However, not sure how would get predicted SNP by looking at actual genotype.
            segment_true_genotype_label = [properties_json["genotype.segment.label_plus_one.{}".format(label)]
                                           for label in segment_label]
            segment_true_genotype_prediction = [properties_json["genotype.segment.label_plus_one.{}".format(label)]
                                                for label in segment_prediction]

            for base_idx in range(segment_label.shape[0]):
                count_overall += 1
                true_or_predicted_ref_at_base = False
                true_or_predicted_indel_at_base = False
                true_or_predicted_snp_at_base = False

                base_metadata_value = segment_metadata[base_idx]
                if base_metadata_value == 0:
                    base_metadata = Metadata.REF
                    true_or_predicted_ref_at_base = True
                    count_true_ref += 1
                elif base_metadata_value == 1:
                    base_metadata = Metadata.SNP
                    true_or_predicted_snp_at_base = True
                    count_true_snp += 1
                elif base_metadata_value == 2:
                    base_metadata = Metadata.INDEL
                    true_or_predicted_indel_at_base = True
                    count_true_indel += 1
                else:
                    raise Exception("Unknown metadata value")

                base_predicted_metadata_value = segment_predicted_metadata[base_idx]
                if base_predicted_metadata_value == 0:
                    base_predicted_metadata = Metadata.REF
                    true_or_predicted_ref_at_base = True
                    count_predicted_ref += 1
                elif base_predicted_metadata_value == 1:
                    base_predicted_metadata = Metadata.SNP
                    true_or_predicted_snp_at_base = True
                    count_predicted_snp += 1
                elif base_predicted_metadata_value == 2:
                    base_predicted_metadata = Metadata.INDEL
                    true_or_predicted_indel_at_base = True
                    count_predicted_indel += 1
                else:
                    raise Exception("Unknown metadata value")

                if true_or_predicted_ref_at_base:
                    count_true_or_predicted_ref += 1
                if true_or_predicted_snp_at_base:
                    count_true_or_predicted_snp += 1
                if true_or_predicted_indel_at_base:
                    count_true_or_predicted_indel += 1

                if segment_prediction[base_idx] == segment_label[base_idx]:
                    correct_overall += 1
                    if true_or_predicted_ref_at_base:
                        correct_ref += 1
                    if true_or_predicted_snp_at_base:
                        correct_snp += 1
                    if true_or_predicted_indel_at_base:
                        correct_indel += 1

                    if base_metadata == Metadata.REF:
                        tp_ref += 1
                    elif base_metadata == Metadata.SNP:
                        tp_snp += 1
                    elif base_metadata == Metadata.INDEL:
                        tp_indel += 1
                    else:
                        raise Exception("Unknown metadata value")
                else:
                    if base_metadata == Metadata.REF:
                        fn_ref += 1
                    elif base_metadata == Metadata.SNP:
                        fn_snp += 1
                    elif base_metadata == Metadata.INDEL:
                        fn_indel += 1
                    else:
                        raise Exception("Unknown metadata value")

                if base_predicted_metadata == Metadata.REF and not base_metadata == Metadata.REF:
                    fp_ref += 1
                elif base_predicted_metadata == Metadata.SNP and not base_metadata == Metadata.SNP:
                    fp_snp += 1
                elif base_predicted_metadata == Metadata.INDEL and not base_metadata == Metadata.INDEL:
                    fp_indel += 1

    precision_ref = tp_ref / (tp_ref + fp_ref)
    precision_snp = tp_snp / (tp_snp + fp_snp)
    precision_indel = tp_indel / (tp_indel + fp_indel)

    recall_ref = tp_ref / (tp_ref + fn_ref)
    recall_snp = tp_snp / (tp_snp + fn_snp)
    recall_indel = tp_indel / (tp_indel + fn_indel)

    f1_ref = (2 * precision_ref * recall_ref) / (precision_ref + recall_ref)
    f1_snp = (2 * precision_snp * recall_snp) / (precision_snp + recall_snp)
    f1_indel = (2 * precision_indel * recall_indel) / (precision_indel + recall_indel)

    accuracy_overall = correct_overall / count_overall
    accuracy_ref = correct_ref / count_true_or_predicted_ref
    accuracy_snp = correct_snp / count_true_or_predicted_snp
    accuracy_indel = correct_indel / count_true_or_predicted_indel

    print("Precision ref: {}".format(precision_ref))
    print("Precision snp: {}".format(precision_snp))
    print("Precision indel: {}".format(precision_indel))

    print("Recall ref: {}".format(recall_ref))
    print("Recall snp: {}".format(recall_snp))
    print("Recall indel: {}".format(recall_indel))

    print("F1 ref: {}".format(f1_ref))
    print("F1 snp: {}".format(f1_snp))
    print("F1 indel: {}".format(f1_indel))

    print("Accuracy overall: {}".format(accuracy_overall))
    print("Accuracy ref: {}".format(accuracy_ref))
    print("Accuracy snp: {}".format(accuracy_snp))
    print("Accuracy indel: {}".format(accuracy_indel))

    print("Count true or predicted ref: {}".format(count_true_or_predicted_ref))
    print("Count true or predicted snp: {}".format(count_true_or_predicted_snp))
    print("Count true or predicted indel: {}".format(count_true_or_predicted_indel))

    print("Count true ref: {}".format(count_true_ref))
    print("Count true snp: {}".format(count_true_snp))
    print("Count true indel: {}".format(count_true_indel))

    print("Count predicted ref: {}".format(count_predicted_ref))
    print("Count predicted snp: {}".format(count_predicted_snp))
    print("Count predicted indel: {}".format(count_predicted_indel))


def main(args):
    eval_model(args.model, args.testing)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser_args = parser.parse_args()
    main(parser_args)
