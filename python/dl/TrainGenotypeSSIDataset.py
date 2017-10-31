import argparse
import json
import math
import os
import warnings

from keras import backend, Input
from keras.callbacks import ModelCheckpoint, EarlyStopping, ReduceLROnPlateau, TensorBoard
from keras.layers import Masking, LSTM, Bidirectional, Dropout, TimeDistributed, Dense, GRU, SimpleRNN, regularizers
from keras.models import Model
from keras.optimizers import RMSprop
from keras.regularizers import l1_l2, l1, l2
from keras.utils import Sequence

import numpy as np
import tensorflow as tf


def batch_numpy_file_generator(np_batch_directory, max_base_count, properties_json=None):
    curr_batch_num = 0
    if properties_json is None:
        properties_json = get_properties_json(np_batch_directory)
    batch_path_and_prefix = os.path.join(np_batch_directory, properties_json["batch_prefix"])
    while True:
        with np.load("{}_{}.npz".format(batch_path_and_prefix,
                                        curr_batch_num % properties_json["total_batches_written"])) as batch_data_set:
            batch_input = batch_data_set["input"]
            batch_label = batch_data_set["label"]
            batch_metadata = batch_data_set["metadata"]
            # Prepad timesteps, postpad features/labels (if there's a shape mismatch)
            num_base_diff = max(0, max_base_count - batch_input.shape[1])
            timestep_padding = (num_base_diff, 0) if properties_json["padding"] == "pre" else (0, num_base_diff)
            batch_input = np.pad(batch_input,
                                 pad_width=((0, 0), timestep_padding, (0, 0)),
                                 mode="constant")
            batch_label = np.pad(batch_label,
                                 pad_width=((0, 0), timestep_padding, (0, 0)),
                                 mode="constant")
            batch_metadata = np.pad(batch_metadata,
                                    pad_width=((0, 0), timestep_padding, (0, 0)),
                                    mode="constant")
            if max_base_count < batch_input.shape[1]:
                if properties_json["padding"] == "post":
                    batch_input = batch_input[:, :max_base_count, :]
                    batch_label = batch_label[:, :max_base_count, :]
                    batch_metadata = batch_metadata[:, :max_base_count, :]
                else:
                    start_base = batch_input.shape[1] - max_base_count
                    batch_input = batch_input[:, start_base:, :]
                    batch_label = batch_label[:, start_base:, :]
                    batch_metadata = batch_metadata[:, start_base:, :]
            yield {"model_input": batch_input}, {"main_output": batch_label, "metadata_output": batch_metadata}
            curr_batch_num += 1


class BatchNumpyFileSequence(Sequence):
    def __init__(self, np_batch_directory, max_base_count, properties_json=None):
        if properties_json is None:
            properties_json = get_properties_json(np_batch_directory)
        self.properties_json = properties_json
        self.batch_path_and_prefix = os.path.join(np_batch_directory, self.properties_json["batch_prefix"])
        self.max_base_count = max_base_count

    def __len__(self):
        return self.properties_json["total_batches_written"]

    def __getitem__(self, index):
        with np.load("{}_{}.npz".format(self.batch_path_and_prefix, index)) as batch_data_set:
            batch_input = batch_data_set["input"]
            batch_label = batch_data_set["label"]
            batch_metadata = batch_data_set["metadata"]
            # Prepad timesteps, postpad features/labels (if there's a shape mismatch)
            num_base_diff = max(0, self.max_base_count - batch_input.shape[1])
            timestep_padding = (num_base_diff, 0) if self.properties_json["padding"] == "pre" else (0, num_base_diff)
            batch_input = np.pad(batch_input,
                                 pad_width=((0, 0), timestep_padding, (0, 0)),
                                 mode="constant")
            batch_label = np.pad(batch_label,
                                 pad_width=((0, 0), timestep_padding, (0, 0)),
                                 mode="constant")
            batch_metadata = np.pad(batch_metadata,
                                    pad_width=((0, 0), timestep_padding, (0, 0)),
                                    mode="constant")
            if self.max_base_count < batch_input.shape[1]:
                if self.properties_json["padding"] == "post":
                    batch_input = batch_input[:, :self.max_base_count, :]
                    batch_label = batch_label[:, :self.max_base_count, :]
                    batch_metadata = batch_metadata[:, :self.max_base_count, :]
                else:
                    start_base = batch_input.shape[1] - self.max_base_count
                    batch_input = batch_input[:, start_base:, :]
                    batch_label = batch_label[:, start_base:, :]
                    batch_metadata = batch_metadata[:, start_base:, :]
            return {"model_input": batch_input}, {"main_output": batch_label, "metadata_output": batch_metadata}

    def on_epoch_end(self):
        pass


