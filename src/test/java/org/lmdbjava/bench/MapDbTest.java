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
import static java.lang.System.getProperty;
import java.util.Iterator;
import java.util.Map.Entry;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.mapdb.*;
import static org.mapdb.DBMaker.fileDB;
import static org.mapdb.Serializer.BYTE_ARRAY;

public class MapDbTest {

  @Test
  public void mapdb() throws Exception {

    final String tmp = getProperty("java.io.tmpdir");
    final File file = new File(tmp, "map.db");
    file.delete();
    final DB db = fileDB(file).make();

    final BTreeMap<byte[], byte[]> map;
    map
        = db.treeMap("ba2ba")
        .keySerializer(BYTE_ARRAY)
        .valueSerializer(BYTE_ARRAY)
        .createOrOpen();

    final byte[] key = "Zoo".getBytes();
    final byte[] in = "Second".getBytes();
    map.put(key, in);
    final byte[] out = map.get(key);
    assertThat(out, is(in));

    map.put("Foo".getBytes(), "First".getBytes());

    Iterator<Entry<byte[], byte[]>> iterator = map.entryIterator();
    while (iterator.hasNext()) {
      final Entry<byte[], byte[]> entry = iterator.next();
      System.out.println(new String(entry.getKey()) + " = "
                             + new String(entry.getValue()));
    }

    db.close();
  }
}
