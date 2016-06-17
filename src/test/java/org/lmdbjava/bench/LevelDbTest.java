package org.lmdbjava.bench;

import java.io.File;
import static java.lang.System.getProperty;
import static org.fusesource.leveldbjni.JniDBFactory.*;
import org.iq80.leveldb.*;
import static org.iq80.leveldb.CompressionType.NONE;
import org.junit.Test;

public class LevelDbTest {

  @Test
  public void sample() throws Exception {
    pushMemoryPool(1_024 * 512);
    try {
      final Options options = new Options();
      options.createIfMissing(true);
      options.compressionType(NONE);
      final String tmp = getProperty("java.io.tmpdir");
      final File file = new File(tmp, "leveldb");
      file.delete();
      final DB db = factory.open(file, options);
      try {

        final WriteBatch batch = db.createWriteBatch();
        try {
          batch.delete(bytes("Denver"));
          batch.put(bytes("Tampa"), bytes("green"));
          batch.put(bytes("London"), bytes("red"));
          db.write(batch);
        } finally {
          batch.close();
        }

        final DBIterator iterator = db.iterator();
        try {
          for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            String key = asString(iterator.peekNext().getKey());
            String value = asString(iterator.peekNext().getValue());
            System.out.println(key + " = " + value);
          }
        } finally {
          iterator.close();
        }
      } finally {
        db.close();
      }
    } finally {
      popMemoryPool();
    }

  }
}
