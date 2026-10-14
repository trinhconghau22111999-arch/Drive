package com.h.drive3;

public class PhotoActivity extends DriveActivity {
    @Override protected String suffix() { return "anh"; }
    @Override protected String startUrl() { return "https://drive.google.com/drive/search?q=type:image"; }
    @Override protected String label() { return "ẢNH"; }
    @Override protected int accent() { return 0xFF1E88E5; }
}
