#!/bin/bash

PLOT_DEFAULTS="set boxwidth 0.5; set style fill solid 0.25 border; set xtics nomirror rotate by -270"
KEY_DEFAULTS="set key outside bottom center samplen 1"

fix_names() {
  sed -i 's/LmdbJavaAgrona/LMDB DB/g' $1
  sed -i 's/LmdbJavaByteBuffer/LMDB BB/g' $1
  sed -i 's/LmdbJni/LMDB JNI/g' $1
  sed -i 's/LmdbLwjgl/LMDB JGL/g' $1
  sed -i 's/LevelDb/LevelDB/g' $1
  sed -i 's/RocksDb/RocksDB/g' $1
  sed -i 's/MapDb/MapDB/g' $1
  sed -i 's/MvStore/MVStore/g' $1
}

disk_use() {
  grep "Bytes" out-$1.txt > out-$1.tsv
  fix_names out-$1.tsv
}

layout() {
  IN="out-$1.csv"
  TMP=/tmp/working.txt
  OUT="$1-$2.dat"
  NUM_RANGE=$3
  AWK_READY=$(echo $NUM_RANGE | sed 's/ / "," /g')
  awk -F',|\r' "{ print $AWK_READY }" $IN > $OUT

  # capture revised header line and emit file without header
  HEADER="# $(head -n 1 $OUT)"
  tail -n +2 $OUT > $TMP

  # remove package name and move class name to after the method name
  sed -i -r 's/(.*)"org.lmdbjava.bench.(.*)\.(.*)",(.*)/\1"\3.\2",\4/g' $TMP

  fix_names $TMP

  # a vanilla sort will provide comparable benchmarks one after another
  sort $TMP > $OUT

  # clean up and format for plots
  sed -i 's/,/ /g' $OUT
  echo "$HEADER" > $TMP
  cat $OUT >> $TMP
  cp $TMP $OUT
  rm $TMP
}


plot_4() {
  DAT="$1.dat"
  PNG="$1.png"
  KEY=$2
  SEQ=$3
  SIZE=$4
  BENCHES="readCrc readKey readRev readSeq readXxh64 write"
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
    grep $BENCH $DAT > $TMP
    sed -i "s/$BENCH.//g" $TMP
  done
  gplot.pl -type png -mplot 3x2 -title "1M $SEQ $KEY X $SIZE Byte Values" -xlabel "" -ylabel "Ms / 1M (log)" -set "logscale y; set terminal png size 1000,700; $PLOT_DEFAULTS; $KEY_DEFAULTS" -style boxes -outfile $PNG -using '4:xtic(2)' /tmp/readCrc -using '4:xtic(2)' /tmp/readKey -using '4:xtic(2)' /tmp/readXxh64 -using '4:xtic(2)' /tmp/readSeq -using '4:xtic(2)' /tmp/write -using '4:xtic(2)' /tmp/readRev
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
   # rm -f $TMP
  done
}

plot_4_summary() {
    DAT="$1.dat"
    PNG="$1-summary.png"
    KEY=$2
    SEQ=$3
    SIZE=$4
    BENCHES="readKey readSeq write"
    for BENCH in $BENCHES; do
        TMP=/tmp/$BENCH
        grep $BENCH $DAT > $TMP
        sed -i "s/$BENCH.//g" $TMP
    done
    gplot.pl -type png -mplot 3x1 -title "1M $SEQ $KEY X $SIZE Byte Values" -xlabel "" -ylabel "Ms / 1M" -set "terminal png size 1000,350; $PLOT_DEFAULTS; $KEY_DEFAULTS" -style boxes -outfile $PNG -using '4:xtic(2)' /tmp/readKey -using '4:xtic(2)' /tmp/readSeq -using '4:xtic(2)' /tmp/write
    for BENCH in $BENCHES; do
        TMP=/tmp/$BENCH
        # rm -f $TMP
    done
}

plot_5() {
  DAT="$1.dat"
  PNG="$1.png"
  KEY=$2
  SEQ=$3
  SIZE=$4
  TIME_UNIT=$5
  BENCHES="readKey readSeq write"
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
    grep $BENCH $DAT > $TMP
    sed -i "s/$BENCH.//g" $TMP
  done
  gplot.pl -type png -mplot 3x1 -title "10M $SEQ $KEY X $SIZE Byte Values" -xlabel "" -ylabel "$TIME_UNIT / 10M (log)" -set "logscale y; set terminal png size 1000,350; $PLOT_DEFAULTS; $KEY_DEFAULTS" -style boxes -outfile $PNG -using '3:xtic(1)' /tmp/readKey -using '3:xtic(1)' /tmp/readSeq -using '3:xtic(1)' /tmp/write
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
   # rm -f $TMP
  done
}

