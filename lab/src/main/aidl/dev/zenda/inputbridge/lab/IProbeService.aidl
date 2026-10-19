package dev.zenda.inputbridge.lab;

interface IProbeService {
    String getIdentity();
    int getProcessUid();
    String startSocketHelper(int port, String sessionToken);
    String getLastInputResult();
    void stopSocketHelper();
}
