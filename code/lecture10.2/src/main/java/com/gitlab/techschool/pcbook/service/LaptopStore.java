package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Laptop;

public interface LaptopStore {
    void Save(Laptop laptop) throws Exception; // consider using a separate db model
    Laptop Find(String id);
}

