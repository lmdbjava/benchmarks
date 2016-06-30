#!/bin/bash

# designed to run from benchmark root, then user copies txt files to a results/YYYYMMDDD dir

# RAM-bounded tests of embedded KV stores

rm -f out-?.csv out-?.tsv out-?.txt

# Run 1 compares LMDB impls with 1M x 100 byte values and LMDB-specific configuration options (~ 1 GB w/o overhead)
java -jar target/benchmarks.jar -rf csv -f 3 -wi 3 -i 3 -to 10m -tu ms -p sync=true,false -p forceSafe=true,false -p metaSync=true,false -p writeMap=true,false -rff out-1.csv LmdbJavaAgrona LmdbJavaByteBuffer LmdbJni | tee out-1.txt

# Run 2 single shot (no warm up) native libraries with 1M entries to find reasonable ~2 KB byte value size
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 10m -tu ms -p sequential=true,false -p valSize=2020,2025,2030 -e readCrc -e readRev -e readSeq -e readXxh64 -e write -rff out-2.csv LevelDb LmdbJavaAgrona RocksDb | tee out-2.txt

# conclusion: optimal if valSize=2025

# Run 3 single shot (no warm up) evaluates LevelDB batch size for 1M entries
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 10m -tu ms -p valSize=2025 -p batchSize=10000,100000,1000000 -e readCrc -e readKey -e readRev -e readSeq -e readXxh64 -rff out-3.csv LevelDb | tee out-3.txt

# conclusion: optimal if batchSize=num (1M is default batchSize already)

# Run 4 compares all libraries with 1M x 100 byte values and assorted key types / access patterns (~ 100 MB w/o overhead)
java -jar target/benchmarks.jar -rf csv -f 3 -wi 3 -i 3 -to 10m -tu ms -p intKey=true,false -p sequential=true,false -rff out-4.csv | tee out-4.txt

# Following tests exclude readCrc, readRev and readXxh64 to save execution time

# Following tests exclude string keys to save execution time (integer keys are smaller and easy to index/bitmask etc)

# Following tests exclude MvStore, as it gives "java.lang.OutOfMemoryError: Capacity: 2147483647"

# Following tests exclude RocksDb, as it gives "too many open files" error (lsof | grep RocksDb | wc -l reports over 144,000!)

# Run 5 single shot (no warm up) with 10M x 2025 byte values (~19 GB w/o overhead)
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 60m -tu ms -p sequential=true,false -p num=10000000 -p batchSize=10000000 -p valSize=2025 -e readCrc -e readRev -e readXxh64 -rff out-5.csv Chronicle LevelDb LmdbJavaAgrona LmdbJavaByteBuffer LmdbJni MapDb | tee out-5.txt