size_fragment() {
  DAT="$1.dat"
  MD="$1.md"
  echo '| Implementation | Bytes | Overhead % |' >> $MD
  echo '| -------------- | ----: | ---------: |' >> $MD
  BASE=$(head -n 1 $DAT | cut -d " " -f 1)
  while IFS='' read -r line || [[ -n "$line" ]]; do
      SIZE=$(echo "$line" | cut -d " " -f 1)
      IMPL=$(echo "$line" | cut -d " " -f 2- | sed 's/"//g')
      OVER=$(echo "scale=4; (($SIZE - $BASE) / $BASE) * 100" | bc | rev | cut -c 3- | rev)
      echo "| $IMPL | $SIZE | $OVER |" >> $MD
  done < "$DAT"
}

FILES="1 2 3 4 5 6"

for FILE in $FILES; do rm -f $FILE-*.dat $FILE-*.png $FILE-*.md; done
for FILE in $FILES; do disk_use $FILE; done

#### Generate .dat files and plots

# out-1.csv: bm (1), score (5), sync (13), forceSafe (8), metaSync (10), writeMap (16)
# defaults: sync=false, forceSafe=false, metaSync=false, writeMap=true

layout 1 'forceSafe' '$16 $13 $10 $1 $8 $5'
grep '#\|true   "read' 1-forceSafe.dat | grep '#\|LMDB BB' > 1-forceSafe-reads.dat
rm 1-forceSafe.dat
sed -i 's/LMDB BB" true/safe"/g' 1-forceSafe-reads.dat
sed -i 's/LMDB BB" false/unsafe"/g' 1-forceSafe-reads.dat
gplot.pl -type png -title "LmdbJava ByteBuffer Safe vs Unsafe Overhead" -xlabel "" -ylabel "Ms / 1M" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '3:xtic(2)' -outfile 1-forceSafe-reads.png 1-forceSafe-reads.dat

layout 1 'sync' '$16 $10 $1 $8 $13 $5'
grep '#\|true false "write' 1-sync.dat > 1-sync-writes.dat
rm 1-sync.dat
sed -i 's/write.//g' 1-sync-writes.dat
sed -i 's/"  true/ (sync)"  true/g' 1-sync-writes.dat
sed -i 's/"  false/ (no sync)"  false/g' 1-sync-writes.dat
gplot.pl -type png -title "LMDB Sync" -xlabel "" -ylabel "Ms / 1M" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '5:xtic(3)' -outfile 1-sync-writes.png 1-sync-writes.dat

layout 1 'writeMap' '$13 $10 $1 $8 $16 $5'
grep '#\|false false "write' 1-writeMap.dat > 1-writeMap-writes.dat
rm 1-writeMap.dat
sed -i 's/write.//g' 1-writeMap-writes.dat
sed -i 's/"  true/ (wm)"  true/g' 1-writeMap-writes.dat
sed -i 's/"  false/ (!wm)"  false/g' 1-writeMap-writes.dat
gplot.pl -type png -title "LMDB Write Map" -xlabel "" -ylabel "Ms / 1M" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '5:xtic(3)' -outfile 1-writeMap-writes.png 1-writeMap-writes.dat

layout 1 'metaSync' '$16 $13 $1 $8 $10 $5'
grep '#\|true false "write' 1-metaSync.dat > 1-metaSync-writes.dat
rm 1-metaSync.dat
sed -i 's/write.//g' 1-metaSync-writes.dat
sed -i 's/"  true/ (ms)"  true/g' 1-metaSync-writes.dat
sed -i 's/"  false/ (!ms)"  false/g' 1-metaSync-writes.dat
gplot.pl -type png -title "LMDB Metasync" -xlabel "" -ylabel "Ms / 1M" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '5:xtic(3)' -outfile 1-metaSync-writes.png 1-metaSync-writes.dat

# out-2.csv: bm (1), score (5), sequential (10), valSize (12)
grep 'sequential-false' out-2.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([a-z|A-Z]+).*-valSize-([0-9]+).*/\1 "\2 \3"/g' > 2-size.dat
gplot.pl -type png -title "Native Library Disk Use 1M Rnd X Approx 2-16 KB Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 2-size.png 2-size.dat

# out-3.csv: bm (1), score (5), batchSize (8)
layout 3 'batchSize' '$1 $8 $5'
grep '#\|"write' 3-batchSize.dat > 3-batchSize-writes.dat
rm 3-batchSize.dat
sed -i -r 's/"write\.(.*)" ([0-9]+)000000 (.*)/"\1 \2M" \3/g' 3-batchSize-writes.dat
gplot.pl -type png -title "Native LSM Write Speed by Batch Size (10M Seq X 8,176 Byte Values)" -xlabel "Batch Size" -ylabel "Ms / 1M" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '2:xtic(1)' -outfile 3-batchSize-writes.png 3-batchSize-writes.dat

