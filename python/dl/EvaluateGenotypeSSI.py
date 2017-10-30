import argparse

from keras.metrics import categorical_accuracy
from keras.models import load_model

from dl.TrainGenotypeSSIDataset import BatchNumpyFileSequence, get_properties_json

import numpy as np


def eval_model(model_path, test_set_path):
    model = load_model(model_path)
    properties_json = get_properties_json(test_set_path)
    test_data = BatchNumpyFileSequence(test_set_path, properties_json["max_base_count"], properties_json)
    # Not using predict_generator here because difficult to reconstruct order of inputs,
    # see https://github.com/fchollet/keras/issues/5048
    # Using batch_prediction rather than predicting on each sample to potentially speed up predictions
    accuracies = []
    for data_idx in range(len(test_data)):
        batch_input, batch_label = test_data[data_idx]
        batch_predictions = model.predict_on_batch(batch_input)
        for segment_in_batch_idx in range(batch_predictions.shape[0]):
            segment_label_categorical = batch_label[segment_in_batch_idx]
            segment_prediction_categorical = batch_predictions[segment_in_batch_idx]
            segment_label = np.argmax(segment_label_categorical, axis=1)
            segment_prediction = np.argmax(segment_prediction_categorical, axis=1)
            # Only use positions where label != 0, as label 0 reserved for padding
            segment_label_non_padding_positions = segment_label != 0
            segment_label_non_padding = np.extract(segment_label_non_padding_positions, segment_label)
            segment_prediction_non_padding = np.extract(segment_label_non_padding_positions, segment_prediction)
            acc = np.sum(segment_label_non_padding == segment_prediction_non_padding)
            acc /= segment_label_non_padding.shape[0]
            accuracies.append(acc)
    print(np.mean(accuracies))


def main(args):
    eval_model(args.model, args.testing)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser_args = parser.parse_args()
    main(parser_args)
