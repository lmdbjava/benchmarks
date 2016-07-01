#!/bin/bash

fix_names() {
  sed -i 's/LmdbJavaAgrona/LMDB DB/g' $1
  sed -i 's/LmdbJavaByteBuffer/LMDB BB/g' $1
  sed -i 's/LmdbJni/LMDB JNI/g' $1
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
  gplot.pl -type png -mplot 3x2 -title "1M $SEQ $KEY X $SIZE Byte Values" -xlabel "" -ylabel "Ms / 1 M (log)" -set "logscale y; set terminal png size 1000,700; set key top right; set xtics nomirror rotate by -270" -pointsize 1 -style points -outfile $PNG -using '4:xtic(2)' /tmp/readCrc -using '4:xtic(2)' /tmp/readKey -using '4:xtic(2)' /tmp/readRev -using '4:xtic(2)' /tmp/readSeq -using '4:xtic(2)' /tmp/readXxh64 -using '4:xtic(2)' /tmp/write
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
  BENCHES="readKey readSeq write"
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
    grep $BENCH $DAT > $TMP
    sed -i "s/$BENCH.//g" $TMP
  done
  gplot.pl -type png -mplot 3x1 -title "10M $SEQ $KEY X $SIZE Byte Values" -xlabel "" -ylabel "Ms / 10 M (log)" -set "logscale y; set terminal png size 1000,350; set key top right; set xtics nomirror rotate by -270" -pointsize 1 -style points -outfile $PNG -using '3:xtic(1)' /tmp/readKey -using '3:xtic(1)' /tmp/readSeq -using '3:xtic(1)' /tmp/write
  for BENCH in $BENCHES; do
    TMP=/tmp/$BENCH
   # rm -f $TMP
  done
}

FILES="1 2 3 4 5"

for FILE in $FILES; do rm -f $FILE-*.dat $FILE-*.png; done
for FILE in $FILES; do disk_use $FILE; done

#### Generate .dat files and plots

# out-1.csv: bm (1), score (5), sync (13), forceSafe (8), metaSync (10), writeMap (16)
# defaults: sync=false, forceSafe=false, metaSync=false, writeMap=true

layout 1 'forceSafe' '$16 $13 $10 $1 $8 $5'
grep '#\|true   "read' 1-forceSafe.dat | grep '#\|LMDB BB' > 1-forceSafe-reads.dat
rm 1-forceSafe.dat
sed -i 's/LMDB BB" true/safe"/g' 1-forceSafe-reads.dat
sed -i 's/LMDB BB" false/unsafe"/g' 1-forceSafe-reads.dat
gplot.pl -type png -title "LmdbJava ByteBuffer Safe vs Unsafe Overhead" -xlabel "" -ylabel "Ms / 1M" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '3:xtic(2)' -outfile 1-forceSafe-reads.png 1-forceSafe-reads.dat

layout 1 'sync' '$16 $10 $1 $8 $13 $5'
grep '#\|true false "write' 1-sync.dat > 1-sync-writes.dat
rm 1-sync.dat
sed -i 's/write.//g' 1-sync-writes.dat
sed -i 's/"  true/ (sync)"  true/g' 1-sync-writes.dat
sed -i 's/"  false/ (no sync)"  false/g' 1-sync-writes.dat
gplot.pl -type png -title "LMDB Sync" -xlabel "" -ylabel "Ms / 1M" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '5:xtic(3)' -outfile 1-sync-writes.png 1-sync-writes.dat

layout 1 'writeMap' '$13 $10 $1 $8 $16 $5' 
grep '#\|false false "write' 1-writeMap.dat > 1-writeMap-writes.dat
rm 1-writeMap.dat
sed -i 's/write.//g' 1-writeMap-writes.dat
sed -i 's/"  true/ (wm)"  true/g' 1-writeMap-writes.dat
sed -i 's/"  false/ (!wm)"  false/g' 1-writeMap-writes.dat
gplot.pl -type png -title "LMDB Write Map" -xlabel "" -ylabel "Ms / 1M" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '5:xtic(3)' -outfile 1-writeMap-writes.png 1-writeMap-writes.dat

layout 1 'metaSync' '$16 $13 $1 $8 $10 $5'
grep '#\|true false "write' 1-metaSync.dat > 1-metaSync-writes.dat
rm 1-metaSync.dat
sed -i 's/write.//g' 1-metaSync-writes.dat
sed -i 's/"  true/ (ms)"  true/g' 1-metaSync-writes.dat
sed -i 's/"  false/ (!ms)"  false/g' 1-metaSync-writes.dat
gplot.pl -type png -title "LMDB Metasync" -xlabel "" -ylabel "Ms / 1M" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '5:xtic(3)' -outfile 1-metaSync-writes.png 1-metaSync-writes.dat

# out-2.csv: bm (1), score (5), sequential (10), valSize (12)
grep 'sequential-false' out-2.tsv | grep -v 'compacted' | sed -r 's/Bytes\t([0-9]+)\torg.lmdbjava.bench.([a-z|A-Z]+).*-valSize-([0-9]+).*/"\2 \3" \1/g' > 2-size.dat
gplot.pl -type png -title "Native Library Disk Use 1M Rnd X Approx 2,000 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '2:xtic(1)' -outfile 2-size.png 2-size.dat

# out-3.csv: bm (1), score (5), batchSize (8)
layout 3 'batchSize' '$1 $8 $5'
grep '#\|"write' 3-batchSize.dat > 3-batchSize-writes.dat
rm 3-batchSize.dat
sed -i -r 's/"write.LevelDB" ([0-9]+) (.*)/\1 \2/g' 3-batchSize-writes.dat
gplot.pl -type png -title "LevelDB Write Speed by Batch Size (1M Seq X 2025 Byte Values)" -xlabel "Batch Size" -ylabel "Ms / 1M W" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '1:2' -outfile 3-batchSize-writes.png 3-batchSize-writes.dat

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

grep 'intKey-true-num-1000000-sequential-false' out-4.tsv | grep -v "compacted" | sed -r 's/Bytes\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/"\2" \1/g' > 4-size.dat
sort -t ' ' -k 1 -k 2n 4-size.dat | awk '!(NR%3)' | sort -t , -k 2n > 4-size-biggest.dat
rm 4-size.dat
gplot.pl -type png -title "Library Disk Use 1M Rnd X 100 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '2:xtic(1)' -outfile 4-size-biggest.png 4-size-biggest.dat

# out-5.csv: bm (1), score (5), sequential (13)
layout 5 'seq' '$1 $13 $5'
grep '#\|" true' 5-seq.dat > 5-intKey-seq.dat
grep '#\|" false' 5-seq.dat > 5-intKey-rnd.dat
rm 5-seq.dat
plot_5 5-intKey-seq "Int" "Seq" "2048"
plot_5 5-intKey-rnd "Int" "Rnd" "2048"

grep 'intKey-true-num-10000000-sequential-false' out-5.tsv | grep -v "compacted" | sed -r 's/Bytes\t([0-9]+)\torg.lmdbjava.bench.([ |a-z|A-Z]+).*/"\2" \1/g' > 5-size.dat
gplot.pl -type png -title "Library Disk Use 10M Rnd X 2,025 Byte Values" -xlabel "" -ylabel "Bytes" -set "nokey; set xtics nomirror rotate by -270" -pointsize 1 -style points -using '2:xtic(1)' -outfile 5-size.png 5-size.dat
