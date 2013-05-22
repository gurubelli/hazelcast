/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.queue;

import com.hazelcast.client.ClientTestSupport;
import com.hazelcast.config.Config;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.instance.StaticNodeFactory;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.SerializationService;
import com.hazelcast.nio.serialization.SerializationServiceImpl;
import com.hazelcast.queue.client.*;
import com.hazelcast.spi.impl.PortableCollection;
import com.hazelcast.test.RandomBlockJUnit4ClassRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @ali 5/8/13
 */
@RunWith(RandomBlockJUnit4ClassRunner.class)
public class QueueBinaryClientTest extends ClientTestSupport {

    static final String queueName = "test";
    static final SerializationService ss = new SerializationServiceImpl(0);
    static HazelcastInstance hz = null;

    @BeforeClass
    public static void init() {
        Config config = new Config();
        QueueConfig queueConfig = config.getQueueConfig(queueName);
        queueConfig.setMaxSize(6);
        hz = new StaticNodeFactory(1).newHazelcastInstance(config);
    }

    @AfterClass
    public static void destroy() {
        Hazelcast.shutdownAll();
    }

    @After
    public void clear() throws IOException {
        hz.getQueue(queueName).clear();
    }

    @Test
    public void testAddAll() throws IOException {

        List<Data> list = new ArrayList<Data>();
        list.add(ss.toData("item1"));
        list.add(ss.toData("item2"));
        list.add(ss.toData("item3"));
        list.add(ss.toData("item4"));
        client().send(new AddAllRequest(queueName, list));
        Object result = client().receive();
        assertTrue((Boolean) result);
        int size = hz.getQueue(queueName).size();
        assertEquals(size, list.size());

    }

    @Test
    public void testAddListener() throws IOException {

        client().send(new AddListenerRequest(queueName, true));
        client().receive();

        hz.getQueue(queueName).offer("item");


        String result = (String) client().receive();
        assertTrue(result.startsWith("ItemEvent"));
    }

    @Test
    public void testClear() throws Exception {


        IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");

        client().send(new ClearRequest(queueName));
        Object result = client().receive();
        assertTrue((Boolean) result);
        assertEquals(0, q.size());


    }

    @Test
    public void testCompareAndRemove() throws IOException {


        IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");
        q.offer("item4");
        q.offer("item5");

        List<Data> list = new ArrayList<Data>();
        list.add(ss.toData("item1"));
        list.add(ss.toData("item2"));

        client().send(new CompareAndRemoveRequest(queueName, list, true));
        Boolean result = (Boolean) client().receive();
        assertTrue(result);
        assertEquals(2, q.size());


        client().send(new CompareAndRemoveRequest(queueName, list, false));
        result = (Boolean) client().receive();
        assertTrue(result);
        assertEquals(0, q.size());


    }

    @Test
    public void testContains() throws IOException {


        IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");
        q.offer("item4");
        q.offer("item5");

        List<Data> list = new ArrayList<Data>();
        list.add(ss.toData("item1"));
        list.add(ss.toData("item2"));

        client().send(new ContainsRequest(queueName, list));
        Boolean result = (Boolean) client().receive();
        assertTrue(result);

        list.add(ss.toData("item0"));

        client().send(new ContainsRequest(queueName, list));
        result = (Boolean) client().receive();
        assertFalse(result);


    }

    @Test
    public void testDrain() throws IOException {


        IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");
        q.offer("item4");
        q.offer("item5");

        client().send(new DrainRequest(queueName, 1));
        PortableCollection result = (PortableCollection)client().receive();
        Collection<Data> coll = result.getCollection();
        assertEquals(1, coll.size());
        assertEquals("item1", ss.toObject(coll.iterator().next()));
        assertEquals(4, q.size());

    }

    @Test
    public void testIterator() throws IOException {

        IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");
        q.offer("item4");
        q.offer("item5");

        client().send(new IteratorRequest(queueName));
        PortableCollection result = (PortableCollection)client().receive();
        Collection<Data> coll = result.getCollection();
        int i = 1;
        for (Data data : coll) {
            assertEquals("item" + i, ss.toObject(data));
            i++;
        }

    }

    @Test
    public void testOffer() throws IOException {


        final IQueue q = hz.getQueue(queueName);

        client().send(new OfferRequest(queueName, ss.toData("item1")));
        Object result = client().receive();
        assertTrue((Boolean) result);
        Object item = q.peek();
        assertEquals(item, "item1");


        q.offer("item2");
        q.offer("item3");
        q.offer("item4");
        q.offer("item5");
        q.offer("item6");

        new Thread() {
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                q.poll();
            }
        }.start();
        client().send(new OfferRequest(queueName, 500, ss.toData("item7")));
        result = client().receive();
        assertFalse((Boolean) result);

        client().send(new OfferRequest(queueName, 10 * 1000, ss.toData("item7")));
        result = client().receive();
        assertTrue((Boolean) result);


    }

    @Test
    public void testPeek() throws IOException {

        IQueue q = hz.getQueue(queueName);

        client().send(new PeekRequest(queueName));
        Object result = client().receive();
        assertNull(result);

        q.offer("item1");
        client().send(new PeekRequest(queueName));
        result = client().receive();
        assertEquals("item1", result);
        assertEquals(1, q.size());

    }

    @Test
    public void testPoll() throws IOException {


        final IQueue q = hz.getQueue(queueName);

        client().send(new PollRequest(queueName));
        Object result = client().receive();
        assertNull(result);

        q.offer("item1");
        client().send(new PollRequest(queueName));
        result = client().receive();
        assertEquals("item1", result);
        assertEquals(0, q.size());


        new Thread() {
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                q.offer("item2");
            }
        }.start();
        client().send(new PollRequest(queueName, 10 * 1000));
        result = client().receive();
        assertEquals("item2", result);
        assertEquals(0, q.size());


    }

    @Test
    public void testRemove() throws IOException {

        final IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");

        client().send(new RemoveRequest(queueName, ss.toData("item2")));
        Boolean result = (Boolean) client().receive();
        assertTrue(result);
        assertEquals(2, q.size());

        client().send(new RemoveRequest(queueName, ss.toData("item2")));
        result = (Boolean) client().receive();
        assertFalse(result);
        assertEquals(2, q.size());

    }

    @Test
    public void testSize() throws IOException {

        final IQueue q = hz.getQueue(queueName);
        q.offer("item1");
        q.offer("item2");
        q.offer("item3");

        client().send(new SizeRequest(queueName));
        int result = (Integer) client().receive();
        assertEquals(result, q.size());

    }
}