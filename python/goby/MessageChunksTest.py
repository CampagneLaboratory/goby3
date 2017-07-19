import unittest

import Reads_pb2
import Alignments_pb2

import MessageChunks


class CheckChunks(unittest.TestCase):
    def testIterate(self):

        for chunk in MessageChunks.MessageChunksGenerator(
                "../../test-data/compact-reads/s_1_sequence_short.compact-reads"):
            collection = Reads_pb2.ReadCollection()
            collection.ParseFromString(chunk)
            print("chunk has {} reads", len(collection.reads),flush=True)
            self.assertTrue(len(collection.reads) > 0)

    def testIterateWithType(self):
        collection = Reads_pb2.ReadCollection()
        for readCollection in MessageChunks.MessageChunksGenerator(
                "../../test-data/compact-reads/s_1_sequence_short.compact-reads", collectionContainer=collection):
            print("chunk has {} reads", len(readCollection.reads),flush=True)
            self.assertTrue(len(readCollection.reads) > 0)

    def testIterateAlignment(self):

        for chunk in MessageChunks.MessageChunksGenerator("../../test-data/bam/Example.entries"):
            collection = Alignments_pb2.AlignmentCollection()
            collection.ParseFromString(chunk)
            print("chunk has {} entries", len(collection.alignment_entries),flush=True)
            self.assertTrue(len(collection.alignment_entries) > 0)


if __name__ == '__main__':
    unittest.main()
