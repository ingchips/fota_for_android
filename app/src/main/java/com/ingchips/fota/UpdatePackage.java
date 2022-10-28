package com.ingchips.fota;

import static java.lang.System.arraycopy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.*;

/**
 * This class models a update package (a .zip archive)
 *
 * Use `LoadFromStream` to load an `InputStream` created on a .zip archive
 */
public class UpdatePackage {

    public List<UpdateItem> extraBins = new ArrayList<>();
    public UpdateItem platform = null;
    public UpdateItem app = null;

    public Updater.ProductVersion version;

    private int Entry;
    public int getEntry() { return Entry; }

    public String readme;

    private UpdatePackage() {

    }

    static private Updater.Version fromArray(JSONArray arr) {
        try {
            return new Updater.Version(arr.getInt(0), arr.getInt(1), arr.getInt(2));
        } catch (Exception e) {
            return new Updater.Version(0, 0, 0);
        }
    }

    private boolean LoadFromStream0(InputStream stream) {
        Hashtable<String, UpdateItem> contents = new Hashtable<>();
        boolean r;
        try {
            ZipInputStream zipIn = new ZipInputStream(stream);
            ZipEntry entry = zipIn.getNextEntry();
            // iterates over entries in the zip file
            while (entry != null) {
                if (!entry.isDirectory()) {
                    UpdateItem b = new UpdateItem();
                    b.name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                    b.data = new byte[(int) entry.getSize()];
                    byte []bytes = new byte[1024];
                    int len;
                    int cnt = 0;
                    while ((len = zipIn.read(bytes)) > 0) {
                        arraycopy(bytes, 0, b.data, cnt, len);
                        cnt += len;
                    }
                    contents.put(b.name, b);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
            zipIn.close();

            UpdateItem manifest = contents.get("manifest.json");

            JSONObject obj = new JSONObject(new String(manifest.data));

            platform = contents.get(obj.getJSONObject("platform").getString("name"));
            if (platform != null)
                platform.loadAddr = obj.getJSONObject("platform").getInt("address");

            app = contents.get(obj.getJSONObject("app").getString("name"));
            app.loadAddr = obj.getJSONObject("app").getInt("address");

            version = new Updater.ProductVersion(fromArray(obj.getJSONObject("platform").getJSONArray("version")),
                    fromArray(obj.getJSONObject("app").getJSONArray("version")));

            Entry = obj.getInt("entry");

            if (contents.containsKey("readme")) {
                readme = new String(contents.get("readme").data);
            } else
                readme = "<null>";

            JSONArray arr = obj.getJSONArray("bins");
            for (int i = 0; i < arr.length(); i++)
            {
                UpdateItem b = contents.get(arr.getJSONObject(i).getString("name"));
                b.loadAddr = arr.getJSONObject(i).getInt("address");
                extraBins.add(b);
            }

            r = true;
        } catch (Exception e)  {
            r = false;
        }

        return r;
    }

    /**
     * Load a Zip archive from an input stream
     * @param stream        input stream of the archive
     * @return              UpdatePacket instance representing the package
     */
    public static UpdatePackage LoadFromStream(InputStream stream) {
        UpdatePackage r = new UpdatePackage();
        return r.LoadFromStream0(stream) ? r : null;
    }

    /**
     * Create an UpdatePacket instance from a single App binary
     *
     * Note: this is used for on-the-fly App update.
     *
     * @param loadAddr      Load address of app
     * @param bin           content of the app binary
     * @param fileName      file name of app binary (used for logging, etc)
     * @param readme        readme of the update package
     * @return              UpdatePacket instance representing the package
     */
    public static UpdatePackage MakeAppOnPackage(long loadAddr, byte []bin, String fileName, String readme) {
        UpdatePackage r = new UpdatePackage();
        UpdateItem item = new UpdateItem();
        item.data = bin;
        item.name = fileName;
        item.loadAddr = loadAddr;

        r.app = item;
        r.readme = readme;
        r.Entry = 0;
        r.version = new Updater.ProductVersion(new Updater.Version(0, 0, 0),
                new Updater.Version(-1, 0, 0));
        return r;
    }
}
