[![Maven Build](https://github.com/lmdbjava/benchmarks/workflows/Maven%20Build/badge.svg)](https://github.com/lmdbjava/benchmarks/actions)
[![License](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0.txt)

# LmdbJava Benchmarks

**Just want the latest results?
[View them here!](https://github.com/lmdbjava/benchmarks/blob/master/results/20160710/README.md)**

This is a [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmark
of open source, embedded, memory-mapped, key-value stores available from Java:

* [LmdbJava](https://github.com/lmdbjava/lmdbjava) (with fast `ByteBuffer`, safe
  `ByteBuffer` and an [Agrona](https://github.com/real-logic/Agrona) buffer)
* [LMDBJNI](https://github.com/deephacks/lmdbjni)
* [Lightweight Java Game Library](https://github.com/LWJGL/lwjgl3/) (LMDB API)
* [LevelDBJNI](https://github.com/fusesource/leveldbjni)
* [RocksDB](http://rocksdb.org/)
* [MVStore](http://h2database.com/html/mvstore.html) (pure Java)
* [MapDB](http://www.mapdb.org/) (pure Java)
* [Xodus](https://github.com/JetBrains/xodus) (pure Java)
* [Chroncile Map](https://github.com/OpenHFT/Chronicle-Map) (pure Java) (**)

(**) does not support ordered keys, so iteration benchmarks not performed

The benchmark itself is adapted from LMDB's
[db_bench_mdb.cc](http://lmdb.tech/bench/microbench/db_bench_mdb.cc), which in
turn is adapted from
[LevelDB's benchmark](https://github.com/google/leveldb/blob/master/db/db_bench.cc).

The benchmark includes:

* Writing data
* Reading all data via each key
* Reading all data via a reverse iterator
* Reading all data via a forward iterator
* Reading all data via a forward iterator and computing a CRC32 (via JDK API)
* Reading all data via a forward iterator and computing a XXH64
  (via [extremely fast](https://github.com/benalexau/hash-bench)
  [Zero-Allocation-Hashing](https://github.com/OpenHFT/Zero-Allocation-Hashing))

Byte arrays (`byte[]`) are always used for the keys and values, avoiding any
serialization library overhead. For those libraries that support compression,
it is disabled in the benchmark. In general any special library features that
decrease latency (eg batch modes, disable auto-commit, disable journals,
hint at expected data sizes etc) were used. While we have tried to be fair and
consistent, some libraries offer non-obvious tuning settings or usage patterns
that might further reduce their latency. We do not claim we have exhausted
every tuning option every library exposes, but pull requests are most welcome.

## Usage
This benchmark uses POSIX calls to accurately determine consumed disk space and
only depends on Linux-specific native library wrappers where a range of such
wrappers exists. Operation on non-Linux operating systems is unsupported.

1. Clone this repository and `mvn clean package`
2. Run the benchmark with `java -jar target/benchmarks.jar`

The benchmark offers many parameters, but to reduce execution time they default
to a fast, mechanically-sympathetic workload (ie integer keys, sequential IO)
that should fit in RAM. A default execution takes around 15 minutes on
server-grade hardware (ie 2 x Intel Xeon E5-2667 v3 CPUs, 512 GB RAM etc).

You can append ``-h`` to the ``java -jar`` line for JMH help. For example, use:

  * ``-foe true`` to stop on any error (recommended)
  * ``-rf csv`` to emit a CSV results file (recommended)
  * ``-f 3`` to run three forks for smaller error ranges (recommended)
  * ``-lp`` to list all available parameters
  * ``-p intKey=true,false`` to test both integer and string-based keys

The parameters (available from `-lp`) allow you to create workloads of different
iteration counts (`num`), key sizes and layout (`intKey`), value sizes
(`valSize`), mechanical sympathy (`sequential`, `valRandom`) and feature tuning
(eg `forceSafe`, `writeMap` etc).

``System.out`` will display the actual on-disk usage of each implementation as
``"Bytes" \t longVal \t benchId`` lines. This is not the "apparent" size (given
sparse files are typical), but the actual on-disk space used. The underlying
storage location defaults to the temporary file system. To force an alternate
location, invoke Java with `-Djava.io.tmpdir=/somewhere/you/like`.

## Support

Please [open a GitHub issue](https://github.com/lmdbjava/benchmarks/issues)
if you have any questions.

## Contributing

Contributions are welcome! Please see the LmdbJava project's
[Contributing Guidelines](https://github.com/lmdbjava/lmdbjava/blob/master/CONTRIBUTING.md).

## License

This project is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
