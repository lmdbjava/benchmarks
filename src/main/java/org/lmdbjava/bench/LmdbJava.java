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
import org.lmdbjava.Cursor;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import org.lmdbjava.Dbi;
import org.lmdbjava.Dbi.KeyNotFoundException;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import org.lmdbjava.Env;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;
import org.lmdbjava.LmdbException;
import org.lmdbjava.Txn;

final class LmdbJava extends AbstractStore {

  private static final int POSIX_MODE = 0664;
  static final String LMDBJAVA = "lmdbjava";
  private final Dbi db;
  private final Env env;
  private final ByteBuffer keyBb;
  private Txn tx;

  private final ByteBuffer valBb;

  LmdbJava(final byte[] key, final byte[] val) throws LmdbException, IOException {
    super(key, val);

    keyBb = allocateDirect(key.length);
    valBb = allocateDirect(val.length);

    final File tmp = createTempFile("bench", ".db");
    env = new Env();
    env.setMapSize(1_024 * 1_024 * 128);
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    env.open(tmp, POSIX_MODE, MDB_NOSUBDIR);

    final Txn dbCreate = new Txn(env);
    db = new Dbi(dbCreate, "db", MDB_CREATE);
    dbCreate.commit();
  }

  @Override
  long crc32() throws Exception {
    final Cursor c = db.openCursor(tx);
    try {
      while (c.get(keyBb, valBb, MDB_NEXT)) {
        CRC.update(keyBb);
        CRC.update(valBb);
      }
    } catch (KeyNotFoundException eof) {
    }
    return CRC.getValue();
  }

  @Override
  void finishCrcPhase() throws Exception {
    tx.abort();
  }

  @Override
  void get() throws Exception {
    keyBb.clear();
    keyBb.put(key);
    db.get(tx, keyBb, valBb);
    valBb.get(val);
  }

  @Override
  void put() throws Exception {
    keyBb.clear();
    valBb.clear();
    keyBb.put(key);
    valBb.put(val);
    db.put(tx, keyBb, valBb);
  }

  @Override
  void startReadPhase() throws Exception {
  }

  @Override
  void startWritePhase() throws Exception {
    tx = new Txn(env);
  }

}
