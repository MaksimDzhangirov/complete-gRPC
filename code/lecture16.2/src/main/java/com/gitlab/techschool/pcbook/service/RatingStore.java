package com.gitlab.techschool.pcbook.service;

public interface RatingStore {
    Rating Add(String laptopID, double score);
}
