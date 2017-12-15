#!/usr/bin/env bash
function dieIfError {
    if [ ! $? == 0 ]; then
     echo "An error was encountered ($1)"
     exit;
    fi
}

function assertRTGInstalled {
    echo no | rtg version >/dev/null 2>&1 || { echo >&2 "This script requires rtg but it's not installed. Aborting. Install rtg (see https://github.com/genome-in-a-bottle/giab_FAQ) and add the rtg executable to your path, then try again."; exit 1; }
}
assertRTGInstalled

function assertVcfToolsInstalled {
   echo done| vcf-sort >/dev/null 2>&1 || { echo >&2 "This script requires vcf-sort but it's not installed. Aborting. Install vcf-tools (see https://sourceforge.net/projects/vcftools/files/) and add the vcf-sort executable to your path, then try again."; exit 1; }
}
assertVcfToolsInstalled

function assertBedtoolsInstalled {
   bedtools --version  >/dev/null 2>&1 || { echo >&2 "This script requires bedtools but it's not installed. Aborting. Install bedtools (see http://bedtools.readthedocs.io/en/latest/content/installation.html) and add the bedtools executable to your path, then try again."; exit 1; }
}
assertBedtoolsInstalled

if [ -e configure.sh ]; then
    echo "Loading configure.sh"
    source configure.sh
fi

NUM_ARGS="$#"
NUM_ARGS_EXPECTED="${NUM_ARGS}"

if [ -z "${OUTPUT_PREFIX+set}" ]; then
    NUM_ARGS_EXPECTED=3
fi

if [ -z "${NO_OUTPUT_ORIGINAL+set}" ]; then
    OUTPUT_ORIGINAL=true
fi

if [ -z "${NO_OUTPUT_ERROR+set}" ]; then
    OUTPUT_ERROR=true
fi

if [ "${NUM_ARGS}" == 3 ]; then
    unset OUTPUT_PREFIX
fi

