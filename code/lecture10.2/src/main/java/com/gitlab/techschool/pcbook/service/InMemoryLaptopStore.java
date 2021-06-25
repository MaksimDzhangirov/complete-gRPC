package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Filter;
import com.github.techschool.pcbook.pb.Laptop;
import com.github.techschool.pcbook.pb.Memory;
import io.grpc.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class InMemoryLaptopStore implements LaptopStore {
    private static final Logger logger = Logger.getLogger(LaptopClient.class.getName());
    private ConcurrentMap<String, Laptop> data;

    public InMemoryLaptopStore() {
        data = new ConcurrentHashMap<>(0);
    }

    @Override
    public void Save(Laptop laptop) throws Exception {
        if (data.containsKey(laptop.getId())) {
            throw new AlreadyExistsException("laptop ID already exists");
        }

        // deep copy
        Laptop other = laptop.toBuilder().build();
        data.put(other.getId(), other);
    }

    @Override
    public Laptop Find(String id) {
        if (!data.containsKey(id)) {
            return null;
        }

        // deep copy
        Laptop other = data.get(id).toBuilder().build();
        return other;
    }
}
