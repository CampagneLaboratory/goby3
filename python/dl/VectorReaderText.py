from dl.VectorReader import VectorReader

import numpy as np


class VectorReaderText(VectorReader):
    def __init__(self, path_to_vector):
        super().__init__(path_to_vector)
        self.vector_fp = open(path_to_vector)

    def get_vector_lines(self):
        for line in self.vector_fp:
            line_split = line.strip().split()
            sample_id = int(line_split[0])
            example_id = int(line_split[1])
            vector_id = int(line_split[2])
            vector_elements = np.array(line_split[3:], dtype=np.float32)
            yield sample_id, example_id, vector_id, vector_elements

    def close(self):
        self.vector_fp.close()
