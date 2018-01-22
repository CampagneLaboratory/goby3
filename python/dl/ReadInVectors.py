import argparse

from dl.VectorReaderText import VectorReaderText


def _print_vector_lines(example_id_to_print, vector_lines_to_print):
    print("Example ID: {}".format(example_id_to_print))
    for vector_line in vector_lines_to_print:
        print("Sample ID: {} | Vector ID: {} | Array: {}".format(vector_line[0], vector_line[1], vector_line[2]))
    print()


if __name__ == "__main__":
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument("-i", "--input", help=".vec file to read in", type=str, required=True)
    arg_parser.add_argument("-t", "--type", help="type of reader to use", type=str, choices=["text", "binary"],
                            required=True)
    args = arg_parser.parse_args()
    if args.type == "text":
        with VectorReaderText(args.input) as vector_text_reader:
            vector_line_tuple_generator = vector_text_reader.get_record_vector_lines()
            example_id, vector_lines = next(vector_line_tuple_generator)
            _print_vector_lines(example_id, vector_lines)
            example_id, vector_lines = next(vector_line_tuple_generator)
            _print_vector_lines(example_id, vector_lines)
            example_id, vector_lines = next(vector_line_tuple_generator)
            _print_vector_lines(example_id, vector_lines)
            i = 0
            for vector_line_tuple in vector_line_tuple_generator:
                example_id, vector_lines = vector_line_tuple
                _print_vector_lines(example_id, vector_lines)
                i += 1
                if i == 3:
                    break
    elif args.type == "binary":
        raise NotImplementedError("No binary reader yet")
    else:
        raise NotImplementedError("Unknown reader type")

