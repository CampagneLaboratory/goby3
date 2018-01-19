import argparse

from dl.VectorReaderText import VectorReaderText

if __name__ == "__main__":
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument("-i", "--input", help=".vec file to read in", type=str, required=True)
    arg_parser.add_argument("-t", "--type", help="type of reader to use", type=str, choices=["text", "binary"],
                            required=True)
    args = arg_parser.parse_args()
    if args.type == "text":
        vector_text_reader = VectorReaderText(args.input)
        print(next(vector_text_reader.get_vector_lines()))
        print(next(vector_text_reader.get_vector_lines()))
        print(next(vector_text_reader.get_vector_lines()))
        i = 0
        for vector_line in vector_text_reader.get_vector_lines():
            if i == 3:
                break
            print(vector_line)
            i += 1
        vector_text_reader.close()
    elif args.type == "binary":
        raise NotImplementedError("No binary reader yet")
    else:
        raise NotImplementedError("Unknown reader type")
