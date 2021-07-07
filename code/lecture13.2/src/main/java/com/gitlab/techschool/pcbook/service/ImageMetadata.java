package com.gitlab.techschool.pcbook.service;

public class ImageMetadata {
    private String laptopID;
    private String type;
    private String path;

    public ImageMetadata(String laptopID, String type, String path) {
        this.laptopID = laptopID;
        this.type = type;
        this.path = path;
    }

    public String getLaptopID() {
        return laptopID;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }
}
