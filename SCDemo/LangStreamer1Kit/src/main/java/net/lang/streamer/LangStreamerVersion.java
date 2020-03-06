package net.lang.streamer;

/**
 * Created by lichao on 17-5-2.
 */

public class LangStreamerVersion {
    private int major;
    private int minor;
    private int revision;
    private String note;
    private static LangStreamerVersion sVer = new LangStreamerVersion(1,0,5);

    private LangStreamerVersion(int major, int minor, int revision) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        note = "";
    }

    public static LangStreamerVersion getVersion() {
        return sVer;
    }

    protected int getMinor() {
        return minor;
    }

    protected void setMinor(int minor) {
        this.minor = minor;
    }

    protected int getRevision() {
        return revision;
    }

    protected void setRevision(int revision) {
        this.revision = revision;
    }

    protected String getNote() {
        return note;
    }

    protected void setNote(String note) {
        this.note = note;
    }

    protected int getMajor() {
        return major;
    }

    protected void setMajor(int major) {
        this.major = major;
    }

    protected String version() {
        return major + "." + minor + "." + revision + note;
    }
}
