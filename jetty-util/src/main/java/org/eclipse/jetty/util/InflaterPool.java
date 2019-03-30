//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Inflater;

public class InflaterPool
{
    private final Queue<Inflater> _pool;
    private final int _compressionLevel;
    private final boolean _nowrap;
    private final AtomicInteger _numInflaters = new AtomicInteger(0);
    private final int _capacity;


    /**
     * Create a Pool of {@link Inflater} instances.
     *
     * If given a capacity equal to zero the Inflaters will not be pooled
     * and will be created on acquire and ended on release.
     * If given a negative capacity equal to zero there will be no size restrictions on the InflaterPool
     *
     * @param capacity maximum number of Inflaters which can be contained in the pool
     * @param nowrap if true then use GZIP compatible compression for all new Inflater objects
     */
    public InflaterPool(int capacity, boolean nowrap)
    {
        _capacity = capacity;
        _nowrap = nowrap;

        if (_capacity != 0)
            _pool = new ConcurrentLinkedQueue<>();
        else
            _pool = null;
    }

    protected Inflater newInflater()
    {
        return new Inflater(_nowrap);
    }

    /**
     * @return Inflater taken from the pool if it is not empty or a newly created Inflater
     */
    public Inflater acquire()
    {
        Inflater inflater;

        if (_capacity == 0)
            inflater = newInflater();
        else if (_capacity < 0)
        {
            inflater = _pool.poll();
            if (inflater == null)
                inflater = newInflater();
        }
        else
        {
            inflater = _pool.poll();
            if (inflater == null)
                inflater = newInflater();
            else
                _numInflaters.decrementAndGet();
        }

        return inflater;
    }

    /**
     * @param inflater returns this Inflater to the pool or calls Inflater.end() if the pool is full.
     */
    public void release(Inflater inflater)
    {
        if (inflater == null)
            return;

        if (_capacity == 0)
        {
            inflater.end();
            return;
        }
        else if (_capacity < 0)
        {
            inflater.reset();
            _pool.add(inflater);
        }
        else
        {
            while (true)
            {
                int d = _numInflaters.get();

                if (d >= _capacity)
                {
                    inflater.end();
                    break;
                }

                if (_numInflaters.compareAndSet(d, d + 1))
                {
                    inflater.reset();
                    _pool.add(inflater);
                    break;
                }
            }
        }
    }
}
