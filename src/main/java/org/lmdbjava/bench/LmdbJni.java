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
import org.fusesource.lmdbjni.Env;
import org.fusesource.lmdbjni.Transaction;
import org.lmdbjava.LmdbException;

final class LmdbJni extends AbstractStore {

  private static final int POSIX_MODE = 0664;
  static final String LMDBJNI = "lmdbjni";
  private final Database db;
  private final Env env;
  private final DirectBuffer keyDb;
  private final ByteBuffer keyMapped;
  private Transaction tx;
  private final DirectBuffer valDb;
  private final ByteBuffer valMapped;

  LmdbJni(final ByteBuffer key, final ByteBuffer val) throws LmdbException,
                                                             IOException {
    super(key, val);

    keyMapped = allocateDirect(key.capacity());
    valMapped = allocateDirect(val.capacity());
    keyDb = new DirectBuffer(key);
    valDb = new DirectBuffer(val);

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
  void crc32() throws Exception {
    keyDb.wrap(keyMapped);
    valDb.wrap(valMapped);
    try (final BufferCursor c = db.bufferCursor(tx, keyDb, valDb)) {
      if (c.first()) {
        do {
          keyDb.getBytes(0, keyMapped, keyMapped.capacity());
          valDb.getBytes(0, valMapped, valMapped.capacity());
          keyMapped.flip();
          valMapped.flip();
          CRC.update(keyMapped);
          CRC.update(valMapped);
          keyMapped.flip();
          valMapped.clear();
        } while (c.next());
      }
    }
  }

  @Override
  void finishCrcPhase() throws Exception {
    tx.abort();
  }

  @Override
  void get() throws Exception {
    if (db.get(tx, keyDb, valDb) != 0) {
      throw new IllegalStateException();
    }
    valDb.getBytes(0, val, val.capacity());
    val.flip();
  }

  @Override
  void put() throws Exception {
    if (db.put(tx, keyDb, valDb, 0) != 0) {
      throw new IllegalStateException();
    }
  }

  @Override
  void startReadPhase() throws Exception {
    keyDb.wrap(key);
    valDb.wrap(valMapped);
  }

  @Override
  void startWritePhase() throws Exception {
    tx = env.createWriteTransaction();
    keyDb.wrap(key);
    valDb.wrap(val);
  }

}
