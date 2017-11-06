import csv
import json
import os
from enum import Enum

from keras.models import load_model
from keras.utils import Sequence

import numpy as np


def get_properties_json(path_to_directory):
    properties_json_path = os.path.join(path_to_directory, "properties.json")
    with open(properties_json_path, "r") as properties_json_file:
        properties_json = json.load(properties_json_file)
    return properties_json


class BatchNumpyFileSequence(Sequence):
    def __init__(self, np_batch_directory, max_base_count, properties_json=None, add_metadata=False):
        if properties_json is None:
            properties_json = get_properties_json(np_batch_directory)
        self.properties_json = properties_json
        self.batch_path_and_prefix = os.path.join(np_batch_directory, self.properties_json["batch_prefix"])
        self.max_base_count = max_base_count
        self.add_metadata = add_metadata

    def __len__(self):
        return self.properties_json["total_batches_written"]

    def __getitem__(self, index):
        with np.load("{}_{}.npz".format(self.batch_path_and_prefix, index)) as batch_data_set:
            batch_input = batch_data_set["input"]
            batch_label = batch_data_set["label"]
            # Prepad timesteps, postpad features/labels (if there's a shape mismatch)
            num_base_diff = max(0, self.max_base_count - batch_input.shape[1])
            timestep_padding = (num_base_diff, 0) if self.properties_json["padding"] == "pre" else (0, num_base_diff)
            batch_input = self._pad_batch(batch_input, timestep_padding)
            batch_label = self._pad_batch(batch_label, timestep_padding)
            batch_output = ({"model_input": batch_input}, {"main_output": batch_label})
            if self.add_metadata:
                batch_metadata = self._pad_batch(batch_data_set["metadata"], timestep_padding)
                return batch_output, batch_metadata
            else:
                return batch_output

    def _pad_batch(self, batch_array, timestep_padding):
        batch_array_padded = np.pad(batch_array,
                                    pad_width=((0, 0), timestep_padding, (0, 0)),
                                    mode="constant")
        if self.max_base_count < batch_array.shape[1]:
            if self.properties_json["padding"] == "post":
                return batch_array_padded[:, :self.max_base_count, :]
            else:
                start_base = batch_array.shape[1] - self.max_base_count
                return batch_array_padded[:, start_base:, :]

    def on_epoch_end(self):
        pass


class Metadata(Enum):
    REF = 0
    SNP = 1
    INDEL = 2


