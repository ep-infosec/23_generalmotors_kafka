/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.state.internals;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.ProcessorContextImpl;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.test.MockRecordCollector;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.time.Instant.ofEpochMilli;

@RunWith(EasyMockRunner.class)
public class ChangeLoggingWindowBytesStoreTest {

    private final TaskId taskId = new TaskId(0, 0);
    private final MockRecordCollector collector = new MockRecordCollector();

    private final byte[] value = {0};
    private final Bytes bytesKey = Bytes.wrap(value);

    @Mock(type = MockType.NICE)
    private WindowStore<Bytes, byte[]> inner;
    @Mock(type = MockType.NICE)
    private ProcessorContextImpl context;
    private ChangeLoggingWindowBytesStore store;


    @Before
    public void setUp() {
        store = new ChangeLoggingWindowBytesStore(inner, false);
    }

    private void init() {
        EasyMock.expect(context.taskId()).andReturn(taskId);
        EasyMock.expect(context.recordCollector()).andReturn(collector);
        inner.init(context, store);
        EasyMock.expectLastCall();
        EasyMock.replay(inner, context);

        store.init(context, store);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldLogPuts() {
        inner.put(bytesKey, value, 0);
        EasyMock.expectLastCall();

        init();

        final Bytes key = WindowKeySchema.toStoreKeyBinary(bytesKey, 0, 0);

        EasyMock.reset(context);
        EasyMock.expect(context.timestamp()).andStubReturn(0L);
        context.logChange(store.name(), key, value, 0L);

        EasyMock.replay(context);
        store.put(bytesKey, value);

        EasyMock.verify(inner, context);
    }

    @Test
    public void shouldDelegateToUnderlyingStoreWhenFetching() {
        EasyMock
            .expect(inner.fetch(bytesKey, 0, 10))
            .andReturn(KeyValueIterators.emptyWindowStoreIterator());

        init();

        store.fetch(bytesKey, ofEpochMilli(0), ofEpochMilli(10));
        EasyMock.verify(inner);
    }

    @Test
    public void shouldDelegateToUnderlyingStoreWhenFetchingRange() {
        EasyMock
            .expect(inner.fetch(bytesKey, bytesKey, 0, 1))
            .andReturn(KeyValueIterators.emptyIterator());

        init();

        store.fetch(bytesKey, bytesKey, ofEpochMilli(0), ofEpochMilli(1));
        EasyMock.verify(inner);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void shouldRetainDuplicatesWhenSet() {
        store = new ChangeLoggingWindowBytesStore(inner, true);

        inner.put(bytesKey, value, 0);
        EasyMock.expectLastCall().times(2);

        init();

        final Bytes key1 = WindowKeySchema.toStoreKeyBinary(bytesKey, 0, 1);
        final Bytes key2 = WindowKeySchema.toStoreKeyBinary(bytesKey, 0, 2);

        EasyMock.reset(context);
        EasyMock.expect(context.timestamp()).andStubReturn(0L);
        context.logChange(store.name(), key1, value, 0L);
        context.logChange(store.name(), key2, value, 0L);

        EasyMock.replay(context);

        store.put(bytesKey, value);
        store.put(bytesKey, value);

        EasyMock.verify(inner, context);
    }

}