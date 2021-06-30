package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Filter;
import com.github.techschool.pcbook.pb.Laptop;
import io.grpc.Context;

public interface LaptopStore {
    void Save(Laptop laptop) throws Exception; // consider using a separate db model
    Laptop Find(String id);
    void Search(Context ctx, Filter filter, LaptopStream stream);
}

