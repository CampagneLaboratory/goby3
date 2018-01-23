from dl.VectorReader import VectorReader

import numpy as np


class VectorReaderText(VectorReader):
    def __init__(self, path_to_vector, assert_example_id=False):
        """
        :param path_to_vector: Path to the .vec file. 
        :param assert_example_id: If True, test that example ids never repeat.
        """
        super().__init__(path_to_vector)
        self.vector_fp = open(path_to_vector)
        self.assert_example_ids=assert_example_id
        if assert_example_id:
            self.processed_example_ids = set([])

    def get_record_vector_lines(self):
        curr_example_id = None
        curr_example_vector_lines = []
        for line in self.vector_fp:
            line_split = line.strip().split()
            line_example_id = np.uint32(line_split[1])
            sample_id = np.uint32(line_split[0])
            vector_id = np.uint32(line_split[2])
            vector_elements = np.array(line_split[3:], dtype=np.float32)
            if curr_example_id is None:
                curr_example_id = line_example_id
            if curr_example_id == line_example_id:
                curr_example_vector_lines.append((sample_id, vector_id, vector_elements))
            else:
                yield curr_example_id, curr_example_vector_lines
                if self.assert_example_ids:
                    if line_example_id in self.processed_example_ids:
                        raise RuntimeError("Example ID % already processed".format(line_example_id))
                    self.processed_example_ids.add(curr_example_id)

                curr_example_id = line_example_id
                curr_example_vector_lines = [(sample_id, vector_id, vector_elements)]

    def close(self):
        self.vector_fp.close()
