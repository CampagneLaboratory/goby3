import argparse
import csv


def _generate_reduced_vcf(input_path, output_path, chromosome):
    if chromosome is not None:
        chromosomes_to_write = set(chromosome)
    else:
        chromosomes_to_write = set(["chr{}".format(n) for n in range(1, 23)] + ["chrX"])
    with open(input_path, "r") as vcf_file, open(output_path, "w") as vcf_out_file:
        vcf_out_fields = ["CHROM", "POS", "ID", "REF", "ALT", "QUAL", "INFO", "FORMAT", "vcf_reduced"]
        vcf_out_writer = csv.DictWriter(vcf_out_file, fieldnames=vcf_out_fields, delimiter="\t", lineterminator="\n")
        vcf_out_file.write("##fileFormat=VCFv4.1\n")
        vcf_out_file.write("##originalFile={}\n".format(input_path))
        vcf_out_file.write("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n")
        vcf_out_file.write("#{}\n".format("\t".join(vcf_out_fields)))
        vcf_reader = None
        fields = None
        for line in vcf_file:
            if line.startswith("##"):
                continue
            elif line.startswith("#"):
                if vcf_reader is None:
                    fields = line[1:].split("\t")
                    vcf_reader = csv.DictReader(vcf_file, fieldnames=fields, delimiter="\t", lineterminator="\n")
                    break
                else:
                    raise Exception("Should have encountered header already")
            else:
                raise Exception("Should only be past header when using vcf_reader")
        if vcf_reader is not None:
            for row in vcf_reader:
                if row["CHROM"] in chromosomes_to_write:
                    format_fields = row["FORMAT"].split(":")
                    values = row[fields[-1]].split(":")
                    gt = dict(zip(format_fields, values))["GT"]
                    if "|" in gt:
                        gt = gt.split("|")
                    elif "/" in gt:
                        gt = gt.split("/")
                    else:
                        gt = [gt]
                    try:
                        gt = map(int, gt)
                    except ValueError:
                        raise Exception("Unknown GT format")
                    gt = sorted(gt)
                    if len(gt) == 1:
                        gt = [gt[0], gt[0]]
                    if row["ALT"] != ".":
                        alt_list = sorted(row["ALT"].split(","))
                        alts = ",".join(alt_list)
                    else:
                        alts = "."
                    out_row = {
                        "CHROM": row["CHROM"],
                        "POS": row["POS"],
                        "ID": ".",
                        "REF": row["REF"],
                        "ALT": alts,
                        "QUAL": ".",
                        "INFO": ".",
                        "FORMAT": "GT",
                    }
                    gt = "/".join(map(str, gt))
                    out_row["vcf_reduced"] = gt
                    vcf_out_writer.writerow(out_row)
        else:
            raise Exception("vcf_reader shouldn't be none")


def main(args):
    _generate_reduced_vcf(args.input, args.output, args.chromosome)


if __name__ == "__main__":
    arg_parser = argparse.ArgumentParser()
    arg_parser.add_argument("-i", "--input", type=str, required=True, help="Input VCF file")
    arg_parser.add_argument("-o", "--output", type=str, required=True, help="Output reduced VCF file")
    arg_parser.add_argument("-c", "--chromosome", nargs="*", help="Chromosomes to generate VCFs for",
                            choices=["chr{}".format(n) for n in range(1, 23)] + ["chrX"])
    args_parsed = arg_parser.parse_args()
    main(args_parsed)
