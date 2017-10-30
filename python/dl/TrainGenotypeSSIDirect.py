import argparse
import math
import warnings

from keras import backend
from keras.layers import regularizers
from keras.regularizers import l1_l2, l1, l2

from dl.GenerateDatasetsFromSSI import vectorize_segment_info
from dl.TrainGenotypeSSIDataset import create_model, create_callbacks
from goby.SequenceSegmentInformation import SequenceSegmentInformationGenerator, SequenceSegmentInformationStreamGenerator
from goby.pyjavaproperties import Properties
import numpy as np
import tensorflow as tf


def vectorize(segment_info_generator, max_base_count, max_feature_count, max_label_count, padding="pre"):
    feature_arrays = []
    label_arrays = []
    for segment_info in segment_info_generator:
        feature_array, label_array = vectorize_segment_info(segment_info, max_base_count, max_feature_count,
                                                            max_label_count, padding)
        feature_arrays.append(feature_array)
        label_arrays.append(label_array)
    return np.array(feature_arrays), np.array(label_arrays)


def vectorize_generator(segment_info_generator, max_base_count, max_feature_count, max_label_count, mini_batch_size,
                        num_segments, padding="pre"):
    feature_arrays = []
    label_arrays = []
    segments_processed = 0
    for segment_info in segment_info_generator:
        segments_processed += 1
        feature_array, label_array = vectorize_segment_info(segment_info, max_base_count, max_feature_count,
                                                            max_label_count, padding)
        feature_arrays.append(feature_array)
        label_arrays.append(label_array)
        if len(feature_arrays) == mini_batch_size or segments_processed == num_segments:
            yield np.array(feature_arrays), np.array(label_arrays)
            feature_arrays = []
            label_arrays = []
            segments_processed = 0


def main(args):
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
    with open("{}p".format(args.input), "r") as input_ssip:
        input_properties = Properties()
        input_properties.load(input_ssip)
    with open("{}p".format(args.validation), "r") as val_ssip:
        val_properties = Properties()
        val_properties.load(val_ssip)
    input_base_count = int(input_properties.getProperty("maxNumOfBases"))
    input_feature_count = int(input_properties.getProperty("maxNumOfFeatures"))
    input_label_count = int(input_properties.getProperty("maxNumOfLabels"))
    val_feature_count = int(val_properties.getProperty("maxNumOfFeatures"))
    val_label_count = int(val_properties.getProperty("maxNumOfLabels"))
    input_num_segments = int(input_properties.getProperty("numSegments"))
    val_num_segments = int(val_properties.getProperty("numSegments"))
    input_mini_batch_size = args.input_mini_batch_size
    val_mini_batch_size = args.val_mini_batch_size
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
                         add_metadata=False,
                         regularizer=reg)
    callbacks = create_callbacks(args.model_prefix, args.min_delta, args.tensorboard)
    generator_training_modes = frozenset(["batch", "sequence", "batch-np", "sequence-np"])

    if args.training_mode == "whole":
        print("Vectorizing training data...")
        training_input, training_label = vectorize(SequenceSegmentInformationGenerator(args.input),
                                                   max_base_count=max_base_count,
                                                   max_feature_count=max_feature_count,
                                                   max_label_count=max_label_count,
                                                   padding=args.padding)
        print("Vectorizing validation data...")
        val_input, val_label = vectorize(SequenceSegmentInformationGenerator(args.validation),
                                         max_base_count=max_base_count,
                                         max_feature_count=max_feature_count,
                                         max_label_count=max_label_count,
                                         padding=args.padding)
        with tf.device(cpu_or_gpu):
            print("Training...")
            model.fit(x=training_input,
                      y=training_label,
                      validation_data=(val_input, val_label),
                      batch_size=args.mini_batch_size,
                      verbose=args.verbosity,
                      shuffle=True,
                      epochs=args.max_epochs,
                      callbacks=callbacks)
    elif args.training_mode in generator_training_modes:
        if args.training_mode == "batch":
            input_generator = vectorize_generator(SequenceSegmentInformationStreamGenerator(args.input),
                                                  max_base_count=max_base_count,
                                                  max_feature_count=max_feature_count,
                                                  max_label_count=max_label_count,
                                                  mini_batch_size=input_mini_batch_size,
                                                  num_segments=input_num_segments,
                                                  padding=args.padding)
            val_generator = vectorize_generator(SequenceSegmentInformationStreamGenerator(args.validation),
                                                max_base_count=max_base_count,
                                                max_feature_count=max_feature_count,
                                                max_label_count=max_label_count,
                                                mini_batch_size=val_mini_batch_size,
                                                num_segments=val_num_segments,
                                                padding=args.padding)
        elif args.training_mode == "sequence":
            raise Exception("sequence mode not supported yet")
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

    else:
        raise Exception("Unrecognized training mode")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-t", "--training-mode", type=str,
                        choices=["whole", "batch", "sequence"], required=True,
                        help="Training mode- whole loads training and validation tensors into memory at once; "
                             "batch creates training and validation tensors of size mini-batch-size using a generator; "
                             "sequence behaves similarly to batch, but uses a keras.utils.Sequence object for "
                             "multiprocessing")
    parser.add_argument("-i", "--input", type=str, required=True,
                        help="SSI file with input segments.")
    parser.add_argument("-v", "--validation", type=str, required=True,
                        help="SSI file with validation segments.")
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
    parser.add_argument("--input-mini-batch-size", type=int, default=128,
                        help="Number of sequences to train on at a time. Larger values increase speed, "
                             "but require more memory.")
    parser.add_argument("--validation-mini-batch-size", type=int, default=128,
                        help="Number of sequences to validate on at a time. Larger values increase speed, "
                             "but require more memory.")
    parser.add_argument("--max-epochs", type=int, default=60, help="Maximum number of epochs to train for.")
    parser.add_argument("--l1", type=float, help="L1 regularization rate.")
    parser.add_argument("--l2", type=float, help="L2 regularization rate.")
    parser.add_argument("--parallel", type=int, help="Run training in parallel, with n workers.")
    parser.add_argument("--padding", type=str, choices=["pre", "post"], default="post",
                        help="Whether to pad timesteps before or after sequences. Only used for whole, batch, and "
                             "sequence training modes.")

    parser_args = parser.parse_args()
    main(parser_args)
