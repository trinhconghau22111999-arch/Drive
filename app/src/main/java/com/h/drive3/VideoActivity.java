package com.h.drive3;

public class VideoActivity extends DriveActivity {
    @Override protected String suffix() { return "video"; }
    @Override protected String startUrl() { return "https://drive.google.com/drive/search?q=type:video"; }
    @Override protected String label() { return "VIDEO"; }
    @Override protected int accent() { return 0xFFE53935; }
}