def create_model(num_layers, max_base_count, max_feature_count, max_label_count, use_bidirectional, lstm_units,
                 implementation, regularizer, learning_rate, layer_type, add_metadata=True):
    model_input = Input(shape=(max_base_count, max_feature_count), name="model_input")
    model_masking = Masking(mask_value=0., input_shape=(max_base_count, max_feature_count))(model_input)
    if layer_type == "LSTM":
        lstm_fn = LSTM
    elif layer_type == "GRU":
        lstm_fn = GRU
    elif layer_type == "RNN":
        lstm_fn = SimpleRNN
    elif layer_type == "SRU":
        raise Exception("SRU not added yet")
    else:
        raise Exception("Layer type not valid")
    first_lstm_layer_fn = lstm_fn(units=lstm_units, unroll=True, implementation=implementation,
                                  activity_regularizer=regularizer, return_sequences=True,
                                  input_shape=(max_base_count, max_feature_count))
    first_lstm_layer = (Bidirectional(first_lstm_layer_fn, merge_mode="concat")(model_masking)
                        if use_bidirectional
                        else first_lstm_layer_fn(model_masking))
    prev_lstm_layer = first_lstm_layer
    for _ in range(num_layers):
        hidden_lstm_layer_fn = lstm_fn(units=lstm_units, unroll=True, implementation=implementation,
                                       activity_regularizer=regularizer, return_sequences=True,
                                       input_shape=(max_base_count, lstm_units))
        hidden_lstm_layer = (Bidirectional(hidden_lstm_layer_fn, merge_mode="concat")(prev_lstm_layer)
                             if use_bidirectional
                             else hidden_lstm_layer_fn(prev_lstm_layer))
        prev_lstm_layer = hidden_lstm_layer
    dropout = Dropout(0.5)(prev_lstm_layer)
    model_outputs = [TimeDistributed(Dense(max_label_count, activation="softmax"),
                                     input_shape=(max_base_count, lstm_units), name="main_output")(dropout)]
    loss_weights = [1.]
    if add_metadata:
        # Metadata output with 3 possible labels (ref, SNP, indel) and weight of 0
        model_outputs.append(Dense(3, name="metadata_output"))
        loss_weights.append(0.)
    model = Model(inputs=model_input, outputs=model_outputs)
    optimizer = RMSprop(lr=learning_rate)
    model.compile(loss='categorical_crossentropy', optimizer=optimizer, metrics=['acc'], loss_weights=loss_weights)
    print("Model summary:", model.summary())
    return model


def create_callbacks(model_prefix, min_delta, use_tensorboard):
    callbacks_list = []
    filepath = model_prefix + "-weights-improvement-{epoch:02d}-{val_loss:.4f}.hdf5"
    checkpoint = ModelCheckpoint(filepath, monitor='val_loss', verbose=1, save_best_only=True, mode='min')
    callbacks_list.append(checkpoint)

    early_stopping = EarlyStopping(monitor="val_loss", patience=3, mode="min", min_delta=min_delta)
    callbacks_list.append(early_stopping)

    reduce_lr = ReduceLROnPlateau(monitor='val_loss', patience=2, mode="min", factor=0.2, min_lr=0.0001,
                                  cooldown=3, verbose=1)
    callbacks_list.append(reduce_lr)

    if use_tensorboard:
        tensorboard = TensorBoard(log_dir='./logs', histogram_freq=1, write_graph=True,
                                  write_images=True, embeddings_freq=0,
                                  embeddings_layer_names=None,
                                  embeddings_metadata=None)
        callbacks_list.append(tensorboard)

    return callbacks_list


def get_properties_json(path_to_directory):
    properties_json_path = os.path.join(path_to_directory, "properties.json")
    with open(properties_json_path, "r") as properties_json_file:
        properties_json = json.load(properties_json_file)
    return properties_json


