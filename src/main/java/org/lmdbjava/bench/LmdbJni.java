/*
 * Copyright 2016 The LmdbJava Project, http://lmdbjava.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lmdbjava.bench;

import java.io.File;
import static java.io.File.createTempFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import static java.nio.ByteBuffer.allocateDirect;
import org.fusesource.lmdbjni.BufferCursor;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.DirectBuffer;
import org.fusesource.lmdbjni.Transaction;
import org.lmdbjava.LmdbException;

final class LmdbJni extends AbstractStore {

  private static final int POSIX_MODE = 0664;
  static final String LMDBJNI = "lmdbjni";
  private final Database db;
  private final org.fusesource.lmdbjni.Env env;
  private final ByteBuffer keyBb;
  private final DirectBuffer keyDb;
  private Transaction tx;

  private final ByteBuffer valBb;
  private final DirectBuffer valDb;

  LmdbJni(final byte[] key, final byte[] val) throws LmdbException, IOException {
    super(key, val);

    keyBb = allocateDirect(key.length);
    valBb = allocateDirect(val.length);
    keyDb = new DirectBuffer(keyBb);
    valDb = new DirectBuffer(valBb);

    final File tmp = createTempFile("bench", ".db");
    env = new org.fusesource.lmdbjni.Env();
    env.setMapSize(1_024 * 1_024 * 128);
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    final int noSubDir = 0x4000;
    env.open(tmp.getAbsolutePath(), noSubDir, POSIX_MODE);

    final Transaction dbCreate = env.createWriteTransaction();
    final int create = 0x4_0000;
    db = env.openDatabase(dbCreate, "db", create);
    dbCreate.commit();
  }

  @Override
  long crc32() throws Exception {
    keyBb.clear();
    valBb.clear();
    keyDb.wrap(keyBb);
    valDb.wrap(valBb);
    final BufferCursor c = db.bufferCursor(tx, keyDb, valDb);
    if (c.first()) {
      do {
        keyDb.getBytes(0, key);
        valDb.getBytes(0, val);
        CRC.update(key);
        CRC.update(val);
      } while (c.next());
    }
    return CRC.getValue();
  }

  @Override
  void finishCrcPhase() throws Exception {
    tx.abort();
  }

  @Override
  void get() throws Exception {
    keyDb.putBytes(0, key);
    db.get(tx, keyDb, valDb);
    valDb.getBytes(0, val);
  }

  @Override
  void put() throws Exception {
    db.put(tx, key, val);
  }

  @Override
  void startReadPhase() throws Exception {
  }

  @Override
  void startWritePhase() throws Exception {
    tx = env.createWriteTransaction();
  }

}
