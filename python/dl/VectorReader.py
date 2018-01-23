import argparse

from dl.VectorPropertiesReader import VectorPropertiesReader
from dl.VectorReaderText import VectorReaderText
from operator import itemgetter


class VectorReader:
    def __init__(self, path_to_vector, sample_id, vector_names, assert_example_ids=False):
        """
        :param path_to_vector: Path to the .vec file.
        :param sample_id: sample_id to read vectors from
        :param vector_names: names of vector VectorReader should read
        :param assert_example_ids: If True, test that example ids never repeat.
        """
        self.vector_reader_properties = VectorPropertiesReader("{}p".format(path_to_vector))
        self.sample_id = sample_id
        self.vector_ids = list(map(itemgetter(0), filter(lambda x: x[1]["vectorName"] in vector_names,
                                                         enumerate(self.vector_reader_properties.get_vectors()))))
        self.assert_example_ids = assert_example_ids
        if self.assert_example_ids:
            self.processed_example_ids = set([])
        version_number = self.vector_reader_properties.get_version_number()
        if version_number[0] == 0 and version_number[1] < 2:
            raise ValueError("Version number too low to be parsed by reader")
        if self.vector_reader_properties.get_vector_file_type() == "text":
            self.vector_reader = VectorReaderText(path_to_vector, self.vector_reader_properties)
        else:
            raise NotImplementedError

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception_value, traceback):
        self.vector_reader.close()

    def examples(self, return_example_id=False):
        curr_example = None
        while True:
            next_vector_line = self.vector_reader.get_next_vector_line()
            if curr_example is None:
                curr_example = ExampleVectorLines(next_vector_line.line_example_id, self.vector_ids, self.sample_id)
            if curr_example.same_example(next_vector_line.line_example_id):
                curr_example.add_vector_line(next_vector_line)
            else:
                if self.assert_example_ids:
                    if next_vector_line.line_example_id in self.processed_example_ids:
                        raise RuntimeError("Example ID % already processed".format(next_vector_line.line_example_id))
                    self.processed_example_ids.add(curr_example.example_id)
                yield curr_example.get_tuples(return_example_id)
                curr_example.add_vector_line(next_vector_line, clear=True,
                                             new_example_id=next_vector_line.line_example_id)


class ExampleVectorLines:
    def __init__(self, example_id, vector_ids, sample_id):
        self.example_id = example_id
        self.vector_lines = {}
        self.vector_ids = vector_ids
        self.sample_id = sample_id

    def set_example_id(self, example_id):
        self.example_id = example_id

    def same_example(self, other_example_id):
        return self.example_id == other_example_id

    def add_vector_line(self, vector_line, clear=False, new_example_id=None):
        if clear:
            self.example_id = new_example_id
            self.vector_lines = {}
        if vector_line.line_sample_id == self.sample_id:
            self.vector_lines[vector_line.line_vector_id] = vector_line.line_vector_elements

    def get_tuples(self, return_example_id):
        try:
            vector_lines = [self.vector_lines[vector_id] for vector_id in self.vector_ids]
        except KeyError:
            vectors_missing = list(frozenset(self.vector_ids) - frozenset(self.vector_lines.keys()))
            raise RuntimeError("Vectors missing for example id {}: {}".format(self.example_id, vectors_missing))
        if return_example_id:
            return tuple([self.example_id] + vector_lines)
        else:
            return tuple(vector_lines)


if __name__ == "__main__":
    def _print_vector_lines(example_to_print, vector_names):
        print("Example ID: {}".format(example_to_print[0]))
        all_vector_values = enumerate(example_to_print[1:])
        for vector_idx, vector_values in all_vector_values:
            print("{}: {}".format(vector_names[vector_idx], vector_values))
        print()

    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument("-i", "--input", help=".vec file to read in", type=str, required=True)
    arg_parser.add_argument("-s", "--sample-id", help="sample ID to use", type=int, default=0)
    arg_parser.add_argument("-v", "--vector_names", help="vector names to use", type=str, nargs="+")
    args = arg_parser.parse_args()
    with VectorReader(args.input, args.sample_id, args.vector_names, assert_example_ids=True) as vector_reader:
        vector_example_generator = vector_reader.examples(True)
        next_example = next(vector_example_generator)
        _print_vector_lines(next_example, args.vector_names)
        next_example = next(vector_example_generator)
        _print_vector_lines(next_example, args.vector_names)
        next_example = next(vector_example_generator)
        _print_vector_lines(next_example, args.vector_names)
        i = 0
        for next_example in vector_example_generator:
            if i == 18:
                break
            _print_vector_lines(next_example, args.vector_names)
            i += 1