def main(args):
    if args.training_mode not in frozenset(["batch-np", "sequence-np"]) and args.use_metadata:
        raise Exception("Metadata only provided when data preprocessed by GenerateDatasetsFromSSI")
    backend.set_learning_phase(1)
    init = tf.global_variables_initializer()

    # Show device placement:
    session = (tf.InteractiveSession(config=tf.ConfigProto(log_device_placement=args.show_mappings))
               if args.tensorboard
               else tf.Session(config=tf.ConfigProto(log_device_placement=args.show_mappings)))

    session.run(init)

    if args.platform == "cpu":
        implementation = 0
        cpu_or_gpu = "/cpu:0"
    elif args.platform == "gpu":
        implementation = 2
        gpu_device = args.gpu_device
        cpu_or_gpu = "/gpu:{}".format(gpu_device)
    else:
        raise ValueError("Platform {} not recognized".format(args.platform))

    reg = None
    if args.l1 is not None and args.l2 is not None:
        reg = regularizers.get(l1_l2(args.l1, args.l2))
    elif args.l1 is not None:
        reg = regularizers.get(l1(args.l1))
    elif args.l2 is not None:
        reg = regularizers.get(l2(args.l2))

    input_properties_json = get_properties_json(args.input)
    val_properties_json = get_properties_json(args.validation)
    input_base_count = input_properties_json["max_base_count"]
    input_feature_count = input_properties_json["max_feature_count"]
    input_label_count = input_properties_json["max_label_count"]
    val_feature_count = val_properties_json["max_feature_count"]
    val_label_count = val_properties_json["max_label_count"]
    input_num_segments = input_properties_json["num_segments_written"]
    val_num_segments = val_properties_json["num_segments_written"]
    input_mini_batch_size = input_properties_json["mini_batch_size"]
    val_mini_batch_size = val_properties_json["mini_batch_size"]

    max_base_count = input_base_count
    if input_feature_count != val_feature_count:
        warnings.warn("Mismatch between input feature count {} and val feature count {}".format(input_feature_count,
                                                                                                val_feature_count))
    if input_label_count != val_label_count:
        warnings.warn("Mismatch between input label count {} and val label count {}".format(input_label_count,
                                                                                            val_label_count))
    max_feature_count = max(input_feature_count, val_feature_count)
    max_label_count = max(input_label_count, val_label_count)

    print("Creating model and callbacks...")
    model = create_model(num_layers=args.num_layers,
                         max_base_count=max_base_count,
                         max_feature_count=max_feature_count,
                         max_label_count=max_label_count,
                         use_bidirectional=args.bidirectional,
                         lstm_units=args.lstm_units,
                         implementation=implementation,
                         layer_type=args.layer_type,
                         learning_rate=args.learning_rate,
                         regularizer=reg)
    callbacks = create_callbacks(args.model_prefix, args.min_delta, args.tensorboard)

    if args.training_mode == "batch-np":
        input_generator = batch_numpy_file_generator(args.input, max_base_count, input_properties_json)
        val_generator = batch_numpy_file_generator(args.validation, max_base_count, val_properties_json)
    elif args.training_mode == "sequence-np":
        input_generator = BatchNumpyFileSequence(args.input, max_base_count, input_properties_json)
        val_generator = BatchNumpyFileSequence(args.validation, max_base_count, val_properties_json)
    else:
        raise Exception("Unrecognized training mode")
    input_updates = math.ceil(input_num_segments / input_mini_batch_size)
    val_updates = math.ceil(val_num_segments / val_mini_batch_size)
    use_multiprocessing = args.parallel is not None
    num_workers = args.parallel if args.parallel is not None else 1
    with tf.device(cpu_or_gpu):
        print("Training...")
        model.fit_generator(generator=input_generator,
                            steps_per_epoch=input_updates,
                            validation_data=val_generator,
                            validation_steps=val_updates,
                            epochs=args.max_epochs,
                            callbacks=callbacks,
                            verbose=args.verbosity,
                            use_multiprocessing=use_multiprocessing,
                            workers=num_workers)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-t", "--training-mode", type=str,
                        choices=["batch-np", "sequence-np"], required=True,
                        help="Training mode- batch-np loads in numpy npz datasets generated using "
                             "GenerateDatasetsFromSSI, with the directories specified by the "
                             "--input and --validation parameters and creates "
                             "generators for each; sequence-np is similar to batch-np, but uses a "
                             "keras.utils.Sequence object.")
    parser.add_argument("-i", "--input", type=str, required=True,
                        help="Path to directory containing training npz files generated using GenerateDatasetsFromSSI.")
    parser.add_argument("-v", "--validation", type=str, required=True,
                        help="Path to directory containing validation npz files generated using "
                             "GenerateDatasetsFromSSI.")
    parser.add_argument("--bidirectional", dest="bidirectional", action="store_true",
                        help="When set, train a bidirectional LSTM.")
    parser.add_argument("--lstm-units", type=int, default=64, help="Number of LSTM units.")
    parser.add_argument("--num-layers", type=int, default=1, help="Number of hidden LSTM layers.")
    parser.add_argument("--platform", required=True, type=str, choices=["cpu", "gpu"],
                        help="Platform to train on: cpu or gpu.")
    parser.add_argument("--gpu-device", type=int, default=0, help="Index of the GPU to use, when platform is gpu. "
                                                                  "Not recommended, set CUDA_VISIBLE_DEVICES instead.")
    parser.add_argument("--layer-type", type=str, choices=["LSTM", "RNN", "GRU", "SRU"], default="LSTM",
                        help="Type of RNN layer to use.")
    parser.add_argument("--learning-rate", type=float, default=0.01, help="Learning rate.")
    parser.add_argument("--model-prefix", type=str, default="model",
                        help="Prefix (a short string) to name model checkpoints with")
    parser.add_argument("--min-delta", type=float, default=0, help="Minimum delta for loss improvement in each epoch.")
    parser.add_argument("--tensorboard", dest="tensorboard", action="store_true",
                        help="When set, use an interactive session and monitor on tensorboard.")
    parser.add_argument("--show-mappings", dest="show_mappings", action="store_true",
                        help="When set, show operation placements on cpu/gpu devices.")
    parser.add_argument("--verbosity", type=int, default=1, choices=[0, 1, 2],
                        help="Level of verbosity, 0, not verbose, 1, progress bar, 2, one line per epoch.")
    parser.add_argument("--max-epochs", type=int, default=60, help="Maximum number of epochs to train for.")
    parser.add_argument("--l1", type=float, help="L1 regularization rate.")
    parser.add_argument("--l2", type=float, help="L2 regularization rate.")
    parser.add_argument("--parallel", type=int, help="Run training in parallel, with n workers.")

    parser_args = parser.parse_args()
    main(parser_args)
