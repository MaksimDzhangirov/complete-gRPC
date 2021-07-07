package com.gitlab.techschool.pcbook.service;

import com.github.techschool.pcbook.pb.Laptop;

public interface LaptopStream {
    void Send(Laptop laptop);
}
