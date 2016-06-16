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

import java.io.IOException;

import static java.lang.Boolean.TRUE;
import static java.lang.System.setProperty;

import java.nio.ByteBuffer;

import static java.nio.ByteBuffer.allocateDirect;

import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_NEXT;

import org.lmdbjava.Dbi;

import static org.lmdbjava.DbiFlags.MDB_CREATE;

import org.lmdbjava.Env;

import static org.lmdbjava.Env.DISABLE_CHECKS_PROP;
import static org.lmdbjava.Env.SHOULD_CHECK;
import static org.lmdbjava.EnvFlags.MDB_NOSUBDIR;

import org.lmdbjava.LmdbException;
import org.lmdbjava.Txn;
import static org.lmdbjava.ByteBufferVals.forBuffer;
import org.lmdbjava.CursorB;
import org.lmdbjava.CursorOp;
import static java.io.File.createTempFile;
import org.lmdbjava.ValB;

final class LmdbJavaB extends AbstractStore {

  private static final int POSIX_MODE = 0664;
  static final String LMDBJAVAB = "lmdbjavab";

  static {
    setProperty(DISABLE_CHECKS_PROP, TRUE.toString());
  }
  private CursorB cursor;
  private final Dbi db;
  private final Env env;
  private Txn tx;
  final ValB roKeyVal;
  final ValB roValVal;
  final ValB rwKeyVal;
  final ValB rwValVal;

  LmdbJavaB(final ByteBuffer key, final ByteBuffer val) throws LmdbException,
                                                               IOException {
    super(key, val, allocateDirect(0), allocateDirect(0));

    this.roKeyVal = forBuffer(roKey, false);
    this.roValVal = forBuffer(roVal, false);
    this.rwKeyVal = forBuffer(key, false);
    this.rwValVal = forBuffer(val, false);

    if (SHOULD_CHECK) {
      throw new IllegalStateException();
    }

    final File tmp = createTempFile("bench", ".db");
    env = new Env();
    env.setMapSize(1_024 * 1_024 * 1_024);
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    env.open(tmp, POSIX_MODE, MDB_NOSUBDIR);

    final Txn dbCreate = new Txn(env);
    db = new Dbi(dbCreate, "db", MDB_CREATE);
    dbCreate.commit();
  }

  @Override
  void crc32() throws Exception {
    try (final CursorB c = db.openCursorB(tx)) {
      if (!c.get(roKeyVal, roValVal, MDB_FIRST)) {
        throw new IllegalStateException();
      }

      do {
        roKeyVal.refresh();
        roValVal.refresh();
        CRC.update(roKey);
        CRC.update(roVal);
        roKey.flip();
      } while (c.get(roKeyVal, roValVal, MDB_NEXT));
    }
  }

  @Override
  void cursorGetFirst() throws Exception {
    cursor.get(roKeyVal, roValVal, MDB_FIRST);
    // key and value checked as in theory first row might have different key
    roKeyVal.refresh();
    roValVal.refresh();
    final long k = roKey.getLong();
    final long v = roVal.getLong();

    if (k != BasicOpsBenchmark.KEY || v != BasicOpsBenchmark.VAL) {
      throw new IllegalStateException("k:" + k + " v:" + v);
    }
  }

  @Override
  void finishCrcPhase() throws Exception {
    tx.abort();
  }

  @Override
  void get() throws Exception {
    cursor.get(rwKeyVal, roValVal, CursorOp.MDB_SET_KEY);
    roValVal.refresh();
  }

  @Override
  void put() throws Exception {
    cursor.put(rwKeyVal, rwValVal);
  }

  @Override
  void startReadPhase() throws Exception {
  }

  @Override
  void startWritePhase() throws Exception {
    tx = new Txn(env);
    cursor = db.openCursorB(tx);
  }
  
  @Override
  long sumData() throws Exception {
    long result = 0;
    try (final CursorB c = db.openCursorB(tx)) {
      if (!c.get(roKeyVal, roValVal, MDB_FIRST)) {
        throw new IllegalStateException();
      }

      do {
        result += roKeyVal.size();
        result += roValVal.size();
      } while (c.get(roKeyVal, roValVal, MDB_NEXT));
    }
    return result;
  }

}
