package core.config;

public enum Version {
    V26_1(775, 4786),
    V26_2(776, 4903),
    V26_3(777, 5023),
    ANY(0, 0);

    public final int dataVersion;
    public final int protocolVersion;

    Version(int protocolVersion, int dataVersion) {
        this.protocolVersion = protocolVersion;
        this.dataVersion = dataVersion;
    }
}