class ModelEvaluator:
    def __init__(self, test_data_path, log_path, write_header, log_epochs):
        self.properties_json = get_properties_json(test_data_path)
        self.test_data = BatchNumpyFileSequence(test_data_path, self.properties_json["max_base_count"],
                                                self.properties_json)
        field_names = ["epoch"] if log_epochs else []
        field_names += ["precision_ref", "precision_snp", "precision_indel", "recall_ref", "recall_snp", "recall_indel",
                        "f1_ref", "f1_snp", "f1_indel", "accuracy_overall", "accuracy_ref", "accuracy_snp",
                        "accuracy_indel", "true_or_predicted_ref", "true_or_predicted_snp", "true_or_predicted_indel",
                        "true_ref", "true_snp", "true_indel", "predicted_ref", "predicted_snp", "predicted_indel"]
        with open(log_path, "a") as log_file:
            self.log_writer = csv.DictWriter(log_file, fieldnames=field_names, quoting=csv.QUOTE_NONNUMERIC)
            if write_header:
                self.log_writer.writeheader()

    @staticmethod
    def _get_model(model):
        if type(model) == str:
            return load_model(model)
        else:
            return model

    def eval_model(self, model_to_eval, epoch=None):
        model = ModelEvaluator._get_model(model_to_eval)
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
        for data_idx in range(len(self.test_data)):
            batch_input, batch_label_dict = self.test_data[data_idx]
            batch_label = batch_label_dict["main_output"]
            batch_metadata = batch_label_dict["metadata_output"]
            # batch_predictions, _ = model.predict_on_batch(batch_input)
            batch_predictions = model.predict_on_batch(batch_input)
            for segment_in_batch_idx in range(batch_predictions.shape[0]):
                segment_label_categorical = batch_label[segment_in_batch_idx]
                segment_prediction_categorical = batch_predictions[segment_in_batch_idx]
                segment_metadata_categorical = batch_metadata[segment_in_batch_idx]
                # segment_predicted_metadata_categorical = batch_predicted_metadata[segment_in_batch_idx]
                segment_label_with_padding = np.argmax(segment_label_categorical, axis=1)
                # Get true genotypes from these segment labels
                # Keep track of tp, fp, tn, and fn based on metadata ref, snp, indel, and calculate acc, prec, rec, F1
                segment_prediction_with_padding = np.argmax(segment_prediction_categorical, axis=1)
                segment_metadata_with_padding = np.argmax(segment_metadata_categorical, axis=1)
                # segment_predicted_metadata_with_padding = np.argmax(segment_predicted_metadata_categorical, axis=1)
                # Only use positions where label != 0, as label 0 reserved for padding
                segment_label_non_padding_positions = segment_label_with_padding != 0
                segment_label = np.extract(segment_label_non_padding_positions, segment_label_with_padding)
                segment_prediction = np.extract(segment_label_non_padding_positions, segment_prediction_with_padding)
                segment_metadata = np.extract(segment_label_non_padding_positions, segment_metadata_with_padding)
                # segment_predicted_metadata = np.extract(segment_label_non_padding_positions,
                #                                         segment_predicted_metadata_with_padding)
                # segment_true_genotype_label = [properties_json["genotype.segment.label_plus_one.{}".format(label)]
                #                                for label in segment_label]
                segment_true_genotype_prediction = [
                    self.properties_json["genotype.segment.label_plus_one.{}".format(label)]
                    for label in segment_prediction
                ]

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

                    # Check if prediction is for indel, SNP or ref based on true genotype corresponding to label
                    # If it contains a "-", prediction is indel
                    # If heterozygous, prediction is SNP
                    # Otherwise, is ref
                    base_true_genotype_prediction = segment_true_genotype_prediction[base_idx]
                    if "-" in base_true_genotype_prediction:
                        base_predicted_metadata = Metadata.INDEL
                        true_or_predicted_indel_at_base = True
                        count_predicted_indel += 1
                    elif len("".join(set(base_true_genotype_prediction))) > 1:
                        base_predicted_metadata = Metadata.SNP
                        true_or_predicted_snp_at_base = True
                        count_predicted_snp += 1
                    else:
                        base_predicted_metadata = Metadata.REF
                        true_or_predicted_ref_at_base = True
                        count_predicted_ref += 1

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

        row_dict = {
            "precision_ref": precision_ref,
            "precision_snp": precision_snp,
            "precision_indel": precision_indel,
            "recall_ref": recall_ref,
            "recall_snp": recall_snp,
            "recall_indel": recall_indel,
            "f1_ref": (2 * precision_ref * recall_ref) / (precision_ref + recall_ref),
            "f1_snp": (2 * precision_snp * recall_snp) / (precision_snp + recall_snp),
            "f1_indel": (2 * precision_indel * recall_indel) / (precision_indel + recall_indel),
            "accuracy_overall": correct_overall / count_overall,
            "accuracy_ref": correct_ref / count_true_or_predicted_ref,
            "accuracy_snp": correct_snp / count_true_or_predicted_snp,
            "accuracy_indel": correct_indel / count_true_or_predicted_indel,
            "true_or_predicted_ref": count_true_or_predicted_ref,
            "true_or_predicted_snp": count_true_or_predicted_snp,
            "true_or_predicted_indel": count_true_or_predicted_indel,
            "true_ref": count_true_ref,
            "true_snp": count_true_snp,
            "true_indel": count_true_indel,
            "predicted_ref": count_predicted_ref,
            "predicted_snp": count_predicted_snp,
            "predicted_indel": count_predicted_indel,
        }
        if epoch is not None:
            row_dict["epoch"] = epoch
        self.log_writer.writerow(row_dict)
