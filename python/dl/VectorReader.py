from abc import ABC, abstractmethod

from dl.VectorPropertiesReader import VectorPropertiesReader


class VectorReader(ABC):
    def __init__(self, path_to_vector):
        self.vector_properties = VectorPropertiesReader("{}p".format(path_to_vector))

    def __enter__(self):
        return self

    def __exit__(self, exception_type, exception_value, traceback):
        self.close()

    def get_vector_properties(self):
        return self.vector_properties

    @abstractmethod
    def get_record_vector_lines(self):
        pass

    @abstractmethod
    def close(self):
        pass
