#!/bin/bash

# designed to run from benchmark root, then user copies txt files to a results/YYYYMMDDD dir

# RAM-bounded tests of embedded KV stores

rm -f out-?.csv out-?.tsv out-?.txt

# Run 1 compares LMDB impls with 1M x 100 byte values and LMDB-specific configuration options (~ 1 GB w/o overhead)
java -jar target/benchmarks.jar -rf csv -f 3 -wi 3 -i 3 -to 10m -tu ms -p sync=true,false -p forceSafe=true,false -p metaSync=true,false -p writeMap=true,false -rff out-1.csv LmdbJavaAgrona LmdbJavaByteBuffer LmdbJni LmdbLwjgl | tee out-1.txt

# Run 2 single shot (no warm up) native libraries with 1M entries to find reasonable ~2/4/8/16 KB value sizes (with int keys)
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 10m -tu ms -p sequential=true,false -p valSize=2026,2027,4080,4081,8176,8177,16368,16369 -e readCrc -e readRev -e readSeq -e readXxh64 -e write -rff out-2.csv LevelDb LmdbJavaAgrona RocksDb | tee out-2.txt

# conclusion: optimal if valSize=2026||4080||8176||16368 (Howard Chu has advised record sizes (ie key + val) should increment in 4,096 bytes unit after 4,084, so 4084, 8180, 12276, 16372 etc -- this is what we are seeing above as the valSize is recSize - 4 byte keySize)

# Run 3 single shot (no warm up) evaluates LevelDB/RocksDB batch size for 10M entries
# /etc/security/limits.conf soft + hard nofile @ 1000000 + reboot
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 60m -tu ms -p valSize=8176 -p batchSize=1000000,10000000 -p num=10000000 -e readCrc -e readKey -e readRev -e readSeq -e readXxh64 -rff out-3.csv LevelDb RocksDb | tee out-3.txt

# conclusion: optimal if batchSize=1M (1M was faster for RocksDB, and barely different for LevelDB, so we will go with 1M as it seems a reasonable compromise)

# Run 4 compares all libraries with 1M x 100 byte values and assorted key types / access patterns (~ 100 MB w/o overhead)
java -jar target/benchmarks.jar -rf csv -f 3 -wi 3 -i 3 -to 60m -tu ms -p intKey=true,false -p sequential=true,false -rff out-4.csv | tee out-4.txt

# Following tests exclude readCrc, readRev and readXxh64 to save execution time

# Following tests exclude string keys to save execution time (integer keys are smaller and easy to index/bitmask etc)

# Following tests exclude MvStore, as it gives "java.lang.OutOfMemoryError: Capacity: 2147483647"

# Run 5 single shot (no warm up) with 10M x 2026 byte values (~19 GB w/o overhead)
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 120m -tu ms -p sequential=true,false -p batchSize=1000000 -p num=10000000 -p valSize=2026 -e readCrc -e readRev -e readXxh64 -rff out-5.csv Chronicle LevelDb LmdbJavaAgrona LmdbJavaByteBuffer LmdbJni LmdbLwjgl RocksDb MapDb Xodus | tee out-5.txt

# Following tests exclude MapDB as:
# 1. valSize=8176 gives "Native memory allocation (mmap) failed to map 12288 bytes for committing reserved memory" with values of 8176 and above. Its performance on ~2 KB values above
# 2. Slow execution time makes it difficult to run the benchmark in a reasonable period
# 3. Including very large MapDB values in the graphs make the remaining data hard to read

# Following tests exclude Xodus for MapDB reasons 2-3 above

# Following tests also exclude LmdbJavaByteBuffer and LmdbJni, as they all perform so similarly anyway and it needlessly increases execution time

# Following tests also exclude sequential access patterns in the interests of time, as we already know sequential is much faster

# Following tests switch to measurement in seconds given run durations

# Run 6 single shot (no warm up) with 10M x 4080/8176/16368 byte values (~38/76/152 GB w/o overhead)
java -jar target/benchmarks.jar -rf csv -bm ss -wi 0 -i 1 -to 360m -tu s -p sequential=false -p batchSize=1000000 -p num=10000000 -p valSize=4080,8176,16368 -e readCrc -e readRev -e readXxh64 -rff out-6.csv Chronicle LevelDb LmdbJavaAgrona RocksDb | tee out-6.txt