if [ ${NUM_ARGS} != ${NUM_ARGS_EXPECTED} ] || [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo "Usage: evaluate-genotypes-ssi-keras.sh [model-path test-dir-path output-prefix]"
    echo "The env variables GOLD_STANDARD_VCF_SNP_GZ GOLD_STANDARD_VCF_INDEL_GZ and GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ can be used to change the VCFs and confident region bed."
    echo "The first run downloads these files from the Genome in a Bottle for sample NA12878 when the variables are not defined."
    echo "To skip VCF prediction and use outputted VCFs and BEDs from a previous evaluate run, set OUTPUT_PREFIX to common prefix for SNP and INDEL VCF and BED files, as generated previously."
    echo "Arguments are optional only when OUTPUT_PREFIX is set."
    echo "GOBY_VERSION can be set to specify the version of Goby in the outputted VCF files."
    exit 1
fi

if [ -z "${GOLD_STANDARD_VCF_SNP_GZ+set}" ] || [ -z "${GOLD_STANDARD_VCF_INDEL_GZ+set}" ]; then
    if [ -z "${GOLD_STANDARD_VCF_GZ+set}" ]; then
        echo "Downloading Gold standard Genome in a Bottle VCF. Define GOLD_STANDARD_VCF_GZ to use an alternate Gold Standard."
        rm -fr HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf_phased.vcf.gz.*
        wget ftp://ftp-trace.ncbi.nlm.nih.gov/giab/ftp/release/NA12878_HG001/NISTv3.3.1/GRCh37/HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf_phased.vcf.gz
        mv HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf_phased.vcf.gz GIAB-NA12878-confident.vcf.gz
        # add "chr prefix:"
        gzip -c -d GIAB-NA12878-confident.vcf.gz|awk '{if($0 !~ /^#/) print "chr"$0; else print $0}' >GIAB-NA12878-confident-chr.vcf
        bgzip -f GIAB-NA12878-confident-chr.vcf
        tabix -f GIAB-NA12878-confident-chr.vcf.gz
        echo 'export GOLD_STANDARD_VCF_GZ="GIAB-NA12878-confident-chr.vcf.gz"' >>configure.sh
        export GOLD_STANDARD_VCF_GZ="GIAB-NA12878-confident-chr.vcf.gz"
    else
        echo "Formatting GOLD_STANDARD_VCF_GZ VCF for SNPs and indels"
    fi

    # remove non-SNPs:
    gzip -c -d  ${GOLD_STANDARD_VCF_GZ} |awk '{if($0 !~ /^#/) { if (length($4)==1 && length($5)==1) print $0;}  else {print $0}}' >GOLD-confident-chr-snps.vcf
    bgzip -f GOLD-confident-chr-snps.vcf
    tabix -f GOLD-confident-chr-snps.vcf.gz
    export GOLD_STANDARD_VCF_SNP_GZ="GOLD-confident-chr-snps.vcf.gz"

    # keep only indels:
    gzip -c -d  ${GOLD_STANDARD_VCF_GZ} |awk '{if($0 !~ /^#/) { if (length($4)!=1 || length($5)!=1) print $0;}  else {print $0}}' >GOLD-confident-chr-indels.vcf
    bgzip -f GOLD-confident-chr-indels.vcf
    tabix -f GOLD-confident-chr-indels.vcf.gz
    export GOLD_STANDARD_VCF_INDEL_GZ="GOLD-confident-chr-indels.vcf.gz"
    echo 'export GOLD_STANDARD_VCF_SNP_GZ="GOLD-confident-chr-snps.vcf.gz"' >>configure.sh
    echo 'export GOLD_STANDARD_VCF_INDEL_GZ="GOLD-confident-chr-indels.vcf.gz"' >>configure.sh
    echo "Gold standard VCF downloaded for NA12878 (SNPs) and named in configure.sh. Edit GOLD_STANDARD_VCF_SNP_GZ/GOLD_STANDARD_VCF_INDEL_GZ to switch to a different gold-standard validation VCF."
fi


if [ -z "${GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ+set}" ]; then
    echo "Downloading Gold standard Genome in a Bottle Confident Regions (bed)"
    rm -fr HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf.bed*
    wget ftp://ftp-trace.ncbi.nlm.nih.gov/giab/ftp/release/NA12878_HG001/NISTv3.3.1/GRCh37/HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf.bed
    mv HG001_GRCh37_GIAB_highconf_CG-IllFB-IllGATKHC-Ion-10X-SOLID_CHROM1-X_v.3.3.1_highconf.bed GIAB-NA12878-confident-regions.bed
    # add "chr prefix:"
    cat  GIAB-NA12878-confident-regions.bed |awk '{print "chr"$1"\t"$2"\t"$3}' >GIAB-NA12878-confident-regions-chr.bed
    bgzip -f GIAB-NA12878-confident-regions-chr.bed
    tabix -f GIAB-NA12878-confident-regions-chr.bed.gz
    rm GIAB-NA12878-confident-regions.bed

    GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ="GIAB-NA12878-confident-regions-chr.bed.gz"
    echo 'export GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ="GIAB-NA12878-confident-regions-chr.bed.gz"' >>configure.sh
    echo "Gold standard confident regions downloaded for NA12878  and named in configure.sh. Edit GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ to switch to a different gold-standard confident region bed file."
fi

if [ -z "${RTG_TEMPLATE+set}" ]; then
      RTG_TEMPLATE="hg19.sdf"
      echo "RTG_TEMPLATE not set, using default=${RTG_TEMPLATE}"
fi

if [ ! -e "${RTG_TEMPLATE}" ]; then
     echo "You must install an rtg template, or build one (rtg format file.fa -o template.sdf) in the current directory. See rtg downloads at http://www.realtimegenomics.com/news/pre-formatted-reference-datasets/"
     exit 1;
fi

if [ -z "${OUTPUT_PREFIX+set}" ]; then
    echo "OUTPUT_PREFIX not defined. Running GenerateVCF first..."
    arg_string="goby-python-with-path.sh dl/GenerateVCF.py --model "$1" --testing "$2" --prefix "$3""
    if [ -n "${GOBY_VERSION}" ]; then
        arg_string=""${arg_string}" --version "${GOBY_VERSION}""
    fi
    if [ -n "${OUTPUT_ORIGINAL}" ]; then
        arg_string=""${arg_string}" --generate-original-vcf"
    fi
    if [ -n "${OUTPUT_ERROR}" ]; then
        arg_string=""${arg_string}" --generate-error-vcf"
    fi
    eval ${arg_string}
    dieIfError "Failed to generate VCF with arguments $*"
    echo "Evaluation with rtg vcfeval starting.."
    OUTPUT_PREFIX="$3"
fi

curr_date="`date +%m`_`date +%d`"
RTG_OUTPUT_FOLDER="output/${curr_date}"
mkdir -p ${RTG_OUTPUT_FOLDER}
RTG_OUTPUT_FOLDER="${RTG_OUTPUT_FOLDER}/$((`ls -l ${RTG_OUTPUT_FOLDER} | grep -c ^d` + 1))"
mkdir -p ${RTG_OUTPUT_FOLDER}

VCF_OUTPUT=${OUTPUT_PREFIX}.vcf
BED_OUTPUT=${OUTPUT_PREFIX}.bed

cp ${VCF_OUTPUT} ${RTG_OUTPUT_FOLDER}
cp ${BED_OUTPUT} ${RTG_OUTPUT_FOLDER}

VCF_OUTPUT_SORTED=`basename ${VCF_OUTPUT} .vcf`-sorted.vcf

if [ ! -e "${VCF_OUTPUT_SORTED}.gz" ]; then
    cat ${VCF_OUTPUT} | vcf-sort > ${VCF_OUTPUT_SORTED}
    dieIfError "Unable to sort prediction VCF."

    bgzip -f ${VCF_OUTPUT_SORTED}
    tabix -f -p vcf ${VCF_OUTPUT_SORTED}.gz
fi

BED_OUTPUT_SORTED=`basename ${BED_OUTPUT} .bed`-sorted.bed

if [ ! -e "${BED_OUTPUT_SORTED}.gz" ]; then
    # note -V option on chromosome key below is necessary on Centos 7, with sort version 8.22,
    # see https://github.com/chapmanb/bcbio-nextgen/issues/624
    sort -k1,1V -k2,2n ${BED_OUTPUT} | bedtools merge > ${BED_OUTPUT_SORTED}
    bgzip -f ${BED_OUTPUT_SORTED}
    tabix -f -p bed ${BED_OUTPUT_SORTED}.gz
fi

cp ${VCF_OUTPUT_SORTED}.gz* ${RTG_OUTPUT_FOLDER}
cp ${BED_OUTPUT_SORTED}.gz* ${RTG_OUTPUT_FOLDER}


VCF_OUTPUT_SORTED_SNPS=`basename ${VCF_OUTPUT_SORTED} .vcf`-snps.vcf
gzip -c -d ${VCF_OUTPUT_SORTED}.gz | awk '{if($0 !~ /^#/) { if (length($4)==1 && length($5)==1) print $0;}  else {print $0}}' > ${VCF_OUTPUT_SORTED_SNPS}
bgzip -f ${VCF_OUTPUT_SORTED_SNPS}
tabix -f -p vcf ${VCF_OUTPUT_SORTED_SNPS}.gz

VCF_OUTPUT_SORTED_INDELS=`basename ${VCF_OUTPUT_SORTED} .vcf`-indels.vcf
gzip -c -d ${VCF_OUTPUT_SORTED}.gz | awk '{if($0 !~ /^#/) { if (length($4)!=1 || length($5)!=1) print $0;}  else {print $0}}' > ${VCF_OUTPUT_SORTED_INDELS}
bgzip -f ${VCF_OUTPUT_SORTED_INDELS}
tabix -f -p vcf ${VCF_OUTPUT_SORTED_INDELS}.gz

rtg vcfeval --baseline=${GOLD_STANDARD_VCF_SNP_GZ}  \
        -c ${VCF_OUTPUT_SORTED_SNPS}.gz -o ${RTG_OUTPUT_FOLDER}/snps --template=${RTG_TEMPLATE}  \
            --evaluation-regions=${GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ} \
            --bed-regions=${BED_OUTPUT_SORTED}.gz \
            --vcf-score-field=P  --sort-order=descending
dieIfError "Failed to run rtg vcfeval for SNPs."
rtg vcfeval --baseline=${GOLD_STANDARD_VCF_INDEL_GZ}  \
        -c ${VCF_OUTPUT_SORTED_INDELS}.gz -o ${RTG_OUTPUT_FOLDER}/indels --template=${RTG_TEMPLATE}  \
            --evaluation-regions=${GOLD_STANDARD_CONFIDENT_REGIONS_BED_GZ} \
            --bed-regions=${BED_OUTPUT_SORTED}.gz \
            --vcf-score-field=P  --sort-order=descending
dieIfError "Failed to run rtg vcfeval."

cp ${VCF_OUTPUT_SORTED_SNPS}.gz* ${RTG_OUTPUT_FOLDER}/snps
cp ${VCF_OUTPUT_SORTED_INDELS}.gz* ${RTG_OUTPUT_FOLDER}/indels

RTG_ROCPLOT_OPTIONS="--scores"
rtg rocplot ${RTG_OUTPUT_FOLDER}/snps/snp_roc.tsv.gz --svg ${RTG_OUTPUT_FOLDER}/snps/SNP-ROC.svg ${RTG_ROCPLOT_OPTIONS} --title="SNPs, model ${1}"
dieIfError "Unable to generate SNP ROC plot."

rtg rocplot ${RTG_OUTPUT_FOLDER}/snps/snp_roc.tsv.gz -P --svg ${RTG_OUTPUT_FOLDER}/snps/SNP-PrecisionRecall.svg ${RTG_ROCPLOT_OPTIONS} --title="SNPs, model ${1}"
dieIfError "Unable to generate SNP Precision Recall plot."

rtg rocplot ${RTG_OUTPUT_FOLDER}/indels/non_snp_roc.tsv.gz -P --svg ${RTG_OUTPUT_FOLDER}/indels/INDEL-PrecisionRecall.svg ${RTG_ROCPLOT_OPTIONS} --title="INDELs, model ${1}"
dieIfError "Unable to generate indel Precision Recall plot."

rtg rocplot ${RTG_OUTPUT_FOLDER}/indels/non_snp_roc.tsv.gz --svg ${RTG_OUTPUT_FOLDER}/indels/INDEL-ROC.svg  ${RTG_ROCPLOT_OPTIONS} --title="INDELs, model ${1}"
dieIfError "Unable to generate indel ROC plot."




