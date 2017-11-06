import argparse
import os

from dl.SegmentGenotypingClassesFunctions import ModelEvaluator


def main(args):
    # Only write header if new file
    write_header = not os.path.isfile(args.log_path)
    model_evaluator = ModelEvaluator(args.testing, args.log_path, write_header, log_epochs=False, main_model=args.model)
    model_evaluator.eval_model()
    model_evaluator.close_log()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", "--model", type=str, required=True, help="Path to model to evaluate.")
    parser.add_argument("-t", "--testing", type=str, required=True,
                        help="Path to test set directory that was preprocessed via GenerateDatasetsFromSSI.")
    parser.add_argument("-l", "--log-path", type=str, required=True,
                        help="Path to log file to use. Can be new file or existing one; if existing one, should "
                             "have been created by previous usage of EvaluateGenotypeSSI")
    parser_args = parser.parse_args()
    main(parser_args)
