package com.h.drive3;

public class VideoActivity extends DriveActivity {
    @Override protected String suffix() { return "video"; }
    @Override protected String startUrl() { return "https://drive.google.com/drive/search?q=type:video"; }
    @Override protected String pickType() { return "video/*"; }
    @Override protected String label() { return "VIDEO"; }
}
