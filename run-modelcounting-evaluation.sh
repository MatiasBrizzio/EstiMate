#!/bin/bash -l
FILE=case-studies/modelcounting/10_formulas.form 
OUT=src/sis/SIS.java
pfile=src/sis/SIS.preds
I=1
K=$1
# while read -r line; do
# 	echo "$line"
# 	RESULT_DIR="resultMC/k$K/$I/"
# 	mkdir -p $RESULT_DIR
# 	./modelcount.sh -vars=a,b,c -k=$K -b=case-studies/modelcounting/50_formulas.form -out="$RESULT_DIR/out.txt"
#    	I=$((I+1))

#  	done < "$FILE"

RESULT_DIR="result"
while [ "$I" -le "$K" ]; do
	RESULT_DIR="MC/k$K/S$I/"
	mkdir -p $RESULT_DIR
	./modelcount.sh -vars=a,b,c -k=$I -out="$RESULT_DIR/out.out" case-studies/modelcounting/S$I.form
	  I=$(($I + 1))
done