# out-4.csv: bm (1), score (5), sequential (13), intKey (10)
layout 4 'seq' '$10 $1 $13 $5'
grep '#\|true "' 4-seq.dat | grep '#\|" true' > 4-intKey-seq.dat
grep '#\|false "' 4-seq.dat | grep '#\|" false ' > 4-strKey-rnd.dat
grep '#\|true "' 4-seq.dat | grep '#\|" false ' > 4-intKey-rnd.dat
grep '#\|false "' 4-seq.dat | grep '#\|" true ' > 4-strKey-seq.dat
rm 4-seq.dat
plot_4 4-intKey-seq "Int" "Seq" "100"
plot_4 4-strKey-seq "Str" "Seq" "100"
plot_4 4-intKey-rnd "Int" "Rnd" "100"
plot_4 4-strKey-rnd "Str" "Rnd" "100"
plot_4_summary 4-intKey-rnd "Int" "Rnd" "100"

grep 'intKey-true-num-1000000-sequential-false' out-4.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/\1 "\2"/g' > 4-size.dat
echo '104000000 "(Flat Array)"' > 4-size-sorted.dat
sort -n 4-size.dat | awk '!(NR%3)' | sort -n >> 4-size-sorted.dat
rm 4-size.dat
mv 4-size-sorted.dat 4-size.dat
size_fragment "4-size"
gplot.pl -type png -title "Library Disk Use 1M Rnd X 100 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 4-size.png 4-size.dat

# out-5.csv: bm (1), score (5), sequential (13)
layout 5 'seq' '$1 $13 $5'
grep '#\|" true' 5-seq.dat > 5-intKey-seq.dat
grep '#\|" false' 5-seq.dat > 5-intKey-rnd.dat
rm 5-seq.dat
plot_5 5-intKey-seq "Int" "Seq" "2,026" "Ms"
plot_5 5-intKey-rnd "Int" "Rnd" "2,026" "Ms"

grep 'intKey-true-num-10000000-sequential-false' out-5.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/\1 "\2"/g' >> 5-size.dat
echo '20300000000 "(Flat Array)"' > 5-size-sorted.dat
sort -n 5-size.dat >> 5-size-sorted.dat
rm 5-size.dat
mv 5-size-sorted.dat 5-size.dat
size_fragment "5-size"
gplot.pl -type png -title "Library Disk Use 10M Rnd X 2,026 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 5-size.png 5-size.dat

# out-6.csv: bm (1), score (5), valSize (15)
layout 6 'all' '$1 $15 $5'
grep '#\|" 4080' 6-all.dat > 6-intKey-rnd-4080.dat
grep '#\|" 8176' 6-all.dat > 6-intKey-rnd-8176.dat
grep '#\|" 16368' 6-all.dat > 6-intKey-rnd-16368.dat
rm 6-all.dat
plot_5 6-intKey-rnd-4080  "Int" "Rnd"  "4,080" "Sec"
plot_5 6-intKey-rnd-8176  "Int" "Rnd"  "8,176" "Sec"
plot_5 6-intKey-rnd-16368 "Int" "Rnd" "16,368" "Sec"

echo '40840000000 "(Flat Array)"' > 6-size-4080.dat
echo '81800000000 "(Flat Array)"' > 6-size-8176.dat
echo '163720000000 "(Flat Array)"' > 6-size-16368.dat
grep 'valRandom-false-valSize-4080' out-6.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/\1 "\2"/g' | sort -n >> 6-size-4080.dat
grep 'valRandom-false-valSize-8176' out-6.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/\1 "\2"/g' | sort -n >> 6-size-8176.dat
grep 'valRandom-false-valSize-16368' out-6.tsv | grep 'after-close' | sed -r 's/Bytes\tafter-close\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/\1 "\2"/g' | sort -n >> 6-size-16368.dat
size_fragment "6-size-4080"
size_fragment "6-size-8176"
size_fragment "6-size-16368"
gplot.pl -type png -title "Library Disk Use 10M Rnd X 4,080 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 6-size-4080.png 6-size-4080.dat
gplot.pl -type png -title "Library Disk Use 10M Rnd X 8,176 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 6-size-8176.png 6-size-8176.dat
gplot.pl -type png -title "Library Disk Use 10M Rnd X 4,080 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; $PLOT_DEFAULTS" -style boxes -using '1:xtic(2)' -outfile 6-size-16368.png 6-size-16368.dat
