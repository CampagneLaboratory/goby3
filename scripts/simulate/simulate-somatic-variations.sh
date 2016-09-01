#!/usr/bin/env bash

# This script requires bbmap (https://sourceforge.net/projects/bbmap/),
# goby (http://goby.campagnelab.org) and wget
wget http://ftp.ensembl.org/pub/release-85/fasta/homo_sapiens/cdna/Homo_sapiens.GRCh38.cdna.all.fa.gz
# extract about 10000 cDNA sequences longer than 200bp:
reformat.sh in=Homo_sapiens.GRCh38.cdna.all.fa  out=sample.fasta minlength=200  samplerate=0.05 overwrite=true

# Add substitutions at a rate of 1 per 250 base pairs
mutate.sh in=sample.fasta out=mutated-snp-indels.fasta  subrate=0.004

# Map to the genome, removing ambiguous mappers:
bbmap.sh -Xmx13g build=5 in=mutated-snp-indels.fasta ref=/data/genomes/1000G.fa out=mutated-snp-indels.sam \
                vslow=true  ambiguous=toss usemodulo=true maxindel=100000 xstag=firststrand intronlen=10

# Map to the genome, removing ambiguous mappers:
bbmap.sh -Xmx13g in=sample.fasta ref=/data/genomes/1000G.fa out=sample.sam \
                        ambiguous=toss usemodulo=true maxindel=100000 xstag=firststrand intronlen=10

# Convert sam to goby format:
export PATH=${PATH}:~/IdeaProjects/git/goby
goby 4g stc -i mutated-snp-indels.sam -o mutated-snp-indels -g /data/genomes/1000G
goby 4g stc -i sample.sam  -o sample -g /data/genomes/1000G


goby 4g sort mutated-snp-indels.entries -o mutated-snp-indels-sorted
goby 4g sort sample.entries -o sample-sorted

goby 4g discover-sequence-variants sample-sorted mutated-snp-indels-sorted -f genotypes --genome /data/genomes/1000G -o mutated-snp-indels.vcf --call-indels true -n 1 -t 1

grep "0/0" mutated-snp-indels.vcf |grep "0/1" |grep -v '##' |cut -f 1,2,4,5 >true-mutations.tsv

NUM_READS=10000000
randomreads.sh build=2 ref=sample.fasta  out=germline-reads.fq reads=${NUM_READS} seed=9484 maxq=40 midq=35 minq=28
randomreads.sh build=3 ref=mutated-snp-indels.fasta  out=somatic-reads.fq reads=${NUM_READS} seed=232323 maxq=40 midq=35 minq=28

# convert to Goby read format to support parallel alignment:
goby 1g fasta-to-compact *-reads.fq  --quality-encoding  SANGER --parallel
